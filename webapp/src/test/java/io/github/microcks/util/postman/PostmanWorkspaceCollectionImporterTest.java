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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class PostmanWorkspaceCollectionImporter.
 * @author laurent
 */
class PostmanWorkspaceCollectionImporterTest {

   @Test
   void testSimpleProjectImportV21() {
      PostmanWorkspaceCollectionImporter importer = null;
      try {
         importer = new PostmanWorkspaceCollectionImporter(
               "target/test-classes/io/github/microcks/util/postman/Swagger Petstore.postman_workspace_collection-2.1.json");
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
}
