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
package io.github.microcks.util.openapi;

import io.github.microcks.domain.*;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * This is a test case for class OpenAPIImporter.
 * @author laurent
 */
public class OpenAPIImporterTest {

   @Test
   public void testSimpleOpenAPIImportYAML() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPI(importer);
   }

   @Test
   public void testSimpleOpenAPIImportJSON() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi.json");
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPI(importer);
   }


   private void importAndAssertOnSimpleOpenAPI(OpenAPIImporter importer) {
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);

      // Check that operations and and input/output have been found.
      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
            assertEquals("owner ?? page && limit", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, messages.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals("/owner/laurent/car", operation.getResourcePaths().get(0));

            for (Map.Entry<Request, Response> entry : messages.entrySet()) {
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("laurent_cars", request.getName());
               assertEquals("laurent_cars", response.getName());
               assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
               assertEquals("200", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertNotNull(response.getContent());
            }
         }
         else if ("POST /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, messages.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertEquals("/owner/laurent/car", operation.getResourcePaths().get(0));

            for (Map.Entry<Request, Response> entry : messages.entrySet()) {
               Request request = entry.getKey();
               Response response = entry.getValue();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("laurent_307", request.getName());
               assertEquals("laurent_307", response.getName());
               assertEquals("/owner=laurent", response.getDispatchCriteria());
               assertEquals("201", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertNotNull(response.getContent());
            }
         }
         else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            Map<Request, Response> messages = null;
            try {
               messages = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, messages.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }
}
