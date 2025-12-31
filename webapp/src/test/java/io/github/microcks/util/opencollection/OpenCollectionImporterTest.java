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
package io.github.microcks.util.opencollection;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.MockRepositoryImportException;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class OpenCollectionImporter.
 * @author krisrr3
 */
class OpenCollectionImporterTest {

   @Test
   void testSimpleCollectionImport() {
      OpenCollectionImporter importer = null;
      try {
         importer = new OpenCollectionImporter(
               "target/test-classes/io/github/microcks/util/opencollection/sample-rest-api.json");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown: " + e.getMessage());
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("API Pastries", service.getName());
      assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed.
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_COLLECTION, resources.get(0).getType());

      // Check that operations have been found.
      // Note: Operations are grouped by method + path, so we have 5 unique operations:
      // GET /pastries, GET /pastries/:name, POST /pastries, PATCH /pastries/:name, DELETE /pastries/:name
      assertEquals(5, service.getOperations().size());

      for (Operation operation : service.getOperations()) {
         if ("GET /pastries".equals(operation.getName())) {
            // Assertions for Get All Pastries.
            assertEquals("GET", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions: " + e.getMessage());
            }
            assertEquals(1, exchanges.size());
            Exchange exchange = exchanges.get(0);
            if (exchange instanceof RequestResponsePair entry) {
               Request request = entry.getRequest();
               Response response = entry.getResponse();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("Success Response", response.getName());
               assertEquals("200", response.getStatus());
               assertNotNull(response.getContent());
               assertTrue(response.getContent().contains("Croissant"));
               assertTrue(response.getContent().contains("France"));
            } else {
               fail("Exchange has the wrong type. Expecting RequestResponsePair");
            }
         } else if ("GET /pastries/:name".equals(operation.getName())) {
            // Assertions for Get Pastry by Name.
            assertEquals("GET", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions: " + e.getMessage());
            }
            assertEquals(2, exchanges.size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  if ("Croissant".equals(response.getName())) {
                     assertEquals("200", response.getStatus());
                     assertNotNull(response.getContent());
                     assertTrue(response.getContent().contains("Croissant"));
                  } else if ("Pastry Not Found".equals(response.getName())) {
                     assertEquals("404", response.getStatus());
                     assertNotNull(response.getContent());
                     assertTrue(response.getContent().contains("Pastry not found"));
                  } else {
                     fail("Unknown response name: " + response.getName());
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("POST /pastries".equals(operation.getName())) {
            // Assertions for Create New Pastry operations.
            assertEquals("POST", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions: " + e.getMessage());
            }
            // Should have multiple POST examples (Belgian Waffle, Churro, Gateau piments, Napolitaine).
            assertEquals(4, exchanges.size());

            boolean foundBelgianWaffle = false;
            boolean foundChurro = false;
            boolean foundGateauPiments = false;
            boolean foundNapolitaine = false;

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);

                  if (response.getName().contains("Belgian Waffle")) {
                     foundBelgianWaffle = true;
                     assertEquals("201", response.getStatus());
                     assertTrue(response.getContent().contains("Belgian Waffle"));
                     assertTrue(response.getContent().contains("Belgium"));
                  } else if (response.getName().contains("Churro")) {
                     foundChurro = true;
                     assertEquals("201", response.getStatus());
                     assertTrue(response.getContent().contains("Churro"));
                  } else if (response.getName().contains("Gateau piments")) {
                     foundGateauPiments = true;
                     assertEquals("201", response.getStatus());
                     assertTrue(response.getContent().contains("Gateau piments"));
                     assertTrue(response.getContent().contains("Mauritius"));
                  } else if (response.getName().contains("Napolitaine")) {
                     foundNapolitaine = true;
                     assertEquals("201", response.getStatus());
                     assertTrue(response.getContent().contains("Napolitaine"));
                     assertTrue(response.getContent().contains("Mauritius"));
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }

            assertTrue(foundBelgianWaffle, "Should have found Belgian Waffle response");
            assertTrue(foundChurro, "Should have found Churro response");
            assertTrue(foundGateauPiments, "Should have found Gateau piments response");
            assertTrue(foundNapolitaine, "Should have found Napolitaine response");
         } else if ("PATCH /pastries/:name".equals(operation.getName())) {
            // Assertions for Update Pastry.
            assertEquals("PATCH", operation.getMethod());

            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions: " + e.getMessage());
            }
            assertEquals(1, exchanges.size());
            Exchange exchange = exchanges.get(0);
            if (exchange instanceof RequestResponsePair entry) {
               Response response = entry.getResponse();
               assertEquals("200", response.getStatus());
               assertTrue(response.getContent().contains("limited"));
            } else {
               fail("Exchange has the wrong type. Expecting RequestResponsePair");
            }
         } else if ("DELETE /pastries/:name".equals(operation.getName())) {
            // Assertions for Delete Pastry.
            assertEquals("DELETE", operation.getMethod());

            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions: " + e.getMessage());
            }
            assertEquals(2, exchanges.size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Response response = entry.getResponse();
                  if ("Deleted".equals(response.getName())) {
                     assertEquals("204", response.getStatus());
                  } else if ("Pastry Not Found".equals(response.getName())) {
                     assertEquals("404", response.getStatus());
                  } else {
                     fail("Unknown response name: " + response.getName());
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         }
      }
   }

   @Test
   void testInvalidVersion() {
      try {
         OpenCollectionImporter importer = new OpenCollectionImporter(
               "target/test-classes/io/github/microcks/util/opencollection/invalid-version.json");
         importer.getServiceDefinitions();
         fail("Should have thrown MockRepositoryImportException");
      } catch (IOException ioe) {
         fail("IOException should not be thrown");
      } catch (MockRepositoryImportException e) {
         assertTrue(e.getMessage().contains("Only OpenCollection v1.x is supported"));
      }
   }

   @Test
   void testMissingVersion() {
      try {
         OpenCollectionImporter importer = new OpenCollectionImporter(
               "target/test-classes/io/github/microcks/util/opencollection/missing-version.json");
         importer.getServiceDefinitions();
         fail("Should have thrown MockRepositoryImportException");
      } catch (IOException ioe) {
         fail("IOException should not be thrown");
      } catch (MockRepositoryImportException e) {
         assertTrue(e.getMessage().contains("Missing 'opencollection' version field"));
      }
   }
}
