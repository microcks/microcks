/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.microcks.util.grpc;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.ReferenceResolver;

import com.github.os72.protocjar.Protoc;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * An implementation of MockRepositoryImporter that deals with Protobuf v3 specification documents.
 * @author laurent
 */
public class ProtobufImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ProtobufImporter.class);

   private static final String BINARY_DESCRIPTOR_EXT = ".pbb";
   private static final String BUILTIN_LIBRARY_PREFIX = "google/protobuf";
   private static final String PACKAGE = "package ";

   private final PackageServices packageServices;
   private final String protoDirectory;
   private final String protoFileName;
   private final ReferenceResolver referenceResolver;
   private String specContent;
   private DescriptorProtos.FileDescriptorSet fds;


   /**
    * Build a new importer.
    * @param protoFilePath     The path to local proto spec file
    * @param referenceResolver An optional resolver for references present into the Protobuf file
    * @throws IOException if project file cannot be found or read.
    */
   public ProtobufImporter(String protoFilePath, ReferenceResolver referenceResolver) throws IOException {
      this.referenceResolver = referenceResolver;

      String fileSeparator = FileSystems.getDefault().getSeparator();

      // Move proto file to a unique subdir that matches its package name (to avoid conflicts)
      // This will allow protoc to find the relative imports we will download later on.
      Path protoPath = Paths.get(protoFilePath);
      packageServices = getPackage(protoPath);
      if (packageServices.packageName != null) {
         String packagePath = packageServices.packageName.replace(".", fileSeparator);

         String uuid = UUID.randomUUID().toString();
         Path newProtoPath = protoPath.getParent()
               .resolve(uuid + fileSeparator + packagePath + fileSeparator + protoPath.getFileName());

         try {
            Files.createDirectories(newProtoPath.getParent());
            Files.createFile(newProtoPath);
         } catch (FileAlreadyExistsException faee) {
            // Ignore if the file already exists, it means we have already downloaded it.
         }
         Files.copy(protoPath, newProtoPath, StandardCopyOption.REPLACE_EXISTING);

         // Prepare file, path and name for easier process.
         File protoFile = new File(protoFilePath);
         protoDirectory = protoPath.getParent().resolve(uuid + fileSeparator).toFile().getAbsolutePath();
         protoFileName = packagePath + fileSeparator + protoFile.getName();

         // Now switch the proto and file paths.
         protoFilePath = newProtoPath.toString();
         protoPath = newProtoPath;
      } else {
         // Prepare file, path and name for easier process.
         File protoFile = new File(protoFilePath);
         protoDirectory = protoFile.getParentFile().getAbsolutePath();
         protoFileName = protoFile.getName();
      }

      // Prepare protoc arguments.
      String[] args = { "-v3.21.8", "--include_std_types", "--include_imports", "--proto_path=" + protoDirectory,
            "--descriptor_set_out=" + protoDirectory + "/" + protoFileName + BINARY_DESCRIPTOR_EXT, protoFileName };

      // Track resolved imports (must be cleanup after success of failure).
      List<File> resolvedImportsLocalFiles = null;
      try {
         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(protoFilePath));
         specContent = new String(bytes, StandardCharsets.UTF_8);

         // Resolve and retrieve imports if any.
         if (referenceResolver != null) {
            String rootBaseUrl = referenceResolver.getBaseRepositoryUrl();
            resolvedImportsLocalFiles = new ArrayList<>();
            resolveAndPrepareRemoteImports(protoPath, resolvedImportsLocalFiles, rootBaseUrl);
         }

         // Run Protoc.
         Protoc.runProtoc(args);

         File protoFileB = new File(protoDirectory, protoFileName + BINARY_DESCRIPTOR_EXT);
         try (var is = new FileInputStream(protoFileB)) {
            fds = DescriptorProtos.FileDescriptorSet.parseFrom(is);
         }
      } catch (InterruptedException ie) {
         log.error("Protobuf schema compilation has been interrupted on {}", protoFilePath);
         Thread.currentThread().interrupt();
      } catch (Exception e) {
         throw new IOException("Protobuf schema file parsing error on " + protoFilePath + ": " + e.getMessage());
      } finally {
         // Cleanup locally downloaded dependencies needed by protoc.
         if (resolvedImportsLocalFiles != null) {
            resolvedImportsLocalFiles.forEach(File::delete);
         }
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> results = new ArrayList<>();

      // Prepare dependencies.
      List<Descriptors.FileDescriptor> dependencies = new ArrayList<>();
      for (DescriptorProtos.FileDescriptorProto fdp : fds.getFileList()) {
         // Retrieve version from package name.
         // org.acme package => org.acme version
         // org.acme.v1 package => v1 version
         String packageName = fdp.getPackage();
         String[] parts = packageName.split("\\.");
         String version = (parts.length > 2 ? parts[parts.length - 1] : packageName);

         Descriptors.FileDescriptor fd = null;
         try {
            fd = Descriptors.FileDescriptor.buildFrom(fdp,
                  dependencies.toArray(new Descriptors.FileDescriptor[dependencies.size()]), true);
            dependencies.add(fd);
         } catch (Descriptors.DescriptorValidationException e) {
            throw new MockRepositoryImportException(
                  "Exception while building Protobuf descriptor, probably a missing dependency issue: "
                        + e.getMessage());
         }

         for (Descriptors.ServiceDescriptor sd : fd.getServices()) {
            if (packageServices.packageName != null && (!packageServices.packageName.equals(fd.getPackage()))
                  || !packageServices.services.contains(sd.getName())) {
               // If the service is not in the package and list of services, skip it.
               continue;
            }
            // Build a new service.
            Service service = new Service();
            service.setName(sd.getFullName());
            service.setVersion(version);
            service.setType(ServiceType.GRPC);
            service.setXmlNS(fd.getPackage());

            // Then build its operations.
            service.setOperations(extractOperations(sd));

            results.add(service);
         }
      }
      return results;
   }

   @Override
   public List<Resource> getResourceDefinitions(Service service) throws MockRepositoryImportException {
      List<Resource> results = new ArrayList<>();

      // Build 2 resources: one with plain text, another with base64 encoded binary descriptor.
      Resource textResource = new Resource();
      textResource.setName(service.getName() + "-" + service.getVersion() + ".proto");
      textResource.setType(ResourceType.PROTOBUF_SCHEMA);
      textResource.setContent(specContent);
      results.add(textResource);

      try {
         byte[] binaryPB = Files.readAllBytes(Path.of(protoDirectory, protoFileName + BINARY_DESCRIPTOR_EXT));
         String base64PB = new String(Base64.getEncoder().encode(binaryPB), StandardCharsets.UTF_8);

         Resource descResource = new Resource();
         descResource.setName(service.getName() + "-" + service.getVersion() + BINARY_DESCRIPTOR_EXT);
         descResource.setType(ResourceType.PROTOBUF_DESCRIPTOR);
         descResource.setContent(base64PB);
         results.add(descResource);
      } catch (Exception e) {
         log.error("Exception while encoding Protobuf binary descriptor into base64", e);
         throw new MockRepositoryImportException("Exception while encoding Protobuf binary descriptor into base64");
      }

      // Now build resources for dependencies if any.
      if (referenceResolver != null) {
         referenceResolver.getRelativeResolvedReferences().forEach((p, f) -> {
            Resource protoResource = new Resource();
            protoResource.setName(service.getName() + "-" + service.getVersion() + "-" + p.replace("/", "~1"));
            protoResource.setType(ResourceType.PROTOBUF_SCHEMA);
            protoResource.setPath(p);
            try {
               protoResource.setContent(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            } catch (IOException ioe) {
               log.warn("Exception while setting content of {} Protobuf resource", protoResource.getName(), ioe);
               log.warn("Pursuing on next resource as it was for information purpose only");
            }
            results.add(protoResource);
         });
         referenceResolver.cleanResolvedReferences();
      }
      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation)
         throws MockRepositoryImportException {
      return new ArrayList<>();
   }

   /** Initial proto file package and embedded services. */
   private record PackageServices(String packageName, List<String> services) {
   }

   /** Extract the package name from a proto file. */
   private PackageServices getPackage(Path protoFilePath) {
      String packageName = null;
      List<String> servicesNames = new ArrayList<>();

      String line = null;
      try (BufferedReader reader = Files.newBufferedReader(protoFilePath, StandardCharsets.UTF_8)) {
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith(PACKAGE)) {
               // Extract package name.
               String protoPackage = line.substring(PACKAGE.length()).trim();
               if (protoPackage.endsWith(";")) {
                  protoPackage = protoPackage.substring(0, protoPackage.length() - 1);
               }
               packageName = protoPackage;
            } else if (line.startsWith("service ")) {
               // Extract service name.
               String serviceName = line.substring("service ".length()).trim();
               if (serviceName.endsWith("{")) {
                  serviceName = serviceName.substring(0, serviceName.length() - 1).trim();
               }
               servicesNames.add(serviceName);
            }
         }
      } catch (Exception e) {
         log.error("Exception while retrieving protobuf package", e);
      }
      return new PackageServices(packageName, servicesNames);
   }

   /**
    * Analyse a protofile imports, resolve and retrieve them from remote to allow protoc to run later.
    */
   private void resolveAndPrepareRemoteImports(Path protoFilePath, List<File> resolvedImportsLocalFiles,
         String rootBaseUrl) {

      String protoPackage = null;

      try (BufferedReader reader = Files.newBufferedReader(protoFilePath, StandardCharsets.UTF_8)) {
         String line;
         while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.startsWith(PACKAGE)) {
               // Extract package name
               protoPackage = line.substring(PACKAGE.length()).trim();
               if (protoPackage.endsWith(";")) {
                  protoPackage = protoPackage.substring(0, protoPackage.length() - 1);
               }
               log.debug("Found a package in protobuf: {}", protoPackage);

            } else if (line.startsWith("import ")) {
               processImportLine(line, protoFilePath, protoPackage, resolvedImportsLocalFiles, rootBaseUrl);
            }
         }
      } catch (Exception e) {
         log.error("Exception while retrieving protobuf dependency", e);
      }
   }

   /**
    * Process a single import line, resolve and retrieve it if necessary.
    */
   private void processImportLine(String line, Path protoFilePath, String protoPackage,
         List<File> resolvedImportsLocalFiles, String rootBaseUrl) {

      String importStr = line.substring("import ".length() + 1).trim();

      // Remove semicolon and quotes/double-quotes
      if (importStr.endsWith(";")) {
         importStr = importStr.substring(0, importStr.length() - 1);
      }
      if (importStr.endsWith("\"") || importStr.endsWith("'")) {
         importStr = importStr.substring(0, importStr.length() - 1);
      }
      if (importStr.startsWith("\"") || importStr.startsWith("'")) {
         importStr = importStr.substring(1);
      }

      log.debug("Found an import to resolve in protobuf: {}", importStr);

      // Skip built-in imports
      if (importStr.startsWith(BUILTIN_LIBRARY_PREFIX)) {
         return;
      }

      Path importPath;
      try {
         if (protoPackage != null) {
            int levelsToRoot = protoPackage.split("\\.").length;
            String relativeImportStr = "../".repeat(levelsToRoot) + importStr;
            importPath = protoFilePath.getParent().resolve(relativeImportStr);
            downloadImportReferenceAndProgress(importPath, relativeImportStr, resolvedImportsLocalFiles, rootBaseUrl);
         } else {
            // No package, so just use the import string as is
            importPath = protoFilePath.getParent().resolve(importStr);
            downloadImportReferenceAndProgress(importPath, importStr, resolvedImportsLocalFiles, rootBaseUrl);
         }
      } catch (IOException e) {
         log.error("Error while processing import line for {}", importStr, e);
      }
   }

   /**
    * Download a remote import reference and write it to local file system. Progressively resolve its own imports.
    */
   private void downloadImportReferenceAndProgress(Path importPath, String importStr,
         List<File> resolvedImportsLocalFiles, String rootBaseUrl) throws IOException {
      referenceResolver.setBaseRepositoryUrl(rootBaseUrl);
      String importContent = referenceResolver.getReferenceContent(importStr, StandardCharsets.UTF_8);
      if (!Files.exists(importPath)) {
         try {
            Files.createDirectories(importPath.getParent());
            Files.createFile(importPath);
         } catch (FileAlreadyExistsException faee) {
            log.warn("Exception while writing protobuf dependency: {}", importPath.toFile().getAbsolutePath());
         }
      }
      log.debug("Writing protobuf import {} to {}", importStr, importPath);
      Files.write(importPath, importContent.getBytes(StandardCharsets.UTF_8));
      resolvedImportsLocalFiles.add(importPath.toFile());

      // Now go down the resource content and resolve its own imports.
      if (importStr.startsWith("../")) {
         rootBaseUrl = referenceResolver.getReferenceURL(importStr);
      }
      resolveAndPrepareRemoteImports(importPath, resolvedImportsLocalFiles, rootBaseUrl);
   }

   /**
    * Extract the operations from GRPC service methods.
    */
   private List<Operation> extractOperations(Descriptors.ServiceDescriptor service) {
      List<Operation> results = new ArrayList<>();

      for (Descriptors.MethodDescriptor method : service.getMethods()) {
         Operation operation = new Operation();
         operation.setName(method.getName());
         if (method.getInputType() != null) {
            operation.setInputName("." + method.getInputType().getFullName());

            boolean hasOnlyPrimitiveArgs = hasOnlyPrimitiveArgs(method);
            if (hasOnlyPrimitiveArgs && !method.getInputType().getFields().isEmpty()) {
               operation.setDispatcher(DispatchStyles.QUERY_ARGS);
               operation.setDispatcherRules(extractOperationParams(method.getInputType().getFields()));
            }
         }
         if (method.getOutputType() != null) {
            operation.setOutputName("." + method.getOutputType().getFullName());
         }

         results.add(operation);
      }
      return results;
   }

   /** Check if a method has only primitive arguments. */
   private static boolean hasOnlyPrimitiveArgs(Descriptors.MethodDescriptor method) {
      for (Descriptors.FieldDescriptor field : method.getInputType().getFields()) {
         Descriptors.FieldDescriptor.Type fieldType = field.getType();
         if (!isScalarType(fieldType)) {
            return false;
         }
      }
      return true;
   }

   /** Defines is a protobuf message field type is a scalar type. s */
   private static boolean isScalarType(Descriptors.FieldDescriptor.Type fieldType) {
      return fieldType != Descriptors.FieldDescriptor.Type.MESSAGE
            && fieldType != Descriptors.FieldDescriptor.Type.GROUP
            && fieldType != Descriptors.FieldDescriptor.Type.BYTES;
   }

   /** Build a string representing operation parameters as used in dispatcher rules (arg1 && arg2). */
   private static String extractOperationParams(List<Descriptors.FieldDescriptor> inputFields) {
      StringBuilder builder = new StringBuilder();

      for (Descriptors.FieldDescriptor inputField : inputFields) {
         builder.append(inputField.getName()).append(" && ");
      }
      return builder.substring(0, builder.length() - 4);
   }
}
