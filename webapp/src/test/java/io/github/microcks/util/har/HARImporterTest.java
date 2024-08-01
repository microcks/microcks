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
package io.github.microcks.util.har;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class HARImporter.
 * @author laurent
 */
class HARImporterTest {

   @Test
   void testSimpleHARImport() {
      HARImporter importer = null;
      try {
         importer = new HARImporter("target/test-classes/io/github/microcks/util/har/api-pastry-2.0.har");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("API Pastry - 2.0", service.getName());
      assertEquals("2.0.0", service.getVersion());

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().get(0);
      assertEquals("GET /rest/API+Pastry+-+2.0/2.0.0/pastry/{part1}", operation.getName());
      assertEquals("GET", operation.getMethod());
      assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
      assertEquals("part1", operation.getDispatcherRules());

      assertEquals(2, operation.getResourcePaths().size());

      List<Exchange> messages = null;
      try {
         messages = importer.getMessageDefinitions(service, operation);
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }

      assertEquals(2, messages.size());
   }

   @Test
   void testMissingCommentHARImport() {
      HARImporter importer = null;
      try {
         importer = new HARImporter("target/test-classes/io/github/microcks/util/har/microcks.har");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }
      // Check that basic service properties import fail because of missing comment.
      boolean failure = false;
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         failure = true;
         assertNotEquals(-1, e.getMessage().indexOf("Expecting a comment in HAR"));
      }
      assertTrue(failure);
   }

   @Test
   void testComplexHARImport() {
      HARImporter importer = null;
      try {
         importer = new HARImporter("target/test-classes/io/github/microcks/util/har/api-pastries-0.0.1.har");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("API Pastries", service.getName());
      assertEquals("0.0.1", service.getVersion());

      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("GET /rest/API+Pastries/0.0.1/pastries".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertEquals("size", operation.getDispatcherRules());

            List<Exchange> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (MockRepositoryImportException e) {
               fail("Exception should not be thrown");
            }

            assertEquals(3, messages.size());
            for (Exchange exchange : messages) {
               if (exchange instanceof RequestResponsePair pair) {
                  Request request = pair.getRequest();
                  Response response = pair.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals(request.getName(), response.getName());
                  assertEquals("application/json;charset=UTF-8", response.getMediaType());
                  assertEquals(1, request.getQueryParameters().size());
                  assertEquals("size", request.getQueryParameters().get(0).getName());
                  assertEquals(4, request.getHeaders().size());
                  if ("2023-08-17T08:43:27.244Z".equals(request.getName())) {
                     assertEquals("200", response.getStatus());
                     assertEquals("?size=M", response.getDispatchCriteria());
                     assertEquals("M", request.getQueryParameters().get(0).getValue());
                     assertNotNull(response.getContent());
                  } else if ("2023-08-17T08:42:25.547Z".equals(request.getName())) {
                     assertEquals("200", response.getStatus());
                     assertEquals("?size=S", response.getDispatchCriteria());
                     assertEquals("S", request.getQueryParameters().get(0).getValue());
                     assertNotNull(response.getContent());
                  } else if ("2023-08-16T13:03:11.873Z".equals(request.getName())) {
                     assertEquals("200", response.getStatus());
                     assertEquals("?size=L", response.getDispatchCriteria());
                     assertEquals("L", request.getQueryParameters().get(0).getValue());
                     assertNotNull(response.getContent());
                  } else {
                     fail("Unknown request name: " + request.getName());
                  }
               }
            }

         } else if ("GET /rest/API+Pastries/0.0.1/pastries/{part1}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("part1", operation.getDispatcherRules());

            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/rest/API+Pastries/0.0.1/pastries/Millefeuille"));
            assertTrue(operation.getResourcePaths().contains("/rest/API+Pastries/0.0.1/pastries/Eclair+Cafe"));

            List<Exchange> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (MockRepositoryImportException e) {
               fail("Exception should not be thrown");
            }

            assertEquals(2, messages.size());
            for (Exchange exchange : messages) {
               if (exchange instanceof RequestResponsePair pair) {
                  Request request = pair.getRequest();
                  Response response = pair.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals(request.getName(), response.getName());
                  assertEquals("application/json;charset=UTF-8", response.getMediaType());
                  if ("2023-08-17T08:43:38.913Z".equals(request.getName())) {
                     assertEquals("200", response.getStatus());
                     assertEquals("/part1=Millefeuille", response.getDispatchCriteria());
                     assertNotNull(response.getContent());
                     assertTrue(response.getContent().contains("Delicieux Millefeuille"));
                  } else if ("2023-08-17T08:43:48.168Z".equals(request.getName())) {
                     assertEquals("200", response.getStatus());
                     assertEquals("/part1=Eclair Cafe", response.getDispatchCriteria());
                     assertNotNull(response.getContent());
                     assertTrue(response.getContent().contains("Delicieux Eclair au Cafe"));
                  } else {
                     fail("Unknown request name: " + request.getName());
                  }
               }
            }

         } else if ("PATCH /rest/API+Pastries/0.0.1/pastries/Eclair+Cafe".equals(operation.getName())) {
            assertEquals("PATCH", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());

            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/rest/API+Pastries/0.0.1/pastries/Eclair+Cafe"));

            List<Exchange> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (MockRepositoryImportException e) {
               fail("Exception should not be thrown");
            }

            assertEquals(1, messages.size());
            Exchange exchange = messages.get(0);
            if (exchange instanceof RequestResponsePair pair) {
               Request request = pair.getRequest();
               Response response = pair.getResponse();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals(request.getName(), response.getName());
               assertEquals("application/json;charset=UTF-8", response.getMediaType());
               if ("2023-08-17T11:05:53.434+02:00".equals(request.getName())) {
                  assertEquals("200", response.getStatus());
                  assertNotNull(response.getContent());
                  assertTrue(response.getContent().contains("price"));
               } else {
                  fail("Unknown request name: " + request.getName());
               }
            }
         } else {
            fail("Found an unknown operation! " + operation.getName());
         }
      }
   }

   @Test
   void testComplexHARImportWithAPIPrefix() {
      HARImporter importer = null;
      try {
         importer = new HARImporter(
               "target/test-classes/io/github/microcks/util/har/api-pastries-0.0.1-with-prefix.har");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("API Pastries", service.getName());
      assertEquals("0.0.1", service.getVersion());

      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("GET /pastries".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertEquals("size", operation.getDispatcherRules());

            List<Exchange> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (MockRepositoryImportException e) {
               fail("Exception should not be thrown");
            }

            assertEquals(3, messages.size());

         } else if ("GET /pastries/{part1}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("part1", operation.getDispatcherRules());

            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/pastries/Millefeuille"));
            assertTrue(operation.getResourcePaths().contains("/pastries/Eclair+Cafe"));

            List<Exchange> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (MockRepositoryImportException e) {
               fail("Exception should not be thrown");
            }

            assertEquals(2, messages.size());
            for (Exchange exchange : messages) {
               if (exchange instanceof RequestResponsePair pair) {
                  Request request = pair.getRequest();
                  Response response = pair.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals(request.getName(), response.getName());
                  assertEquals("application/json;charset=UTF-8", response.getMediaType());
                  if ("2023-08-17T08:43:38.913Z".equals(request.getName())) {
                     assertEquals("200", response.getStatus());
                     assertEquals("/part1=Millefeuille", response.getDispatchCriteria());
                     assertNotNull(response.getContent());
                     assertTrue(response.getContent().contains("Delicieux Millefeuille"));
                  } else if ("2023-08-17T08:43:48.168Z".equals(request.getName())) {
                     assertEquals("200", response.getStatus());
                     assertEquals("/part1=Eclair Cafe", response.getDispatchCriteria());
                     assertNotNull(response.getContent());
                     assertTrue(response.getContent().contains("Delicieux Eclair au Cafe"));
                  } else {
                     fail("Unknown request name: " + request.getName());
                  }
               }
            }

         } else if ("PATCH /pastries/Eclair+Cafe".equals(operation.getName())) {
            assertEquals("PATCH", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());

            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/pastries/Eclair+Cafe"));

            List<Exchange> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (MockRepositoryImportException e) {
               fail("Exception should not be thrown");
            }

            assertEquals(1, messages.size());
         } else {
            fail("Found an unknown operation! " + operation.getName());
         }
      }
   }

   @Test
   void testGraphQLMessageImportWithExistingOperation() {
      HARImporter importer = null;
      try {
         importer = new HARImporter("target/test-classes/io/github/microcks/util/har/movie-graph-api-1.0.har");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());

      Service service = services.get(0);
      assertEquals("Movie Graph API", service.getName());
      assertEquals("1.0", service.getVersion());
      assertEquals(ServiceType.GRAPHQL, service.getType());

      assertEquals(1, service.getOperations().size());
      Operation operation = service.getOperations().get(0);
      assertEquals("film", operation.getName());
      assertEquals("QUERY", operation.getMethod());

      // Now simulate a secondary artifact import.
      operation.setDispatcher(DispatchStyles.QUERY_ARGS);
      operation.setDispatcherRules("id");

      List<Exchange> messages = null;
      try {
         messages = importer.getMessageDefinitions(service, operation);
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, messages.size());

      Exchange exchange = messages.get(0);
      if (exchange instanceof RequestResponsePair pair) {
         Request request = pair.getRequest();
         assertEquals("2023-08-23T11:15:01.184+02:00", request.getName());
         assertTrue(request.getContent().contains("query film ($id: String)"));
         assertTrue(request.getContent().contains("\"variables\":{"));
         assertTrue(request.getContent().contains("\"id\": \"3\""));

         Response response = pair.getResponse();
         assertEquals("2023-08-23T11:15:01.184+02:00", response.getName());
         assertEquals("200", response.getStatus());
         assertEquals("application/json", response.getMediaType());
         assertEquals("?id=3", response.getDispatchCriteria());
         assertNotNull(response.getContent());
      } else {
         fail("Exchange is of unexpected type");
      }
   }
}
