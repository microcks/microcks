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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-extensions.yaml", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-extensions.json", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-quoted.yaml", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-with-json.yaml", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/locations-openapi.json", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-spacesops.yaml", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-headers.yaml", null);
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
         byte[] bytes = Files.readAllBytes(Paths.get("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml"));
         JsonNode openapiSpec = mapper.readTree(bytes);

         String verb = "get";
         String path = "/owner/{owner}/car";

         String pointer = "/paths/" + path.replace("/", "~1") + "/" + verb
               + "/responses/200/content/" + "application/json".replace("/", "~1");

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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-complete.yaml", null);
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
                  RequestResponsePair entry = (RequestResponsePair)exchange;
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
         }
         else if ("POST /owner/{owner}/car".equals(operation.getName())) {
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
                  RequestResponsePair entry = (RequestResponsePair)exchange;
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
         }
         else if ("GET /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
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
                  RequestResponsePair entry = (RequestResponsePair)exchange;
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
         }
         else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-3.1-complete.yaml", null);
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
                  RequestResponsePair entry = (RequestResponsePair)exchange;
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
         }
         else if ("POST /owner/{owner}/car".equals(operation.getName())) {
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
                  RequestResponsePair entry = (RequestResponsePair)exchange;
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
         }
         else if ("GET /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
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
                  RequestResponsePair entry = (RequestResponsePair)exchange;
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
         }
         else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-uncomplete-params.yaml", null);
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
         }
         else if ("POST /owner/{owner}/car".equals(operation.getName())) {
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
         }
         else if ("GET /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/test-openapi-yaml.yaml", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/test-openapi-json.json", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/cars-openapi-complex-refs.yaml", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/param-refs-openapi.yaml", null);
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
                     assertEquals("{\"account\":{\"resourceId\":\"f377afb3-5c62-40cc-8f07-1f4749a780eb\"}}", response.getContent());
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/query-param-refs-openapi.yaml", null);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/examples-ref-openapi.yaml", null);
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
   public void testExternalValueExampleRessourceImport(){
    OpenAPIImporter importer = null;
    try {
       importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/external-value-openapi.yaml", new ReferenceResolver("https://raw.githubusercontent.com/microcks/microcks/1.5.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-openapi.yaml", null, true));
    } catch (IOException ioe) {
       ioe.printStackTrace();
       fail("Exception should not be thrown");
    }

    // Check that basic service properties are there.
    List<Resource> resources = null;
    List<Service> services = null;
    List<Exchange> exchanges = null;
    try {
       services = importer.getServiceDefinitions();
       resources = importer.getResourceDefinitions( services.get(0));
       exchanges = importer.getMessageDefinitions(services.get(0), services.get(0).getOperations().get(0));
    } catch (MockRepositoryImportException e) {
       fail("Exception should not be thrown");
       return;
    }
    assertEquals(2, resources.size());
    assertEquals(1, exchanges.size());

    var exchange = (RequestResponsePair) exchanges.get(0);
    assertEquals("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"
    , exchange.getResponse().getContent());
  }

   @Test
   public void testExternalRelativeReferenceOpenAPIImport() {
      OpenAPIImporter importer = null;
      ReferenceResolver resolver = new ReferenceResolver(
            "https://raw.githubusercontent.com/microcks/microcks/1.5.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref.yaml",
            null, true);
      try {
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref.yaml", resolver);
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref-example.yaml", resolver);
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

      for (int i=1; i<3; i++) {
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-absolute-ref.yaml", resolver);
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
      assertEquals("https://raw.githubusercontent.com/microcks/microcks/1.5.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-schema.yaml", refSchema.getPath());
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-absolute-ref-pointers.yaml", resolver);
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

      for (int i=1; i<3; i++) {
         Resource refResource = resources.get(i);
         if ("WeatherForecast API-1.0.0-weather-forecast-common.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_FRAGMENT, refResource.getType());
            assertEquals("https://raw.githubusercontent.com/microcks/microcks/1.8.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-common.yaml", refResource.getPath());
            assertNotNull(refResource.getContent());
            assertTrue(refResource.getContent().contains("title: Common objects to reuse"));
         } else if ("WeatherForecast API-1.0.0-weather-forecast-schema.yaml".equals(refResource.getName())) {
            assertEquals(ResourceType.JSON_SCHEMA, refResource.getType());
            assertEquals("https://raw.githubusercontent.com/microcks/microcks/1.8.x/webapp/src/test/resources/io/github/microcks/util/openapi/weather-forecast-schema.yaml", refResource.getPath());
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
         importer = new OpenAPIImporter("target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-recursive-ref.yaml", resolver);
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

      for (int i=1; i<4; i++) {
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
            assertTrue(refResource.getContent().contains("$ref: 'WeatherForecast+API-1.0.0--weather-forecast-common-regions.yaml#/regions/north'"));
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
         }
         else if ("POST /owner/{owner}/car".equals(operation.getName())) {
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
         }
         else if ("POST /owner/{owner}/car/{car}/passenger".equals(operation.getName())) {
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
               .filter(operation -> operation.getName().equals("POST /owner/{owner}/car"))
               .findFirst().get();

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
