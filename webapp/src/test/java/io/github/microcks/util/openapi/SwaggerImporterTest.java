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
package io.github.microcks.util.openapi;

import io.github.microcks.domain.*;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;

import io.github.microcks.util.openapi.SwaggerImporter;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is a test case for class SwaggerImporter.
 * @author laurent
 */
public class SwaggerImporterTest {

   @Test
   public void testSimpleSwaggerImportYAML() {
      SwaggerImporter importer = null;
      try {
         importer = new SwaggerImporter(
               "target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-swagger.yaml", null);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleSwagger(importer);
   }

   @Test
   public void testSimpleSwaggerImportJSON() {
      SwaggerImporter importer = null;
      try {
         importer = new SwaggerImporter(
               "target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-swagger.json", null);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleSwagger(importer);
   }

   private void importAndAssertOnSimpleSwagger(SwaggerImporter importer) {
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("Beer Catalog API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("0.9", service.getVersion());

      // Now assert extensions parsing has been done.
      assertNotNull(service.getMetadata());
      assertEquals(3, service.getMetadata().getLabels().size());
      assertEquals("beers", service.getMetadata().getLabels().get("domain"));
      assertEquals("beta", service.getMetadata().getLabels().get("status"));

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.SWAGGER, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /beer".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());

         } else if ("GET /beer/{name}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("name", operation.getDispatcherRules());

         } else if ("GET /beer/findByStatus/{status}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("status", operation.getDispatcherRules());

         } else {
            fail("Unknown operation name: " + operation.getName());
         }

         // Check that messages have been ignored.
         List<Exchange> exchanges = null;
         try {
            exchanges = importer.getMessageDefinitions(service, operation);
         } catch (Exception e) {
            fail("No exception should be thrown when importing message definitions.");
         }
         assertEquals(0, exchanges.size());
      }
   }
}
