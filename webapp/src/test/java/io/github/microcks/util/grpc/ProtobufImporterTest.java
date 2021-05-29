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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.MockRepositoryImportException;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
         importer = new ProtobufImporter("target/test-classes/io/github/microcks/util/grpc/hello-v1.proto");
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
      assertEquals("HelloService", service.getName());
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
            assertEquals("HelloService-v1.proto", resource.getName());
         } else if (ResourceType.PROTOBUF_DESCRIPTOR.equals(resource.getType())) {
            assertEquals("HelloService-v1.pbb", resource.getName());
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
}
