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

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.ReferenceResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPI(importer);
   }

   @Test
   public void testSimpleOpenAPIImportJSON() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi.json", null);
      } catch (IOException ioe) {
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPI(importer);
   }

   @Test
   public void testSimpleOpenAPIImportYAMLWithExtensions() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/cars-openapi-extensions.yaml", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPIWithExtensions(importer);
   }

   @Test
   public void testSimpleOpenAPIImportJSONWithExtensions() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/cars-openapi-extensions.json", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPIWithExtensions(importer);
   }

   @Test
   public void testSimpleOpenAPIImportYAMLWithQuotes() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-quoted.yaml",
               null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPI(importer);
   }

   @Test
   public void testApicurioPetstoreOpenAPI() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json",
               null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("PetStore API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that operations and input/output have been found.
      assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /pets".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertEquals("tags && limit", operation.getDispatcherRules());

         } else if ("GET /pets/{id}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("id", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/pets/1"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("zaza", request.getName());
                  assertEquals("zaza", response.getName());
                  assertEquals("/id=1", response.getDispatchCriteria());
                  assertEquals("200", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("POST /pets".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertNull(operation.getDispatcher());
            assertNull(operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/pets"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("tigresse", request.getName());
                  assertEquals("tigresse", response.getName());
                  assertNull(response.getDispatchCriteria());
                  assertEquals("201", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("DELETE /pets/{id}".equals(operation.getName())) {
            assertEquals("DELETE", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("id", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, exchanges.size());
            assertNull(operation.getResourcePaths());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   public void testSimpleOpenAPIImportYAMLNoDashesWithJSON() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/cars-openapi-with-json.yaml", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());
   }

   @Test
   public void testOpenAPIWithOpsPathParameter() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/locations-openapi.json",
               null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("LocationById", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0", service.getVersion());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /location/{id}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("id", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/location/83"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("location", request.getName());
                  assertEquals("location", response.getName());
                  assertEquals("/id=83", response.getDispatchCriteria());
                  assertEquals("200", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
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
   public void testOpenAPIImportYAMLWithSpacesOps() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/cars-openapi-spacesops.yaml", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnSimpleOpenAPI(importer);
   }

   @Test
   public void testOpenAPIImportYAMLWithHeaders() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-headers.yaml",
               null);
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
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operation and input/output have been found.
      assertEquals(1, service.getOperations().size());

      Operation operation = service.getOperations().get(0);
      assertEquals("GET /owner/{owner}/car", operation.getName());
      assertEquals("GET", operation.getMethod());
      assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
      assertEquals("owner ?? page && limit && x-user-id", operation.getDispatcherRules());

      // Check that messages have been correctly found.
      List<Exchange> exchanges = null;
      try {
         exchanges = importer.getMessageDefinitions(service, operation);
      } catch (Exception e) {
         fail("No exception should be thrown when importing message definitions.");
      }
      assertEquals(1, exchanges.size());
      assertEquals(1, operation.getResourcePaths().size());
      assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

      for (Exchange exchange : exchanges) {
         if (exchange instanceof RequestResponsePair) {
            RequestResponsePair entry = (RequestResponsePair) exchange;
            Request request = entry.getRequest();
            Response response = entry.getResponse();
            assertNotNull(request);
            assertNotNull(response);
            assertEquals("laurent_cars", request.getName());
            assertEquals("laurent_cars", response.getName());
            assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
            assertEquals("200", response.getStatus());
            assertEquals("application/json", response.getMediaType());
            assertNotNull(response.getContent());

            // Check headers now.
            assertEquals(2, request.getHeaders().size());
            Iterator<Header> headers = request.getHeaders().iterator();
            while (headers.hasNext()) {
               Header header = headers.next();
               if ("x-user-id".equals(header.getName())) {
                  assertEquals(1, header.getValues().size());
                  assertEquals("poiuytrezamlkjhgfdsq", header.getValues().iterator().next());
               } else if ("Accept".equals(header.getName())) {
                  assertEquals(1, header.getValues().size());
                  assertEquals("application/json", header.getValues().iterator().next());
               } else {
                  fail("Unexpected header name in request");
               }
            }

            assertEquals(1, response.getHeaders().size());
            Header header = response.getHeaders().iterator().next();
            assertEquals("x-result-count", header.getName());
            assertEquals(1, header.getValues().size());
            assertEquals("2", header.getValues().iterator().next());
         } else {
            fail("Exchange has the wrong type. Expecting RequestResponsePair");
         }
      }
   }

   @Test
   public void testOpenAPIJsonPointer() {
      try {
         ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
         byte[] bytes = Files
               .readAllBytes(Paths.get("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml"));
         JsonNode openapiSpec = mapper.readTree(bytes);

         String verb = "get";
         String path = "/owner/{owner}/car";

         String pointer = "/paths/" + path.replace("/", "~1") + "/" + verb + "/responses/200/content/"
               + "application/json".replace("/", "~1");

         JsonNode responseNode = openapiSpec.at(pointer);
         assertNotNull(responseNode);
         assertFalse(responseNode.isMissingNode());
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
   }

   @Test
   public void testCompleteOpenAPIImportYAML() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/cars-openapi-complete.yaml", null);
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
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
            assertEquals("owner ?? page && limit", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_cars", request.getName());
                  assertEquals("laurent_cars", response.getName());
                  assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
                  assertEquals("200", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("POST /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_307", request.getName());
                  assertEquals("laurent_307", response.getName());
                  assertEquals("/owner=laurent", response.getDispatchCriteria());
                  assertEquals("201", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("GET /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car/307/passenger"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_307_passengers", request.getName());
                  assertEquals("laurent_307_passengers", response.getName());
                  assertEquals("/car=307/owner=laurent", response.getDispatchCriteria());
                  assertEquals("200", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, exchanges.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   public void testCompleteOpenAPI31ImportYAML() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/cars-openapi-3.1-complete.yaml", null);
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
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(4, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
            assertEquals("owner ?? page && limit", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_cars", request.getName());
                  assertEquals("laurent_cars", response.getName());
                  assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
                  assertEquals("200", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("POST /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               e.printStackTrace();
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_307", request.getName());
                  assertEquals("laurent_307", response.getName());
                  assertEquals("/owner=laurent", response.getDispatchCriteria());
                  assertEquals("201", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("GET /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car/307/passenger"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_307_passengers", request.getName());
                  assertEquals("laurent_307_passengers", response.getName());
                  assertEquals("/car=307/owner=laurent", response.getDispatchCriteria());
                  assertEquals("200", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, exchanges.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   public void testUncompleteParamsOpenAPIImportYAML() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/cars-openapi-uncomplete-params.yaml", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("OpenAPI Car API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
            assertEquals("owner ?? page && limit", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, exchanges.size());
         } else if ("POST /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_307", request.getName());
                  assertEquals("laurent_307", response.getName());
                  assertEquals("/owner=laurent", response.getDispatchCriteria());
                  assertEquals("201", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("GET /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }

            // TODO: below should be a failure. We currently detect 1 message as we should have 0
            // cause car path parameters is missing. We should add a test into URIBuilder.buildFromParamsMap()
            // to check that we have at least the number of params what we have into uri pattern.
            //assertEquals(0, messages.size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_307_passengers", request.getName());
                  assertEquals("laurent_307_passengers", response.getName());
                  assertEquals("/owner=laurent", response.getDispatchCriteria());
                  assertEquals("200", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
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
   public void testExampleValueDeserializationYAML() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/test-openapi.yaml", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnTestOpenAPI(importer);
   }

   @Test
   public void testExampleValueDeserializationYAMLYAML() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/test-openapi-yaml.yaml",
               null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnTestOpenAPI(importer);
   }

   @Test
   public void testExampleValueDeserializationJSON() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/test-openapi.json", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnTestOpenAPI(importer);
   }

   @Test
   public void testExampleValueDeserializationJSONJSON() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/test-openapi-json.json",
               null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
         fail("Exception should not be thrown");
      }

      importAndAssertOnTestOpenAPI(importer);
   }

   @Test
   public void testResponseRefsOpenAPIImport() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/cars-openapi-complex-refs.yaml", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("OpenAPI Car API with Refs", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("GET /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
            assertEquals("owner ?? page && limit && x-user-id", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);

                  if ("laurent_cars".equals(request.getName())) {
                     assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
                     assertEquals("200", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertNotNull(response.getContent());
                  } else if ("unknown".equals(request.getName())) {
                     assertEquals("/owner=unknown?limit=20?page=0", response.getDispatchCriteria());
                     assertEquals("404", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertEquals("{\"reason\": \"owner not found\"}", response.getContent());
                     assertEquals(1, response.getHeaders().size());

                     Header header = response.getHeaders().iterator().next();
                     assertEquals("my-custom-header", header.getName());
                     assertEquals("unknown", header.getValues().iterator().next());
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
   public void testParameterRefsOpenAPIImport() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/param-refs-openapi.yaml",
               null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("Sample API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0", service.getVersion());

      // Check that resources have been parsed, correctly renamed, etc...
      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("GET /accounts/{accountId}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("accountId", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/accounts/396be545-e2d4-4497-a5b5-700e89ab99c0"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);

                  if ("Example 1".equals(request.getName())) {
                     assertEquals("/accountId=396be545-e2d4-4497-a5b5-700e89ab99c0", response.getDispatchCriteria());
                     assertEquals("200", response.getStatus());
                     assertEquals("application/json", response.getMediaType());
                     assertNotNull(response.getContent());
                     assertEquals("{\"account\":{\"resourceId\":\"f377afb3-5c62-40cc-8f07-1f4749a780eb\"}}",
                           response.getContent());
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
   public void testQueryParameterRefsOpenAPIImport() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/query-param-refs-openapi.yaml", null);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("API-Template", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      // Check that operations and input/output have been found.
      assertEquals(2, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("GET /accounts".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertEquals("level", operation.getDispatcherRules());
         } else if ("GET /resources".equals(operation.getName())) {
            assertEquals(DispatchStyles.URI_PARAMS, operation.getDispatcher());
            assertEquals("resourceType", operation.getDispatcherRules());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   public void testExamplesRefsOpenAPIImport() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/examples-ref-openapi.yaml",
               null);
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
      assertEquals("Broken Ref", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("2.0.0", service.getVersion());

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());

      for (Operation operation : service.getOperations()) {

         if ("GET /v1.0/endpoint".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/v1.0/endpoint"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);

                  assertEquals("example1", request.getName());
                  assertEquals("example1", response.getName());
                  assertEquals("someValue", response.getContent());
               }
            }
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   public void testExternalRelativeReferenceOpenAPIImport() {
      OpenAPIImporter importer = null;
      ReferenceResolver resolver = new ReferenceResolver(
            "https://raw.githubusercontent.com/microcks/microcks/1.5.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref.yaml",
            null, true);
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref.yaml",
               resolver);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("WeatherForecast API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(2, resources.size());

      Resource openAPISpec = resources.get(0);
      assertEquals("WeatherForecast API-1.0.0.yaml", openAPISpec.getName());
      assertEquals(ResourceType.OPEN_API_SPEC, openAPISpec.getType());
      assertTrue(openAPISpec.getContent().contains("WeatherForecast+API-1.0.0--weather-forecast-schema.yaml"));

      Resource refSchema = resources.get(1);
      assertEquals("WeatherForecast API-1.0.0--weather-forecast-schema.yaml", refSchema.getName());
      assertEquals(ResourceType.JSON_SCHEMA, refSchema.getType());
      assertEquals("./weather-forecast-schema.yaml", refSchema.getPath());
      assertNotNull(refSchema.getContent());
      assertTrue(refSchema.getContent().contains("A weather forecast for a requested region"));
   }

   @Test
   public void testExternalRelativeReferenceWithJSONPointerOpenAPIImport() {
      OpenAPIImporter importer = null;
      ReferenceResolver resolver = new ReferenceResolver(
            "https://raw.githubusercontent.com/microcks/microcks/1.8.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref-example.yaml",
            null, true);
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref-example.yaml",
               resolver);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("WeatherForecast API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(3, resources.size());

      Resource openAPISpec = resources.get(0);
      assertEquals("WeatherForecast API-1.0.0.yaml", openAPISpec.getName());
      assertEquals(ResourceType.OPEN_API_SPEC, openAPISpec.getType());
      assertTrue(openAPISpec.getContent().contains("WeatherForecast+API-1.0.0--weather-forecast-schema.yaml"));

      for (int i = 1; i < 3; i++) {
         Resource refResource = resources.get(i);
         if ("WeatherForecast API-1.0.0--weather-examples.json".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_FRAGMENT, refResource.getType());
            assertEquals("./weather-examples.json", refResource.getPath());
            assertNotNull(refResource.getContent());
            assertTrue(refResource.getContent().contains("\"region\": \"east\""));
         } else if ("WeatherForecast API-1.0.0--weather-forecast-schema.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_SCHEMA, refResource.getType());
            assertEquals("./weather-forecast-schema.yaml", refResource.getPath());
            assertNotNull(refResource.getContent());
            assertTrue(refResource.getContent().contains("A weather forecast for a requested region"));
         } else {
            fail("Unknown ref resource found");
         }
      }

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("GET /forecast/{region}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(5, exchanges.size());
            assertEquals(5, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/forecast/north"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertNotNull(response.getContent());

                  if ("unknown".equals(request.getName())) {
                     assertEquals("Region is unknown. Choose in north, west, east or south.", response.getContent());
                  } else {
                     assertEquals("/region=" + request.getName(), response.getDispatchCriteria());
                     assertTrue(response.getContent().contains("\"region\":\"" + request.getName() + "\""));
                  }
               }
            }
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   public void testExternalAbsoluteReferenceOpenAPIImport() {
      OpenAPIImporter importer = null;
      ReferenceResolver resolver = new ReferenceResolver(
            "https://raw.githubusercontent.com/microcks/microcks/1.5.x/webapp/src/test/resources/io/github/microcks/util/openapi/",
            null, true);
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-absolute-ref.yaml",
               resolver);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("WeatherForecast API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(2, resources.size());

      Resource openAPISpec = resources.get(0);
      assertEquals("WeatherForecast API-1.0.0.yaml", openAPISpec.getName());
      assertEquals(ResourceType.OPEN_API_SPEC, openAPISpec.getType());
      assertFalse(openAPISpec.getContent().contains("WeatherForecast API-1.0.0-weather-forecast-schema.yaml"));

      Resource refSchema = resources.get(1);
      assertEquals("WeatherForecast API-1.0.0-weather-forecast-schema.yaml", refSchema.getName());
      assertEquals(ResourceType.JSON_SCHEMA, refSchema.getType());
      assertEquals(
            "https://raw.githubusercontent.com/microcks/microcks/1.5.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-schema.yaml",
            refSchema.getPath());
      assertNotNull(refSchema.getContent());
      assertTrue(refSchema.getContent().contains("A weather forecast for a requested region"));
   }

   @Test
   public void testExternalAbsoluteReferenceWithJSONPointerOpenAPIImport() {
      OpenAPIImporter importer = null;
      ReferenceResolver resolver = new ReferenceResolver(
            "https://raw.githubusercontent.com/microcks/microcks/1.8.x/webapp/src/test/resources/io/github/microcks/util/openapi/",
            null, true);
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-absolute-ref-pointers.yaml",
               resolver);
      } catch (IOException ioe) {
         ioe.printStackTrace();
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
      assertEquals("WeatherForecast API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(3, resources.size());

      Resource openAPISpec = resources.get(0);
      assertEquals("WeatherForecast API-1.0.0.yaml", openAPISpec.getName());
      assertEquals(ResourceType.OPEN_API_SPEC, openAPISpec.getType());
      assertFalse(openAPISpec.getContent().contains("WeatherForecast API-1.0.0-weather-forecast-schema.yaml"));

      for (int i = 1; i < 3; i++) {
         Resource refResource = resources.get(i);
         if ("WeatherForecast API-1.0.0-weather-forecast-common.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_FRAGMENT, refResource.getType());
            assertEquals(
                  "https://raw.githubusercontent.com/microcks/microcks/1.8.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-common.yaml",
                  refResource.getPath());
            assertNotNull(refResource.getContent());
            assertTrue(refResource.getContent().contains("title: Common objects to reuse"));
         } else if ("WeatherForecast API-1.0.0-weather-forecast-schema.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_SCHEMA, refResource.getType());
            assertEquals(
                  "https://raw.githubusercontent.com/microcks/microcks/1.8.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-schema.yaml",
                  refResource.getPath());
            assertNotNull(refResource.getContent());
            assertTrue(refResource.getContent().contains("A weather forecast for a requested region"));
         } else {
            fail("Unknown ref resource found");
         }
      }

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("GET /forecast/{region}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(5, exchanges.size());
            assertEquals(5, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/forecast/north"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertNotNull(response.getContent());

                  if ("unknown".equals(request.getName())) {
                     assertEquals("Region is unknown. Choose in north, west, east or south.", response.getContent());
                  } else {
                     assertEquals("/region=" + request.getName(), response.getDispatchCriteria());
                     assertTrue(response.getContent().contains("\"region\":\"" + request.getName() + "\""));
                  }
               }
            }
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   public void testExternalRelativeRecursiveReferenceWithJSONPointerOpenAPIImport() {
      OpenAPIImporter importer = null;
      ReferenceResolver resolver = new ReferenceResolver(
            "https://raw.githubusercontent.com/microcks/microcks/1.8.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-openapi-relative-recursive-ref.yaml",
            null, true);
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-recursive-ref.yaml",
               resolver);
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
      assertEquals("WeatherForecast API", service.getName());
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(4, resources.size());

      Resource openAPISpec = resources.get(0);
      assertEquals("WeatherForecast API-1.0.0.yaml", openAPISpec.getName());
      assertEquals(ResourceType.OPEN_API_SPEC, openAPISpec.getType());
      assertTrue(openAPISpec.getContent().contains("WeatherForecast+API-1.0.0--weather-forecast-schema.yaml"));

      for (int i = 1; i < 4; i++) {
         Resource refResource = resources.get(i);
         if ("WeatherForecast API-1.0.0--weather-forecast-schema.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_SCHEMA, refResource.getType());
            assertEquals("./weather-forecast-schema.yaml", refResource.getPath());
            assertNotNull(refResource.getContent());
            assertTrue(refResource.getContent().contains("A weather forecast for a requested region"));
         } else if ("WeatherForecast API-1.0.0--weather-forecast-examples.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_FRAGMENT, refResource.getType());
            assertEquals("./weather-forecast-examples.yaml", refResource.getPath());
            assertNotNull(refResource.getContent());
            assertTrue(refResource.getContent()
                  .contains("$ref: 'WeatherForecast+API-1.0.0--weather-forecast-common-regions.yaml#/regions/north'"));
         } else if ("WeatherForecast API-1.0.0--weather-forecast-common-regions.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_FRAGMENT, refResource.getType());
            assertEquals("./weather-forecast-common-regions.yaml", refResource.getPath());
            assertNotNull(refResource.getContent());
            assertTrue(refResource.getContent().contains("title: Common regions objects to reuse"));
         } else {
            fail("Unknown ref resource found");
         }
      }

      // Check that operations and input/output have been found.
      assertEquals(1, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("GET /forecast/{region}".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(5, exchanges.size());
            assertEquals(5, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/forecast/north"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertNotNull(response.getContent());

                  if ("unknown".equals(request.getName())) {
                     assertEquals("Region is unknown. Choose in north, west, east or south.", response.getContent());
                  } else {
                     assertEquals("/region=" + request.getName(), response.getDispatchCriteria());
                     assertTrue(response.getContent().contains("\"region\":\"" + request.getName() + "\""));
                  }
               }
            }
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   @Test
   public void testNoContentResponseOpenAPIImport() {
      OpenAPIImporter importer = null;
      try {
         importer = new OpenAPIImporter(
               "target/test-classes/io/github/microcks/util/openapi/test-openapi-nocontent.yaml", null);
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
      Assert.assertEquals(ServiceType.REST, service.getType());
      assertEquals("1.0.0", service.getVersion());

      List<Resource> resources = importer.getResourceDefinitions(service);
      assertEquals(1, resources.size());

      // Check that operations and input/output have been found.
      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("DELETE /tests/{id}".equals(operation.getName())) {
            assertEquals("DELETE", operation.getMethod());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(2, exchanges.size());
            assertEquals(2, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/tests/66")
                  || operation.getResourcePaths().contains("/tests/77"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair entry) {
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertNull(response.getContent());

                  if ("to-delete-1".equals(request.getName())) {
                     assertEquals("204", response.getStatus());
                     assertEquals("/id=66", response.getDispatchCriteria());
                     assertFalse(response.isFault());
                     assertEquals(1, request.getQueryParameters().size());
                  } else if ("to-delete-2".equals(request.getName())) {
                     assertEquals("418", response.getStatus());
                     assertEquals("/id=77", response.getDispatchCriteria());
                     assertTrue(response.isFault());
                     assertEquals(1, request.getQueryParameters().size());
                  } else {
                     fail("Unknown request");
                  }
               }
            }
         }
      }
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
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(3, service.getOperations().size());
      for (Operation operation : service.getOperations()) {

         if ("GET /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("GET", operation.getMethod());
            assertEquals(DispatchStyles.URI_ELEMENTS, operation.getDispatcher());
            assertEquals("owner ?? page && limit", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_cars", request.getName());
                  assertEquals("laurent_cars", response.getName());
                  assertEquals("/owner=laurent?limit=20?page=0", response.getDispatchCriteria());
                  assertEquals("200", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("POST /owner/{owner}/car".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());
            assertEquals(1, operation.getResourcePaths().size());
            assertTrue(operation.getResourcePaths().contains("/owner/laurent/car"));

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertEquals("laurent_307", request.getName());
                  assertEquals("laurent_307", response.getName());
                  assertEquals("/owner=laurent", response.getDispatchCriteria());
                  assertEquals("201", response.getStatus());
                  assertEquals("application/json", response.getMediaType());
                  assertNotNull(response.getContent());
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
            assertEquals("POST", operation.getMethod());
            assertEquals(DispatchStyles.URI_PARTS, operation.getDispatcher());
            assertEquals("owner && car", operation.getDispatcherRules());

            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(0, exchanges.size());
         } else {
            fail("Unknown operation name: " + operation.getName());
         }
      }
   }

   private void importAndAssertOnSimpleOpenAPIWithExtensions(OpenAPIImporter importer) {
      // Basic import and assertions.
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
      assertEquals(1, resources.size());
      assertEquals(ResourceType.OPEN_API_SPEC, resources.get(0).getType());
      assertTrue(resources.get(0).getName().startsWith(service.getName() + "-" + service.getVersion()));
      assertNotNull(resources.get(0).getContent());

      // Check that operations and input/output have been found.
      assertEquals(3, service.getOperations().size());

      try {
         // Now assert extensions parsing has been done.
         assertNotNull(service.getMetadata());
         assertEquals(3, service.getMetadata().getLabels().size());
         assertEquals("cars", service.getMetadata().getLabels().get("domain"));
         assertEquals("beta", service.getMetadata().getLabels().get("status"));
         assertEquals("Team A", service.getMetadata().getLabels().get("team"));

         Operation postOp = service.getOperations().stream()
               .filter(operation -> operation.getName().equals("POST /owner/{owner}/car")).findFirst().get();

         assertEquals("POST", postOp.getMethod());
         assertEquals(100, postOp.getDefaultDelay().longValue());
         assertEquals("SCRIPT", postOp.getDispatcher());
         assertTrue(postOp.getDispatcherRules().contains("groovy.json.JsonSlurper"));

         // Check that messages have been correctly found.
         List<Exchange> exchanges = null;
         try {
            exchanges = importer.getMessageDefinitions(service, postOp);
         } catch (Exception e) {
            fail("No exception should be thrown when importing message definitions.");
         }
         assertEquals(1, exchanges.size());
         assertEquals(1, postOp.getResourcePaths().size());
         assertTrue(postOp.getResourcePaths().contains("/owner/{owner}/car"));

         for (Exchange exchange : exchanges) {
            if (exchange instanceof RequestResponsePair) {
               RequestResponsePair entry = (RequestResponsePair) exchange;
               Request request = entry.getRequest();
               Response response = entry.getResponse();
               assertNotNull(request);
               assertNotNull(response);
               assertEquals("laurent_307", request.getName());
               assertEquals("laurent_307", response.getName());
               assertNull(response.getDispatchCriteria());
               assertEquals("201", response.getStatus());
               assertEquals("application/json", response.getMediaType());
               assertNotNull(response.getContent());
            } else {
               fail("Exchange has the wrong type. Expecting RequestResponsePair");
            }
         }
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception should not be thrown");
      }
   }

   private void importAndAssertOnTestOpenAPI(OpenAPIImporter importer) {
      // Check that basic service properties are there.
      List<Service> services = null;
      try {
         services = importer.getServiceDefinitions();
      } catch (MockRepositoryImportException e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, services.size());
      Service service = services.get(0);

      // Check that operations and input/output have been found.
      assertEquals(2, service.getOperations().size());
      for (Operation operation : service.getOperations()) {
         if ("GET /tests".equals(operation.getName())) {
            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertNotNull(response.getContent());
                  assertFalse(response.getContent().length() == 0);
                  assertTrue(response.getContent().startsWith("["));
                  assertTrue(response.getContent().contains("\"some text\""));
                  assertTrue(response.getContent().contains("11"));
                  assertTrue(response.getContent().contains("35"));
                  assertTrue(response.getContent().endsWith("]"));
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         } else if ("GET /tests/{id}".equals(operation.getName())) {
            // Check that messages have been correctly found.
            List<Exchange> exchanges = null;
            try {
               exchanges = importer.getMessageDefinitions(service, operation);
            } catch (Exception e) {
               fail("No exception should be thrown when importing message definitions.");
            }
            assertEquals(1, exchanges.size());

            for (Exchange exchange : exchanges) {
               if (exchange instanceof RequestResponsePair) {
                  RequestResponsePair entry = (RequestResponsePair) exchange;
                  Request request = entry.getRequest();
                  Response response = entry.getResponse();
                  assertNotNull(request);
                  assertNotNull(response);
                  assertNotNull(response.getContent());
                  assertFalse(response.getContent().length() == 0);
                  assertTrue(response.getContent().startsWith("{"));
                  assertTrue(response.getContent().contains("\"foo\":"));
                  assertTrue(response.getContent().contains("\"bar\":"));
                  assertTrue(response.getContent().endsWith("}"));
               } else {
                  fail("Exchange has the wrong type. Expecting RequestResponsePair");
               }
            }
         }
      }
   }
}
