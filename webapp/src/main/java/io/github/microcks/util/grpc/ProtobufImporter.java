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

import com.github.os72.protocjar.Protoc;
import com.google.protobuf.DescriptorProtos;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;

import io.github.microcks.util.ReferenceResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * An implementation of MockRepositoryImporter that deals with Protobuf v3 specification documents.
 * @author laurent
 */
public class ProtobufImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ProtobufImporter.class);

   private String specContent;
   private String protoDirectory;
   private String protoFileName;
   private ReferenceResolver referenceResolver;
   private DescriptorProtos.FileDescriptorSet fds;

   private static final String BINARY_DESCRIPTOR_EXT = ".pbb";

   private static final String BUILTIN_LIBRARY_PREFIX = "google/protobuf";

   /**
    * Build a new importer.
    * @param protoFilePath     The path to local proto spec file
    * @param referenceResolver An optional resolver for references present into the Protobuf file
    * @throws IOException if project file cannot be found or read.
    */
   public ProtobufImporter(String protoFilePath, ReferenceResolver referenceResolver) throws IOException {
      this.referenceResolver = referenceResolver;
      // Prepare file, path and name for easier process.
      File protoFile = new File(protoFilePath);
      protoDirectory = protoFile.getParentFile().getAbsolutePath();
      protoFileName = protoFile.getName();

      // Prepare protoc arguments.
      String[] args = { "-v3.21.8", "--include_std_types", "--include_imports", "--proto_path=" + protoDirectory,
            "--descriptor_set_out=" + protoDirectory + "/" + protoFileName + BINARY_DESCRIPTOR_EXT, protoFileName };

      try {
         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(protoFilePath));
         specContent = new String(bytes, StandardCharsets.UTF_8);

         // Resolve and retrieve imports if any.
         List<File> resolvedImportsLocalFiles = null;
         if (referenceResolver != null) {
            resolvedImportsLocalFiles = new ArrayList<>();
            resolveAndPrepareRemoteImports(Paths.get(protoFilePath), resolvedImportsLocalFiles);
         }

         // Run Protoc.
         int result = Protoc.runProtoc(args);

         File protoFileB = new File(protoDirectory + "/" + protoFileName + BINARY_DESCRIPTOR_EXT);
         fds = DescriptorProtos.FileDescriptorSet.parseFrom(new FileInputStream(protoFileB));

         // Cleanup locally downloaded dependencies needed by protoc.
         if (resolvedImportsLocalFiles != null) {
            resolvedImportsLocalFiles.forEach(File::delete);
         }
      } catch (Exception e) {
         log.error("Exception while parsing Protobuf schema file " + protoFilePath, e);
         throw new IOException("Protobuf schema file parsing error");
      }
   }

   @Override
   public List<Service> getServiceDefinitions() throws MockRepositoryImportException {
      List<Service> results = new ArrayList<>();

      for (DescriptorProtos.FileDescriptorProto fdp : fds.getFileList()) {
         // Retrieve version from package name.
         // org.acme package => org.acme version
         // org.acme.v1 package => v1 version
         String packageName = fdp.getPackage();
         String[] parts = packageName.split("\\.");
         String version = (parts.length > 2 ? parts[parts.length - 1] : packageName);

         for (DescriptorProtos.ServiceDescriptorProto sdp : fdp.getServiceList()) {
            // Build a new service.
            Service service = new Service();
            service.setName(packageName + "." + sdp.getName());
            service.setVersion(version);
            service.setType(ServiceType.GRPC);
            service.setXmlNS(packageName);

            // Then build its operations.
            service.setOperations(extractOperations(sdp));

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
            protoResource.setName(service.getName() + "-" + service.getVersion() + "-" + p.replaceAll("/", "~1"));
            protoResource.setType(ResourceType.PROTOBUF_SCHEMA);
            protoResource.setPath(p);
            try {
               protoResource.setContent(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            } catch (IOException ioe) {
               log.error("", ioe);
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

   /**
    * Analyse a protofile imports, resolve and retrieve them from remote to allow protoc to run later.
    */
   private void resolveAndPrepareRemoteImports(Path protoFilePath, List<File> resolvedImportsLocalFiles) {
      String line = null;
      try (BufferedReader reader = Files.newBufferedReader(protoFilePath, StandardCharsets.UTF_8)) {
         while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.startsWith("import ")) {
               String importStr = line.substring("import ".length() + 1);
               // Remove semicolon and quotes/double-quotes.
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

               // Check that this lib is not in built-in ones.
               if (!importStr.startsWith(BUILTIN_LIBRARY_PREFIX)) {
                  // Check if import path is locally there.
                  Path importPath = protoFilePath.getParent().resolve(importStr);
                  if (!Files.exists(importPath)) {
                     // Not there, so resolve it remotely and write to local file for protoc.
                     String importContent = referenceResolver.getHttpReferenceContent(importStr,
                           StandardCharsets.UTF_8);
                     try {
                        Files.createDirectories(importPath.getParent());
                        Files.createFile(importPath);
                     } catch (FileAlreadyExistsException faee) {
                        log.warn("Exception while writing protobuf dependency", faee);
                     }
                     Files.write(importPath, importContent.getBytes(StandardCharsets.UTF_8));
                     resolvedImportsLocalFiles.add(importPath.toFile());
                  }
               }
            }
         }
      } catch (Exception e) {
         log.error("Exception while retrieving protobuf dependency", e);
      }
   }

   /**
    * Extract the operations from GRPC service methods.
    */
   private List<Operation> extractOperations(DescriptorProtos.ServiceDescriptorProto service) {
      List<Operation> results = new ArrayList<>();

      for (DescriptorProtos.MethodDescriptorProto method : service.getMethodList()) {
         Operation operation = new Operation();
         operation.setName(method.getName());
         operation.setInputName(method.getInputType());
         operation.setOutputName(method.getOutputType());

         results.add(operation);
      }
      return results;
   }
}
