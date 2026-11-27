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
package io.github.microcks.util.metadata;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ParameterConstraint;
import io.github.microcks.domain.Service;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class MetadataImporter.
 * @author laurent
 */
class MetadataImporterTest {

   @Test
   void testAPIMetadataImport() {
      MetadataImporter importer = null;
      try {
         importer = new MetadataImporter(
               "target/test-classes/io/github/microcks/util/metadata/hello-grpc-v1-metadata.yml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.getFirst();
      assertEquals("HelloService", service.getName());
      assertEquals("v1", service.getVersion());

      assertEquals(3, service.getMetadata().getLabels().size());
      assertEquals("greeting", service.getMetadata().getLabels().get("domain"));
      assertEquals("stable", service.getMetadata().getLabels().get("status"));
      assertEquals("Team A", service.getMetadata().getLabels().get("team"));

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().getFirst();

      assertEquals("POST /greeting", operation.getName());
      assertEquals(Long.valueOf(100), operation.getDefaultDelay());
      assertEquals(DispatchStyles.JSON_BODY, operation.getDispatcher());
      assertNotNull(operation.getDispatcherRules());
   }

   @Test
   void testAPIMetadataWithParameterConstraintsImport() {
      MetadataImporter importer = null;
      try {
         importer = new MetadataImporter(
               "target/test-classes/io/github/microcks/util/metadata/APIPastry-2.0-metadata.yml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.getFirst();
      assertEquals("API Pastry - 2.0", service.getName());
      assertEquals("2.0.0", service.getVersion());

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().getFirst();

      assertEquals("GET /pastry/{name}", operation.getName());
      assertEquals(Long.valueOf(100), operation.getDefaultDelay());
      assertNotNull(operation.getParameterConstraints());
      assertEquals(2, operation.getParameterConstraints().size());

      for (ParameterConstraint constraint : operation.getParameterConstraints()) {
         if ("Authorization".equals(constraint.getName())) {
            assertTrue(constraint.isRequired());
            assertFalse(constraint.isRecopy());
            assertEquals("^Bearer\\s[a-zA-Z0-9\\._-]+$", constraint.getMustMatchRegexp());
         } else if ("x-request-id".equals(constraint.getName())) {
            assertTrue(constraint.isRequired());
            assertTrue(constraint.isRecopy());
            assertNull(constraint.getMustMatchRegexp());
         } else {
            fail("Unexpected parameter constraint");
         }
      }
   }
}
