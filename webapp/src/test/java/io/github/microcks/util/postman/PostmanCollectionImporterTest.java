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
package io.github.microcks.util.postman;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class PostmanCollectionImporter.
 * @author laurent
 */
class PostmanCollectionImporterTest {

   @Test
   void testSimpleProjectImportV2() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/Swagger Petstore.postman_collection.json");
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
      Service service = services.get(0);
      assertEquals("Swagger Petstore", service.getName());
      assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);

      // Check that operations and and input/output have been found.
      assertEquals(2, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /v2/pet/findByStatus".equals(operation.getName())) {
            // assertions for findByStatus.
            assertEquals("GET", operation.getMethod());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertTrue(operation.getResourcePaths().contains("/v2/pet/findByStatus"));

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            Exchange exchange = exchanges.get(0);
            if (exchange instanceof RequestResponsePair entry) {
               Request request = entry.getRequest();
               Response response = entry.getResponse();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("findbystatus-available", response.getName());
               assertEquals(1, response.getHeaders().size());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertEquals("?status=available?user_key=998bac0775b1d5f588e0a6ca7c11b852",
                     response.getDispatchCriteria());
               assertNotNull(response.getContent());
            } else {
               fail("Exchange has the wrong type. Expecting RequestResponsePair");
            }
         } else if ("GET /v2/pet/:petId".equals(operation.getName())) {
            // assertions for findById.
            assertEquals("GET", operation.getMethod());
            //assertEquals(2, operation.getResourcePaths().size());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/v2/pet/1"));
            assertTrue(operation.getResourcePaths().contains("/v2/pet/2"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  if ("findbyid-2".equals(request.getName())) {
                     assertEquals("findbyid-2", response.getName());
                     assertEquals(1, response.getHeaders().size());
                     assertEquals("200", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertEquals("/petId=2?user_key=998bac0775b1d5f588e0a6ca7c11b852", response.getDispatchCriteria());
                     assertNotNull(response.getContent());
                  } else if ("findbyid-1".equals(request.getName())) {
                     assertEquals("404", response.getStatus());
                     assertEquals("/petId=1?user_key=998bac0775b1d5f588e0a6ca7c11b852", response.getDispatchCriteria());
                  } else {
                     fail("Unknown request name: " + request.getName());
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testSimpleProjectImportV21() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/Swagger Petstore.postman_collection-2.1.json");
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
      Service service = services.get(0);
      assertEquals("Swagger Petstore", service.getName());
      assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.1", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);

      // Check that operations and and input/output have been found.
      assertEquals(2, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /v2/pet/findByStatus".equals(operation.getName())) {
            // assertions for findByStatus.
            assertEquals("GET", operation.getMethod());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertTrue(operation.getResourcePaths().contains("/v2/pet/findByStatus"));

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            Exchange exchange = exchanges.get(0);
            if (exchange instanceof RequestResponsePair entry) {
               Request request = entry.getRequest();
               Response response = entry.getResponse();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("findbystatus-available", response.getName());
               assertEquals(1, response.getHeaders().size());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertEquals("?status=available?user_key=998bac0775b1d5f588e0a6ca7c11b852",
                     response.getDispatchCriteria());
               assertNotNull(response.getContent());
            } else {
               fail("Exchange has the wrong type. Expecting RequestResponsePair");
            }
         } else if ("GET /v2/pet/:petId".equals(operation.getName())) {
            // assertions for findById.
            assertEquals("GET", operation.getMethod());
            //assertEquals(2, operation.getResourcePaths().size());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/v2/pet/1"));
            assertTrue(operation.getResourcePaths().contains("/v2/pet/2"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  if ("findbyid-2".equals(request.getName())) {
                     assertEquals("findbyid-2", response.getName());
                     assertEquals(1, response.getHeaders().size());
                     assertEquals("200", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertEquals("/petId=2?user_key=998bac0775b1d5f588e0a6ca7c11b852", response.getDispatchCriteria());
                     assertNotNull(response.getContent());
                  } else if ("findbyid-1".equals(request.getName())) {
                     assertEquals("404", response.getStatus());
                     assertEquals("/petId=1?user_key=998bac0775b1d5f588e0a6ca7c11b852", response.getDispatchCriteria());
                  } else {
                     fail("Unknown request name: " + request.getName());
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   void testTestAPIImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/Test API.postman_collection.json");
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
      Service service = services.get(0);
      assertEquals("Test API", service.getName());
      assertEquals(ServiceType.REST, service.getType());
      assertEquals("0.0.1", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);

      // Check that operations and and input/output have been found.
      assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("POST /order".equals(operation.getName())) {
            // Assertions for creation.
            assertEquals("POST", operation.getMethod());
            // TODO
            //assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  if ("create-123456".equals(request.getName())) {
                     assertNotNull(request.getContent());
                     assertNull(response.getHeaders());
                     assertEquals("201", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertNotNull(response.getContent());
                  } else if ("create-7891011".equals(request.getName())) {
                     assertNotNull(request.getContent());
                     assertNull(response.getHeaders());
                     assertEquals("201", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertNotNull(response.getContent());
                  } else {
                     fail("Unknown request name: " + request.getName());
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("GET /order".equals(operation.getName())) {
            // Assertions for listing.
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  if ("list-pending_approval".equals(request.getName())) {
                     assertNull(request.getContent());
                     assertNull(response.getHeaders());
                     assertEquals("200", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertNotNull(response.getContent());
                     assertEquals("?page=0?status=pending_approval", response.getDispatchCriteria());
                  } else if ("list-approved".equals(request.getName())) {
                     assertNull(request.getContent());
                     assertNull(response.getHeaders());
                     assertEquals("200", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertNotNull(response.getContent());
                     assertEquals("?page=0?status=approved", response.getDispatchCriteria());
                  } else {
                     fail("Unknown request name: " + request.getName());
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("GET /order/:id".equals(operation.getName())) {
            // Assertions for retrieval.
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/order/123456"));
            assertTrue(operation.getResourcePaths().contains("/order/7891011"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  if ("get-123456".equals(request.getName())) {
                     assertNull(request.getContent());
                     assertNull(response.getHeaders());
                     assertEquals("200", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertNotNull(response.getContent());
                     assertEquals("/id=123456", response.getDispatchCriteria());
                  } else if ("get-7891011".equals(request.getName())) {
                     assertNull(request.getContent());
                     assertNull(response.getHeaders());
                     assertEquals("200", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertNotNull(response.getContent());
                     assertEquals("/id=7891011", response.getDispatchCriteria());
                  } else {
                     fail("Unknown request name: " + request.getName());
                  }
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         }
      }
   }

   @Test
   void testTestAPINoVersionImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/Test API no version.postman_collection.json");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties import fail because of missing version.
      boolean failure = false;
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         failure = true;
         assertNotEquals(-1, e.getMessage().indexOf("Version property"));
      }
      assertTrue(failure);
   }

   @Test
   void testTestAPIMalformedVersionImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/Test API bad version.postman_collection.json");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties import fail because of missing version.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      assertEquals("0.0.1-Description", services.get(0).getVersion());
   }

   @ParameterizedTest
   @ValueSource(strings = {
         "target/test-classes/io/github/microcks/util/postman/structured-version-identifier-collection.json",
         "target/test-classes/io/github/microcks/util/postman/structured-version-digits-collection.json",
         "target/test-classes/io/github/microcks/util/postman/structured-version-raw-collection.json",
         "target/test-classes/io/github/microcks/util/postman/structured-version-desc-collection.json" })
   void testStructuredVersionImport(String collection) {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(collection);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties import fail because of missing version.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      assertEquals("1.0.0", services.get(0).getVersion());
   }

   @Test
   void testPetstoreWithTrailingDollarImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/PetstoreAPI-collection-sample-trailing-dollar.json");
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
      Service service = services.get(0);
      assertEquals("Petstore API", service.getName());
      assertEquals(ServiceType.REST, service.getType());
      assertEquals("12.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);

      // Check that operations and and input/output have been found.
      assertEquals(2, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("GET /v2/pet/:petId/$access".equals(operation.getName())) {
            // assertions for findById.
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("petId", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/v2/pet/1/$access"));
            assertTrue(operation.getResourcePaths().contains("/v2/pet/2/$access"));

         } else if ("GET /v2/pet/:petId/$count".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("petId", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/v2/pet/1/$count"));
            assertTrue(operation.getResourcePaths().contains("/v2/pet/2/$count"));
         } else {
            fail("Unknown operation");
         }
      }
   }

   @Test
   void testPetstoreWithTrailingSlashImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/PetstoreAPI-collection-sample.json");
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
      Service service = services.get(0);
      assertEquals("Petstore API", service.getName());
      assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);

      // Check that operations and and input/output have been found.
      assertEquals(2, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("GET /v2/pet/:petId".equals(operation.getName())) {
            // assertions for findById.
            assertEquals("GET", operation.getMethod());
            //assertEquals(2, operation.getResourcePaths().size());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/v2/pet/1"));
            assertTrue(operation.getResourcePaths().contains("/v2/pet/2"));
         }
      }
   }

   @Test
   void testGraphQLCollectionImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter(
               "target/test-classes/io/github/microcks/util/graphql/films-postman.json");
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
      Service service = services.get(0);
      assertEquals("Movie Graph API", service.getName());
      assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0", service.getVersion());

      // Check that operations and input/output have been found.
      assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("POST allFilms".equals(operation.getName())) {
            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());

            Exchange exchange = exchanges.get(0);
            assertTrue(exchange instanceof RequestResponsePair);
            RequestResponsePair pair = (RequestResponsePair) exchange;

            assertNotNull(pair.getRequest().getContent());
            assertNotNull(pair.getResponse().getContent());
            assertEquals("200", pair.getResponse().getStatus());
            assertNull(pair.getResponse().getDispatchCriteria());
         } else if ("POST film".equals(operation.getName())) {
            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());

            for (Exchange exchange : exchanges) {
               assertTrue(exchange instanceof RequestResponsePair);
               RequestResponsePair pair = (RequestResponsePair) exchange;

               assertNotNull(pair.getRequest().getContent());
               assertTrue(pair.getRequest().getContent().contains("\"id\": \"ZmlsbXM6MQ==\"")
                     || pair.getRequest().getContent().contains("\"id\": \"ZmlsbXM6Mg==\""));
               assertNotNull(pair.getResponse().getContent());
               assertEquals("200", pair.getResponse().getStatus());
            }
         } else if ("POST addStar".equals(operation.getName())) {
            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());

            Exchange exchange = exchanges.get(0);
            assertTrue(exchange instanceof RequestResponsePair);
            RequestResponsePair pair = (RequestResponsePair) exchange;

            assertNotNull(pair.getRequest().getContent());
            assertNotNull(pair.getResponse().getContent());
            assertEquals("200", pair.getResponse().getStatus());
         } else if ("POST addReview".equals(operation.getName())) {
            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());

            Exchange exchange = exchanges.get(0);
            assertTrue(exchange instanceof RequestResponsePair);
            RequestResponsePair pair = (RequestResponsePair) exchange;

            assertNotNull(pair.getRequest().getContent());
            assertNotNull(pair.getResponse().getContent());
            // Add this check to ensure that "comment" found in Postman variables is correctly
            // integrated into variables.
            assertTrue(pair.getRequest().getContent().contains("\"variables\":"));
            assertTrue(pair.getRequest().getContent().contains("\"comment\":"));
            assertEquals("200", pair.getResponse().getStatus());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }
}
