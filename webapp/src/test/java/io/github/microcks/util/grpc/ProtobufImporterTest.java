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

import com.google.protobuf.DescriptorProtos;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.ReferenceResolver;
import org.junit.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is a test case for ProtobufImporter class.
 * @author laurent
 */
public class ProtobufImporterTest {

   @Test
   public void testSimpleProtobufImport() {
      ProtobufImporter importer = null;
      try {
         importer = new ProtobufImporter("target/test-classes/io/github/microcks/util/grpc/hello-v1.proto", null);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Service definition import should not fail");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("io.github.microcks.grpc.hello.v1.HelloService", service.getName());
      assertEquals(ServiceType.GRPC, service.getType());
      assertEquals("v1", service.getVersion());
      assertEquals("io.github.microcks.grpc.hello.v1", service.getXmlNS());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = null;
      try {
         resources = importer.getResourceDefinitions(service);
      } catch (MockRepositoryImportException mrie) {
         fail("Resource definition import should not fail");
      }
      assertEquals(2, resources.size());
      for (Resource resource : resources) {
         assertNotNull(resource.getContent());
         if (ResourceType.PROTOBUF_SCHEMA.equals(resource.getType())) {
            assertEquals("io.github.microcks.grpc.hello.v1.HelloService-v1.proto", resource.getName());
         } else if (ResourceType.PROTOBUF_DESCRIPTOR.equals(resource.getType())) {
            assertEquals("io.github.microcks.grpc.hello.v1.HelloService-v1.pbb", resource.getName());
         } else {
            fail("Resource has not the expected type");
         }
      }

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      Operation operation = service.getOperations().get(0);
      assertEquals("greeting", operation.getName());
      assertEquals(".io.github.microcks.grpc.hello.v1.HelloRequest", operation.getInputName());
      assertEquals(".io.github.microcks.grpc.hello.v1.HelloResponse", operation.getOutputName());
   }

   @Test
   public void testProtobufWithOptionsImport() {
      ProtobufImporter importer = null;
      try {
         importer = new ProtobufImporter("target/test-classes/io/github/microcks/util/grpc/hello-v1-option.proto",
               null);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Service definition import should not fail");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("io.github.microcks.grpc.hello.v1.HelloService", service.getName());
      assertEquals(ServiceType.GRPC, service.getType());
      assertEquals("v1", service.getVersion());
      assertEquals("io.github.microcks.grpc.hello.v1", service.getXmlNS());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = null;
      try {
         resources = importer.getResourceDefinitions(service);
      } catch (MockRepositoryImportException mrie) {
         fail("Resource definition import should not fail");
      }
      assertEquals(2, resources.size());
      for (Resource resource : resources) {
         assertNotNull(resource.getContent());
         if (ResourceType.PROTOBUF_SCHEMA.equals(resource.getType())) {
            assertEquals("io.github.microcks.grpc.hello.v1.HelloService-v1.proto", resource.getName());
         } else if (ResourceType.PROTOBUF_DESCRIPTOR.equals(resource.getType())) {
            assertEquals("io.github.microcks.grpc.hello.v1.HelloService-v1.pbb", resource.getName());

            try {
               // Check Protobuf Descriptor.
               byte[] decodedBinaryPB = Base64.getDecoder().decode(resource.getContent().getBytes("UTF-8"));

               DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.parseFrom(decodedBinaryPB);
               assertEquals(4, fds.getFileCount());
               assertEquals("google/api/http.proto", fds.getFile(0).getName());
               assertEquals("google/api/annotations.proto", fds.getFile(2).getName());
               assertEquals("google/protobuf/descriptor.proto", fds.getFile(1).getName());
               assertEquals("hello-v1-option.proto", fds.getFile(3).getName());
            } catch (Exception e) {
               fail("Protobuf file descriptor is not correct");
            }
         } else {
            fail("Resource has not the expected type");
         }
      }

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      Operation operation = service.getOperations().get(0);
      assertEquals("greeting", operation.getName());
      assertEquals(".io.github.microcks.grpc.hello.v1.HelloRequest", operation.getInputName());
      assertEquals(".io.github.microcks.grpc.hello.v1.HelloResponse", operation.getOutputName());
   }

   @Test
   public void testProtobufWithDependenciesImport() {
      ProtobufImporter importer = null;
      try {
         importer = new ProtobufImporter("target/test-classes/io/github/microcks/util/grpc/goodbye-v1.proto", null);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Service definition import should not fail");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("io.github.microcks.grpc.goodbye.v1.GoodbyeService", service.getName());
      assertEquals(ServiceType.GRPC, service.getType());
      assertEquals("v1", service.getVersion());
      assertEquals("io.github.microcks.grpc.goodbye.v1", service.getXmlNS());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = null;
      try {
         resources = importer.getResourceDefinitions(service);
      } catch (MockRepositoryImportException mrie) {
         fail("Resource definition import should not fail");
      }
      assertEquals(2, resources.size());
      for (Resource resource : resources) {
         assertNotNull(resource.getContent());
         if (ResourceType.PROTOBUF_SCHEMA.equals(resource.getType())) {
            assertEquals("io.github.microcks.grpc.goodbye.v1.GoodbyeService-v1.proto", resource.getName());
         } else if (ResourceType.PROTOBUF_DESCRIPTOR.equals(resource.getType())) {
            assertEquals("io.github.microcks.grpc.goodbye.v1.GoodbyeService-v1.pbb", resource.getName());

            try {
               // Check Protobuf Descriptor.
               byte[] decodedBinaryPB = Base64.getDecoder().decode(resource.getContent().getBytes("UTF-8"));

               DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.parseFrom(decodedBinaryPB);
               assertEquals(2, fds.getFileCount());
               assertEquals("shared/uuid.proto", fds.getFile(0).getName());
               assertEquals("goodbye-v1.proto", fds.getFile(1).getName());
            } catch (Exception e) {
               fail("Protobuf file descriptor is not correct");
            }
         } else {
            fail("Resource has not the expected type");
         }
      }
   }

   @Test
   public void testProtobufWithRemoteDependenciesImport() {
      ProtobufImporter importer = null;
      try {
         importer = new ProtobufImporter("target/test-classes/io/github/microcks/util/grpc/remote/goodbye-v1.proto",
               new ReferenceResolver(
                     "https://raw.githubusercontent.com/microcks/microcks/1.5.x/webapp/src/test/resources/io/github/microcks/util/grpc/base.proto",
                     null, true));
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Service definition import should not fail");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("io.github.microcks.grpc.goodbye.v1.GoodbyeService", service.getName());
      assertEquals(ServiceType.GRPC, service.getType());
      assertEquals("v1", service.getVersion());
      assertEquals("io.github.microcks.grpc.goodbye.v1", service.getXmlNS());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = null;
      try {
         resources = importer.getResourceDefinitions(service);
      } catch (MockRepositoryImportException mrie) {
         fail("Resource definition import should not fail");
      }
      assertEquals(3, resources.size());
      for (Resource resource : resources) {
         assertNotNull(resource.getContent());
         if (ResourceType.PROTOBUF_SCHEMA.equals(resource.getType())) {
            assertTrue("io.github.microcks.grpc.goodbye.v1.GoodbyeService-v1.proto".equals(resource.getName())
                  || "io.github.microcks.grpc.goodbye.v1.GoodbyeService-v1-shared~1uuid.proto"
                        .equals(resource.getName()));
         } else if (ResourceType.PROTOBUF_DESCRIPTOR.equals(resource.getType())) {
            assertEquals("io.github.microcks.grpc.goodbye.v1.GoodbyeService-v1.pbb", resource.getName());
            try {
               // Check Protobuf Descriptor.
               byte[] decodedBinaryPB = Base64.getDecoder().decode(resource.getContent().getBytes("UTF-8"));

               DescriptorProtos.FileDescriptorSet fds = DescriptorProtos.FileDescriptorSet.parseFrom(decodedBinaryPB);
               assertEquals(2, fds.getFileCount());
               assertEquals("shared/uuid.proto", fds.getFile(0).getName());
               assertEquals("goodbye-v1.proto", fds.getFile(1).getName());
            } catch (Exception e) {
               fail("Protobuf file descriptor is not correct");
            }
         } else {
            fail("Resource has not the expected type");
         }
      }
   }
}
