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
package io.github.microcks.util.postman;

import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.domain.*;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.*;

/**
 * This is a test case for class PostmanCollectionImporter.
 * @author laurent
 */
public class PostmanCollectionImporterTest {

   @Test
   public void testSimpleProjectImportV2() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/postman/Swagger Petstore.postman_collection.json");
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
      Assert.assertEquals(ServiceType.REST, service.getType());
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
            Assert.assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertEquals("/v2/pet/findByStatus", operation.getResourcePaths().get(0));

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try{
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e){
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            Exchange exchange = exchanges.get(0);
            if (exchange instanceof RequestResponsePair) {
               RequestResponsePair entry = (RequestResponsePair) exchange;
               Request request = entry.getRequest();
               Response response = entry.getResponse();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("findbystatus-available", response.getName());
               assertEquals(1, response.getHeaders().size());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertEquals("?status=available?user_key=998bac0775b1d5f588e0a6ca7c11b852", response.getDispatchCriteria());
               assertNotNull(response.getContent());
            } else {
               fail("Exchange has the wrong type. Expecting RequestResponsePair");
            }
         }
         else if ("GET /v2/pet/:petId".equals(operation.getName())) {
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
            assertTrue("/v2/pet/1".equals(operation.getResourcePaths().get(0))
                  || "/v2/pet/2".equals(operation.getResourcePaths().get(0)));
            assertTrue("/v2/pet/1".equals(operation.getResourcePaths().get(1))
                  || "/v2/pet/2".equals(operation.getResourcePaths().get(1)));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
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
   public void testSimpleProjectImportV21() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/postman/Swagger Petstore.postman_collection-2.1.json");
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
      Assert.assertEquals(ServiceType.REST, service.getType());
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
            Assert.assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertEquals("/v2/pet/findByStatus", operation.getResourcePaths().get(0));

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try{
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e){
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            Exchange exchange = exchanges.get(0);
            if (exchange instanceof RequestResponsePair) {
               RequestResponsePair entry = (RequestResponsePair) exchange;
               Request request = entry.getRequest();
               Response response = entry.getResponse();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("findbystatus-available", response.getName());
               assertEquals(1, response.getHeaders().size());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertEquals("?status=available?user_key=998bac0775b1d5f588e0a6ca7c11b852", response.getDispatchCriteria());
               assertNotNull(response.getContent());
            } else {
               fail("Exchange has the wrong type. Expecting RequestResponsePair");
            }
         }
         else if ("GET /v2/pet/:petId".equals(operation.getName())) {
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
            assertTrue("/v2/pet/1".equals(operation.getResourcePaths().get(0))
                  || "/v2/pet/2".equals(operation.getResourcePaths().get(0)));
            assertTrue("/v2/pet/1".equals(operation.getResourcePaths().get(1))
                  || "/v2/pet/2".equals(operation.getResourcePaths().get(1)));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
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
   public void testTestAPIImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/postman/Test API.postman_collection.json");
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
            try{
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e){
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
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
         }
         else if ("GET /order".equals(operation.getName())) {
            // Assertions for listing.
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try{
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e){
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
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
         }
         else if ("GET /order/:id".equals(operation.getName())) {
            // Assertions for retrieval.
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try{
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e){
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());

            assertTrue("/order/123456".equals(operation.getResourcePaths().get(0))
                  || "/order/7891011".equals(operation.getResourcePaths().get(0)));
            assertTrue("/order/123456".equals(operation.getResourcePaths().get(1))
                  || "/order/7891011".equals(operation.getResourcePaths().get(1)));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
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
   public void testTestAPINoVersionImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/postman/Test API no version.postman_collection.json");
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
   public void testPetstoreWithTrailingDollarImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/postman/PetstoreAPI-collection-sample-trailing-dollar.json");
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
      Assert.assertEquals(ServiceType.REST, service.getType());
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
            assertTrue("/v2/pet/1/$access".equals(operation.getResourcePaths().get(0))
                  || "/v2/pet/2/$access".equals(operation.getResourcePaths().get(0)));
            assertTrue("/v2/pet/1/$access".equals(operation.getResourcePaths().get(1))
                  || "/v2/pet/2/$access".equals(operation.getResourcePaths().get(1)));
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
            assertTrue("/v2/pet/1/$count".equals(operation.getResourcePaths().get(0))
                  || "/v2/pet/2/$count".equals(operation.getResourcePaths().get(0)));
            assertTrue("/v2/pet/1/$count".equals(operation.getResourcePaths().get(1))
                  || "/v2/pet/2/$count".equals(operation.getResourcePaths().get(1)));
         } else {
            fail("Unknown operation");
         }
      }
   }

   @Test
   public void testPetstoreWithTrailingSlashImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/postman/PetstoreAPI-collection-sample.json");
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
      Assert.assertEquals(ServiceType.REST, service.getType());
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
            assertTrue("/v2/pet/1".equals(operation.getResourcePaths().get(0))
                  || "/v2/pet/2".equals(operation.getResourcePaths().get(0)));
            assertTrue("/v2/pet/1".equals(operation.getResourcePaths().get(1))
                  || "/v2/pet/2".equals(operation.getResourcePaths().get(1)));
         }
      }
   }

   @Test
   public void testGraphQLCollectionImport() {
      PostmanCollectionImporter importer = null;
      try {
         importer = new PostmanCollectionImporter("target/test-classes/io/github/microcks/util/graphql/films-postman.json");
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

      // Check that operations and and input/output have been found.
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
               assertTrue(pair.getRequest().getContent().contains("film (id: \"ZmlsbXM6MQ==\")")
                  || pair.getRequest().getContent().contains("film (id: \"ZmlsbXM6Mg==\")"));
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
            // parsed and then serialized in request parameters without the enclosing double-quotes.
            // This is a particularity of GraphQL query that is not real JSON.
            assertTrue(pair.getRequest().getContent().contains("comment:"));
            assertEquals("200", pair.getResponse().getStatus());
         }
         else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }
}
