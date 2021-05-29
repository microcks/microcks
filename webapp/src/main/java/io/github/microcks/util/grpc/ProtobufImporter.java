/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util.grpc;

import com.github.os72.protocjar.Protoc;
import com.google.protobuf.DescriptorProtos;
import io.github.microcks.domain.*;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 *
 * @author laurent
 */
public class ProtobufImporter implements MockRepositoryImporter {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ProtobufImporter.class);

   private String specContent;
   private String protoDirectory;
   private String protoFileName;
   private DescriptorProtos.FileDescriptorSet fds;

   private static final String BINARY_DESCRIPTOR_EXT = ".pbb";

   /**
    * Build a new importer.
    * @param protoFilePath The path to local proto spec file
    * @throws IOException if project file cannot be found or read.
    */
   public ProtobufImporter(String protoFilePath) throws IOException {
      // Prepare file, path and name for easier process.
      File protoFile = new File(protoFilePath);
      protoDirectory = protoFile.getParentFile().getAbsolutePath();
      protoFileName = protoFile.getName();

      String[] args = {"-v3.11.4",
            "--proto_path=" + protoDirectory,
            "--descriptor_set_out=" + protoDirectory + "/" + protoFileName + BINARY_DESCRIPTOR_EXT,
            protoFileName};

      try {
         // Read spec bytes.
         byte[] bytes = Files.readAllBytes(Paths.get(protoFilePath));
         specContent = new String(bytes, Charset.forName("UTF-8"));

         int result = Protoc.runProtoc(args);

         File protoFileB = new File(protoDirectory + "/" + protoFileName + BINARY_DESCRIPTOR_EXT);
         fds = DescriptorProtos.FileDescriptorSet.parseFrom(new FileInputStream(protoFileB));
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
         String packageName =  fdp.getPackage();
         String[] parts = packageName.split("\\.");
         String version = (parts.length > 2 ? parts[parts.length - 1] : packageName);

         for (DescriptorProtos.ServiceDescriptorProto sdp : fdp.getServiceList()) {
            // Build a new service.
            Service service = new Service();
            service.setName(sdp.getName());
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
         String base64PB = new String(Base64.getEncoder().encode(binaryPB), "UTF-8");

         Resource descResource = new Resource();
         descResource.setName(service.getName() + "-" + service.getVersion() + BINARY_DESCRIPTOR_EXT);
         descResource.setType(ResourceType.PROTOBUF_DESCRIPTOR);
         descResource.setContent(base64PB);
         results.add(descResource);
      } catch (Exception e) {
         log.error("Exception while encoding Protobuf binary descriptor into base64", e);
         throw new MockRepositoryImportException("Exception while encoding Protobuf binary descriptor into base64");
      }
      return results;
   }

   @Override
   public List<Exchange> getMessageDefinitions(Service service, Operation operation) throws MockRepositoryImportException {
      List<Exchange> result = new ArrayList<>();
      return result;
   }

   /**
    * Extract the operations from GPRC service methods.
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
