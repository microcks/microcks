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
package io.github.microcks.util.protobuf;

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;

import com.github.os72.protocjar.Protoc;
import com.google.protobuf.DescriptorProtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * Helper class for compiling Protobuf schema resources (referenced by AsyncAPI messages payloads) into binary
 * descriptor resources that can be used to encode, decode and validate Protobuf messages without generated classes.
 * @author pbvittal
 */
public class ProtobufSchemaCompiler {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ProtobufSchemaCompiler.class);

   private ProtobufSchemaCompiler() {
      // Private constructor to hide the implicit public one.
   }

   /**
    * Build a {@code PROTOBUF_DESCRIPTOR} resource for each {@code PROTOBUF_SCHEMA} resource found in resources. The
    * descriptor resource is named after the schema resource with the {@link ProtobufUtil#DESCRIPTOR_RESOURCE_SUFFIX}
    * suffix, and keeps the same path and operations. Each schema must be self-contained: only Google well-known types
    * can be imported. Schemas that cannot be compiled are skipped.
    * @param resources The resources discovered during an import
    * @return The list of descriptor resources. May be empty.
    */
   public static List<Resource> buildDescriptorResources(List<Resource> resources) {
      List<Resource> results = new ArrayList<>();
      resources.stream().filter(resource -> ResourceType.PROTOBUF_SCHEMA.equals(resource.getType()))
            .forEach(protoSchema -> {
               try {
                  results.add(compileDescriptorResource(protoSchema));
               } catch (InterruptedException ie) {
                  log.error("Protobuf schema compilation has been interrupted on {}", protoSchema.getName());
                  Thread.currentThread().interrupt();
               } catch (Exception e) {
                  log.error(
                        "Protobuf schema {} cannot be compiled (only Google well-known types can be imported), "
                              + "Protobuf messages using it will not be mocked nor validated: {}",
                        protoSchema.getName(), e.getMessage());
               }
            });
      return results;
   }

   private static Resource compileDescriptorResource(Resource protoSchema) throws IOException, InterruptedException {
      Path protoDirectory = Files.createTempDirectory("microcks-protobuf-");
      try {
         String protoFileName = getFileName(protoSchema);
         Files.writeString(protoDirectory.resolve(protoFileName), protoSchema.getContent(), StandardCharsets.UTF_8);
         Path descriptorFile = protoDirectory.resolve(protoFileName + ProtobufUtil.DESCRIPTOR_RESOURCE_SUFFIX);

         String[] args = { "-v3.21.8", "--include_std_types", "--include_imports", "--proto_path=" + protoDirectory,
               "--descriptor_set_out=" + descriptorFile, protoFileName };
         int exitCode = Protoc.runProtoc(args);
         if (exitCode != 0 || !Files.exists(descriptorFile)) {
            throw new IOException("protoc exited with code " + exitCode);
         }

         byte[] descriptorBytes = Files.readAllBytes(descriptorFile);
         // Check this is a valid descriptor set before storing it.
         DescriptorProtos.FileDescriptorSet.parseFrom(descriptorBytes);

         Resource descriptorResource = new Resource();
         descriptorResource.setName(protoSchema.getName() + ProtobufUtil.DESCRIPTOR_RESOURCE_SUFFIX);
         descriptorResource.setPath(protoSchema.getPath());
         descriptorResource.setType(ResourceType.PROTOBUF_DESCRIPTOR);
         descriptorResource.setContent(Base64.getEncoder().encodeToString(descriptorBytes));
         if (protoSchema.getOperations() != null) {
            descriptorResource.setOperations(new HashSet<>(protoSchema.getOperations()));
         }
         return descriptorResource;
      } finally {
         deleteDirectory(protoDirectory);
      }
   }

   /** Get the file name of a schema from its reference path. */
   private static String getFileName(Resource protoSchema) {
      String path = protoSchema.getPath() != null ? protoSchema.getPath() : protoSchema.getName();
      if (path.contains("#")) {
         path = path.substring(0, path.indexOf('#'));
      }
      return path.substring(path.lastIndexOf('/') + 1);
   }

   private static void deleteDirectory(Path directory) {
      try (Stream<Path> paths = Files.walk(directory)) {
         paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      } catch (IOException ioe) {
         log.warn("Cannot clean Protobuf compilation directory {}: {}", directory, ioe.getMessage());
      }
   }
}
