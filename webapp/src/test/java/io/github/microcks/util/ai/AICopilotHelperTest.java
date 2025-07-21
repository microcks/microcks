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
package io.github.microcks.util.ai;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.DispatchStyles;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * This is a test case for class AICopilotHelper.
 * @author laurent
 */
class AICopilotHelperTest {

   @Test
   void testParseRequestResponseOutputYaml() {
      String aiResponse = """
            - example: 1
              request:
                url: /pastries/croissant
                headers:
                  accept: application/json
                body:
              response:
                code: 200
                headers:
                  content-type: application/json
                body:
                  name: "Croissant"
                  description: "A flaky, buttery pastry"
                  size: "L"
                  price: 2.5
                  status: "available"

            - example: 2
              request:
                url: /pastries/donut
                headers:
                  accept: application/json
                body:
              response:
                code: 200
                headers:
                  content-type: application/json
                body:
                  name: "Donut"
                  description: "A delicious fried pastry"
                  size: "M"
                  price: 1.5
                  status: "available"
            """;

      Service service = new Service();
      service.setType(ServiceType.REST);
      Operation operation = new Operation();
      operation.setName("GET /pastries/{name}");
      operation.setDispatcher(DispatchStyles.URI_PARTS);
      operation.setDispatcherRules("name");

      List<RequestResponsePair> results = null;
      try {
         results = AICopilotHelper.parseRequestResponseTemplateOutput(service, operation, aiResponse);
      } catch (Exception e) {
         fail("Exception should not be thrown here");
      }

      assertNotNull(results);
      assertEquals(2, results.size());

      // Check that request has been correctly parsed.
      RequestResponsePair example1 = results.get(0);
      assertNull(example1.getRequest().getContent());
      assertEquals(1, example1.getRequest().getHeaders().size());

      // Check that response has been correctly parsed.
      assertEquals("200", example1.getResponse().getStatus());
      assertNotNull(example1.getResponse().getContent());
      assertFalse(example1.getResponse().getContent().contains("\\n"));
      assertEquals(1, example1.getResponse().getHeaders().size());
   }

   @Test
   void testParseEventMessageOutputYaml() {
      String aiResponse = """
            - example: 1
              message:
                headers:
                  my-app-header: 42
                payload:
                  id: "12345"
                  sendAt: "2022-01-01T10:00:00Z"
                  fullName: "John Doe"
                  email: "john.doe@example.com"
                  age: 25

            - example: 2
              message:
                headers:
                  my-app-header: 75
                payload:
                  id: "98765"
                  sendAt: "2022-01-02T14:30:00Z"
                  fullName: "Jane Smith"
                  email: "jane.smith@example.com"
                  age: 28
            """;

      Service service = new Service();
      service.setType(ServiceType.EVENT);
      Operation operation = new Operation();
      operation.setName("SUBSCRIBE user/signedup");

      List<UnidirectionalEvent> results = null;
      try {
         results = AICopilotHelper.parseUnidirectionalEventTemplateOutput(aiResponse);
      } catch (Exception e) {
         fail("Exception should not be thrown here");
      }

      assertNotNull(results);
      assertEquals(2, results.size());

      // Check that message 1 has been correctly parsed.
      EventMessage event1 = results.get(0).getEventMessage();
      assertEquals(1, event1.getHeaders().size());
      assertNotNull(event1.getContent());
   }

   @Test
   void testParseRequestResponseOutputYamlWithMD() {
      String aiResponse = """
            ##### Example 1:

            Request:
            ```yaml
            example: 1
            request:
              url: "/pastries?size=L"
              headers:
                accept: application/json
              body:
            response:
              code: 200
              headers:
                content-type: application/json
              body:
                - name: "Croissant"
                  description: "A buttery, flaky, viennoiserie pastry named for its well-known crescent shape"
                  size: "L"
                  price: 3.49
                  status: "available"
                - name: "Pain au Chocolat"
                  description: "A sweet, buttery pastry filled with dark chocolate"
                  size: "L"
                  price: 4.99
                  status: "available"
                - name: "Danish"
                  description: "A multilayered, laminated sweet pastry in the viennoiserie tradition"
                  size: "L"
                  price: 2.99
                  status: "available"
            ```

            ##### Example 2:

            Request:
            ```yaml
            example: 2
            request:
              url: "/pastries?size=M"
              headers:
                accept: application/json
              body:
            response:
              code: 200
              headers:
                content-type: application/json
              body:
                - name: "Eclair"
                  description: "An oblong pastry made with choux dough filled with a cream and topped with icing"
                  size: "M"
                  price: 2.99
                  status: "available"
                - name: "Macaron"
                  description: "A sweet meringue-based confection made with egg white, icing sugar, granulated sugar, almond flour, and food coloring"
                  size: "M"
                  price: 1.99
                  status: "available"
                - name: "Madeleine"
                  description: "A small sponge cake traditionally baked in scallop-shaped madeleine molds"
                  size: "M"
                  price: 2.49
                  status: "available"
            ```
            """;

      Service service = new Service();
      service.setType(ServiceType.REST);
      Operation operation = new Operation();
      operation.setName("GET /pastries");
      operation.setDispatcher(DispatchStyles.URI_PARAMS);
      operation.setDispatcherRules("size");

      List<RequestResponsePair> results = null;
      try {
         results = AICopilotHelper.parseRequestResponseTemplateOutput(service, operation, aiResponse);
      } catch (Exception e) {
         fail("Exception should not be thrown here");
      }

      assertNotNull(results);
      assertEquals(2, results.size());

      // Check that request 1 has been correctly parsed.
      RequestResponsePair example1 = results.get(0);
      assertNull(example1.getRequest().getContent());
      assertEquals(1, example1.getRequest().getHeaders().size());

      // Check that response 1 has been correctly parsed.
      assertEquals("200", example1.getResponse().getStatus());
      assertNotNull(example1.getResponse().getContent());
      assertFalse(example1.getResponse().getContent().contains("\\n"));
      assertEquals(1, example1.getResponse().getHeaders().size());
      assertEquals("?size=L", example1.getResponse().getDispatchCriteria());
   }

   @Test
   void testParseRequestResponseOutputYamlWithMD2() {
      String aiResponse = """
            ### Example 1

            ```yaml
            - example: 1
              request:
                url: /pastry/ChocolateCroissant
                headers:
                  accept: application/json
                body:
              response:
                code: 200
                headers:
                  content-type: application/json
                body:
                  name: "ChocolateCroissant"
                  description: "A croissant filled with chocolate"
                  size: "M"
                  price: 2.5
                  status: "available"
            ```

            ### Example 2

            ```yaml
            - example: 2
              request:
                url: /pastry/BlueberryMuffin
                headers:
                  accept: application/json
                body:
              response:
                code: 200
                headers:
                  content-type: application/json
                body:
                  name: "BlueberryMuffin"
                  description: "A muffin filled with fresh blueberries"
                  size: "S"
                  price: 1.5
                  status: "available"
            ```
            """;

      Service service = new Service();
      service.setType(ServiceType.REST);
      Operation operation = new Operation();
      operation.setName("GET /pastry/{name}");
      operation.setDispatcher(DispatchStyles.URI_PARTS);
      operation.setDispatcherRules("name");

      List<RequestResponsePair> results = null;
      try {
         results = AICopilotHelper.parseRequestResponseTemplateOutput(service, operation, aiResponse);
      } catch (Exception e) {
         fail("Exception should not be thrown here");
      }

      assertNotNull(results);
      assertEquals(2, results.size());

      for (RequestResponsePair pair : results) {
         assertNull(pair.getRequest().getContent());
         assertEquals(1, pair.getRequest().getHeaders().size());

         // Check that response has been correctly parsed.
         assertEquals("200", pair.getResponse().getStatus());
         assertNotNull(pair.getResponse().getContent());
         assertEquals(1, pair.getResponse().getHeaders().size());

         if ("1".equals(pair.getResponse().getName())) {
            assertEquals("/name=ChocolateCroissant", pair.getResponse().getDispatchCriteria());
         } else if ("2".equals(pair.getResponse().getName())) {
            assertEquals("/name=BlueberryMuffin", pair.getResponse().getDispatchCriteria());
         } else {
            fail("Unknown example pair name");
         }
      }
   }

   @Test
   void testParseURIElementsDispatchCriteria() {
      String aiResponse = """
            ### Example 1

            ```yaml
            - example: 1
              request:
                url: /customer/12345/accounts?filter=portfolio
                headers:
                  accept: application/json
                parameters:
                  customerId: 12345
                  filter: portfolio
                body:
              response:
                code: 200
                headers:
                  content-type: application/json
                body:
                  id: 12345-portfolio
                  description: "Portfolio account"
            ```

            ### Example 2

            ```yaml
            - example: 2
              request:
                url: /customer/67890/accounts
                headers:
                  accept: application/json
                parameters:
                  filter: standard
                body:
              response:
                code: 200
                headers:
                  content-type: application/json
                body:
                  id: 67890-standard
                  description: "Standard account"
            ```
            """;

      Service service = new Service();
      service.setType(ServiceType.REST);
      Operation operation = new Operation();
      operation.setName("GET /customer/{customerId}/accounts");
      operation.setDispatcher(DispatchStyles.URI_ELEMENTS);
      operation.setDispatcherRules("customerId ?? filter");

      List<RequestResponsePair> results = null;
      try {
         results = AICopilotHelper.parseRequestResponseTemplateOutput(service, operation, aiResponse);
      } catch (Exception e) {
         fail("Exception should not be thrown here");
      }

      assertNotNull(results);
      assertEquals(2, results.size());

      for (RequestResponsePair pair : results) {
         if ("1".equals(pair.getResponse().getName())) {
            assertEquals("/customerId=12345?filter=portfolio", pair.getResponse().getDispatchCriteria());
         } else if ("2".equals(pair.getResponse().getName())) {
            assertEquals("/customerId=67890?filter=standard", pair.getResponse().getDispatchCriteria());
         } else {
            fail("Unknown example pair name");
         }
      }
   }

   @Test
   void testParseRequestResponseOutputSplitWithJson() {
      String aiResponse = """
            Example 1:

            - example: 1
              request:
                url: https://example.com/graphql
                headers:
                  accept: application/json
                body: |
                  {
                    "query": "query { film(id: \\"123\\") { id, title, episodeID, director, starCount, rating } }"
                  }
              response:
                code: 200
                headers:
                  content-type: application/json
                body: |
                  {
                    "data": {
                      "film": {
                        "id": "123",
                        "title": "Star Wars: Episode IV - A New Hope",
                        "episodeID": 4,
                        "director": "George Lucas",
                        "starCount": 5000,
                        "rating": 8.7
                      }
                    }
                  }

            Example 2:

            - example: 2
              request:
                url: https://example.com/graphql
                headers:
                  accept: application/json
                body: |
                  {
                    "query": "query($id: String) { film(id: $id) { id, title, episodeID, director, starCount, rating } }",
                    "variables": {
                      "id": "456"
                    }
                  }
              response:
                code: 200
                headers:
                  content-type: application/json
                body: |
                  {
                    "data": {
                      "film": {
                        "id": "456",
                        "title": "The Godfather",
                        "episodeID": 1,
                        "director": "Francis Ford Coppola",
                        "starCount": 4500,
                        "rating": 9.2
                      }
                    }
                  }
            """;

      Service service = new Service();
      service.setType(ServiceType.GRAPHQL);
      Operation operation = new Operation();
      operation.setName("film");
      operation.setDispatcher(DispatchStyles.QUERY_ARGS);
      operation.setDispatcherRules("id");
      List<RequestResponsePair> results = null;
      try {
         results = AICopilotHelper.parseRequestResponseTemplateOutput(service, operation, aiResponse);
      } catch (Exception e) {
         fail("Exception should not be thrown here");
      }

      assertNotNull(results);
      assertEquals(2, results.size());

      // Check that request 1 has been correctly parsed.
      RequestResponsePair example1 = results.get(0);
      assertNotNull(example1.getRequest().getContent());
      assertFalse(example1.getRequest().getContent().contains("\\n"));
      assertEquals(1, example1.getRequest().getHeaders().size());

      // Check that response 1 has been correctly parsed.
      assertEquals("200", example1.getResponse().getStatus());
      assertNotNull(example1.getResponse().getContent());
      assertFalse(example1.getResponse().getContent().contains("\\n"));
      assertEquals(1, example1.getResponse().getHeaders().size());

      // Check that request 2 has been correctly parsed.
      RequestResponsePair example2 = results.get(1);
      assertNotNull(example2.getRequest().getContent());
      assertFalse(example2.getRequest().getContent().contains("\\n"));
      assertEquals(1, example2.getRequest().getHeaders().size());

      // Check that response 2 has been correctly parsed.
      assertEquals("200", example1.getResponse().getStatus());
      assertNotNull(example2.getResponse().getContent());
      assertFalse(example2.getResponse().getContent().contains("\\n"));
      assertEquals(1, example2.getResponse().getHeaders().size());
      assertEquals("?id=456", example2.getResponse().getDispatchCriteria());
   }

   @Test
   void testParseGrpcRequestResponseOutputYamlWithMD() {
      String aiResponse = """
            ```yaml
            - example: 1
              request:
                body: {"firstname": "John", "lastname": "Doe"}
              response:
                body: {"greeting": "Hello, John Doe!"}

            - example: 2
              request:
                body: {"firstname": "Jane", "lastname": "Smith"}
              response:
                body: {"greeting": "Hello, Jane Smith!"}
            ```
            """;

      Service service = new Service();
      service.setType(ServiceType.GRPC);
      Operation operation = new Operation();
      operation.setName("greeting");
      operation.setDispatcher(DispatchStyles.JSON_BODY);
      operation.setDispatcherRules("");
      List<RequestResponsePair> results = null;
      try {
         results = AICopilotHelper.parseRequestResponseTemplateOutput(service, operation, aiResponse);
      } catch (Exception e) {
         fail("Exception should not be thrown here");
      }

      assertNotNull(results);
      assertEquals(2, results.size());

      // Check that request 1 has been correctly parsed.
      RequestResponsePair example1 = results.get(0);
      assertNotNull(example1.getRequest().getContent());
      assertFalse(example1.getRequest().getContent().contains("\\n"));
      assertTrue(example1.getRequest().getContent().contains("John"));

      // Check that response 1 has been correctly parsed.
      assertNotNull(example1.getResponse().getContent());
      assertFalse(example1.getResponse().getContent().contains("\\n"));
      assertTrue(example1.getResponse().getContent().contains("Hello, John Doe!"));
   }

   @Test
   void testRemoveTokensInNode() {
      // Create an ObjectMapper and a test JsonNode
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode specNode = mapper.createObjectNode();
      specNode.put("info", "Information");
      specNode.put("openapi", "3.0.0");
      specNode.put("paths", "/path");
      specNode.put("tags", "Tags");
      specNode.put("components", "Components");
      specNode.put("servers", "Servers");

      // Define fields to keep
      List<String> keysToKeep = List.of("info", "openapi", "paths");

      // Call method removeTagsInNode
      AICopilotHelper.removeTokensInNode(specNode, keysToKeep);

      // Ensure only desired fields
      assertFalse(specNode.has("tags"));
      assertFalse(specNode.has("components"));
      assertFalse(specNode.has("servers"));
      assertTrue(specNode.has("info"));
      assertTrue(specNode.has("openapi"));
      assertTrue(specNode.has("paths"));
   }

   @Test
   void testGetTokenNames() {
      // Create an ObjectMapper and a test JsonNode
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode specNode = mapper.createObjectNode();
      specNode.put("info", "Information");
      specNode.put("openapi", "3.0.0");
      specNode.put("paths", "/path");

      // Call the getFieldNames method
      List<String> fieldNames = AICopilotHelper.getTokenNames(specNode);

      // Verify that the field names are retrieved correctly
      List<String> expectedFieldNames = List.of("info", "openapi", "paths");
      assertEquals(expectedFieldNames, fieldNames);
   }

   @Test
   void testRemoveSecurityTokenInNode() throws Exception {
      String jsonPathSpec = "{\"get\":{\"parameters\":[{\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}},\"text/xml\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}},\"description\":\"Pastry with specified name\"}},\"operationId\":\"GetPastryByName\",\"summary\":\"Get Pastry by name\",\"description\":\"Get Pastry by name\",\"security\":[{\"clientSecret\":[],\"clientId\":[]}]}}";
      // Create a test JsonNode with a security tag in the verb node
      ObjectMapper mapper = new ObjectMapper();
      JsonNode pathSpec = mapper.readTree(jsonPathSpec);

      // Call the removeSecurityTagInVerbNode method
      AICopilotHelper.removeSecurityTokenInNode(pathSpec, "get");

      // Verify that the security tag is removed from the verb node
      assertFalse(pathSpec.get("get").has("security"));

      // Call the removeSecurityTagInVerbNode method
      AICopilotHelper.removeSecurityTokenInNode(pathSpec, "get");

      // Verify that the method does not throw an exception when the security tag is
      // absent
      // and the verb node remains unchanged
      assertFalse(pathSpec.get("get").has("security"));
   }

   @Test
   void testFilterOpenAPISpec() throws Exception {
      // Create a test JsonNode with a security tag in the verb node
      String jsonSpecNode = "{\"openapi\":\"3.0.2\",\"info\":{\"title\":\"API Pastry - 2.0\",\"version\":\"2.0.0\",\"description\":\"API definition of API Pastry sample app\",\"contact\":{\"name\":\"Laurent Broudoux\",\"url\":\"http://github.com/lbroudoux\",\"email\":\"laurent.broudoux@gmail.com\"},\"license\":{\"name\":\"MIT License\",\"url\":\"https://opensource.org/licenses/MIT\"}},\"paths\":{\"/pastry\":{\"summary\":\"Global operations on pastries\",\"get\":{\"tags\":[\"pastry\"],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"array\",\"items\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}}},\"description\":\"Get list of pastries\"}},\"operationId\":\"GetPastries\",\"summary\":\"Get list of pastries\"}},\"/pastry/{name}\":{\"summary\":\"Specific operation on pastry\",\"get\":{\"parameters\":[{\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}},\"text/xml\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}},\"description\":\"Pastry with specified name\"}},\"operationId\":\"GetPastryByName\",\"summary\":\"Get Pastry by name\",\"description\":\"Get Pastry by name\",\"security\":[{\"clientSecret\":[],\"clientId\":[]}]},\"patch\":{\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}},\"text/xml\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}},\"required\":true},\"parameters\":[{\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}},\"text/xml\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}},\"description\":\"Changed pastry\"}},\"operationId\":\"PatchPastry\",\"summary\":\"Patch existing pastry\"},\"parameters\":[{\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}]}},\"components\":{\"schemas\":{\"Pastry\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}},\"securitySchemes\":{\"clientId\":{\"type\":\"apiKey\",\"name\":\"Client-Id\",\"in\":\"header\"},\"clientSecret\":{\"type\":\"apiKey\",\"name\":\"Client-Secret\",\"in\":\"header\"}}},\"tags\":[{\"name\":\"pastry\",\"description\":\"Pastry resource\"}]}";

      // Create a ObjectMapper
      ObjectMapper mapper = new ObjectMapper();
      // Create a test JsonNode with references and tags
      JsonNode specNode = mapper.readTree(jsonSpecNode);

      // Call the filterOpenAPISpec methods.
      AICopilotHelper.filterOpenAPISpecOnOperation(specNode, "GET /pastry/{name}");
      AICopilotHelper.filterOpenAPISpec(specNode);

      // Verify that the keys not to keep are removed correctly
      assertFalse(specNode.has("tags"));

      assertTrue(specNode.has("components"));
      assertTrue(specNode.has("paths"));
      assertTrue(specNode.has("openapi"));
      assertTrue(specNode.has("info"));

      assertTrue(specNode.get("paths").has("/pastry/{name}"));
      assertFalse(specNode.get("paths").has("/pastry"));
      // Ensure security property is removed
      assertFalse(specNode.get("paths").get("/pastry/{name}").has("security"));
   }

   @Test
   void testFilterAsyncAPISpec() throws Exception {
      // Create a test JsonNode with a security tag in the verb node
      String jsonSpecNode = "{\"asyncapi\":\"2.0.0\",\"id\":\"urn:io.microcks.example.user-signedup\",\"info\":{\"title\":\"User signed-up API\",\"version\":\"0.1.1\",\"description\":\"Sample AsyncAPI for user signedup events\"},\"defaultContentType\":\"application/json\",\"channels\":{\"user/signedup\":{\"description\":\"The topic on which user signed up events may be consumed\",\"subscribe\":{\"summary\":\"Receive informations about user signed up\",\"operationId\":\"receivedUserSignedUp\",\"message\":{\"description\":\"An event describing that a user just signed up.\",\"traits\":[{\"headers\":{\"type\":\"object\",\"properties\":{\"my-app-header\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":100}}}}],\"payload\":{\"type\":\"object\",\"additionalProperties\":false,\"properties\":{\"id\":{\"type\":\"string\"},\"sendAt\":{\"type\":\"string\"},\"fullName\":{\"type\":\"string\"},\"email\":{\"type\":\"string\",\"format\":\"email\"},\"age\":{\"type\":\"integer\",\"minimum\":18}}}}}}},\"components\":{\"messageTraits\":{\"commonHeaders\":{\"headers\":{\"type\":\"object\",\"properties\":{\"my-app-header\":{\"type\":\"integer\",\"minimum\":0,\"maximum\":100}}}}}}}";

      // Create a ObjectMapper
      ObjectMapper mapper = new ObjectMapper();
      // Create a test JsonNode with references and tags
      JsonNode specNode = mapper.readTree(jsonSpecNode);

      // Call the reduceSpecSize method
      AICopilotHelper.filterAsyncAPISpec(specNode);

      // Verify that the keys not to keep are removed correctly
      assertFalse(specNode.has("id"));

      assertTrue(specNode.has("components"));
      assertTrue(specNode.has("channels"));
      assertTrue(specNode.has("asyncapi"));
      assertTrue(specNode.has("info"));

      assertTrue(specNode.get("channels").has("user/signedup"));
   }

   @Test
   void testResolveReferenceAndRemoveTokensInNode() throws Exception {

      String jsonSpecNode = "{\"openapi\":\"3.0.2\",\"info\":{\"title\":\"API Pastry - 2.0\",\"version\":\"2.0.0\",\"description\":\"API definition of API Pastry sample app\",\"contact\":{\"name\":\"Laurent Broudoux\",\"url\":\"http://github.com/lbroudoux\",\"email\":\"laurent.broudoux@gmail.com\"},\"license\":{\"name\":\"MIT License\",\"url\":\"https://opensource.org/licenses/MIT\"}},\"paths\":{\"/pastry\":{\"summary\":\"Global operations on pastries\",\"get\":{\"tags\":[\"pastry\"],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"array\",\"items\":{\"$ref\":\"#/components/schemas/Pastry\"}},\"examples\":{\"pastries_json\":{\"value\":[{\"name\":\"Baba Rhum\",\"description\":\"Delicieux Baba au Rhum pas calorique du tout\",\"size\":\"L\",\"price\":3.2,\"status\":\"available\"},{\"name\":\"Divorces\",\"description\":\"Delicieux Divorces pas calorique du tout\",\"size\":\"M\",\"price\":2.8,\"status\":\"available\"},{\"name\":\"Tartelette Fraise\",\"description\":\"Delicieuse Tartelette aux Fraises fraiches\",\"size\":\"S\",\"price\":2,\"status\":\"available\"}]}}}},\"description\":\"Get list of pastries\"}},\"operationId\":\"GetPastries\",\"summary\":\"Get list of pastries\"}},\"/pastry/{name}\":{\"summary\":\"Specific operation on pastry\",\"get\":{\"parameters\":[{\"examples\":{\"Eclair Cafe\":{\"value\":\"Eclair Cafe\"},\"Eclair Cafe Xml\":{\"value\":\"Eclair Cafe\"},\"Millefeuille\":{\"value\":\"Millefeuille\"}},\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"$ref\":\"#/components/schemas/Pastry\"},\"examples\":{\"Eclair Cafe\":{\"value\":{\"name\":\"Eclair Cafe\",\"description\":\"Delicieux Eclair au Cafe pas calorique du tout\",\"size\":\"M\",\"price\":2.5,\"status\":\"available\"}},\"Millefeuille\":{\"value\":{\"name\":\"Millefeuille\",\"description\":\"Delicieux Millefeuille pas calorique du tout\",\"size\":\"L\",\"price\":4.4,\"status\":\"available\"}}}},\"text/xml\":{\"schema\":{\"$ref\":\"#/components/schemas/Pastry\"},\"examples\":{\"Eclair Cafe Xml\":{\"value\":\"<pastry>\\n"
            + //
            "    <name>Eclair Cafe</name>\\n" + //
            "    <description>Delicieux Eclair au Cafe pas calorique du tout</description>\\n" + //
            "    <size>M</size>\\n" + //
            "    <price>2.5</price>\\n" + //
            "    <status>available</status>\\n" + //
            "</pastry>\"}}}},\"description\":\"Pastry with specified name\"}},\"operationId\":\"GetPastryByName\",\"summary\":\"Get Pastry by name\",\"description\":\"Get Pastry by name\",\"security\":[{\"clientSecret\":[],\"clientId\":[]}]},\"patch\":{\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"$ref\":\"#/components/schemas/Pastry\"},\"examples\":{\"Eclair Cafe\":{\"value\":{\"price\":2.6}}}},\"text/xml\":{\"schema\":{\"$ref\":\"#/components/schemas/Pastry\"},\"examples\":{\"Eclair Cafe Xml\":{\"value\":\"<pastry>\\n"
            + //
            "\\t<price>2.6</price>\\n" + //
            "</pastry>\"}}}},\"required\":true},\"parameters\":[{\"examples\":{\"Eclair Cafe\":{\"value\":\"Eclair Cafe\"},\"Eclair Cafe Xml\":{\"value\":\"Eclair Cafe\"}},\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"$ref\":\"#/components/schemas/Pastry\"},\"examples\":{\"Eclair Cafe\":{\"value\":{\"name\":\"Eclair Cafe\",\"description\":\"Delicieux Eclair au Cafe pas calorique du tout\",\"size\":\"M\",\"price\":2.6,\"status\":\"available\"}}}},\"text/xml\":{\"schema\":{\"$ref\":\"#/components/schemas/Pastry\"},\"examples\":{\"Eclair Cafe Xml\":{\"value\":\"<pastry>\\n"
            + //
            "    <name>Eclair Cafe</name>\\n" + //
            "    <description>Delicieux Eclair au Cafe pas calorique du tout</description>\\n" + //
            "    <size>M</size>\\n" + //
            "    <price>2.6</price>\\n" + //
            "    <status>available</status>\\n" + //
            "</pastry>\"}}}},\"description\":\"Changed pastry\"}},\"operationId\":\"PatchPastry\",\"summary\":\"Patch existing pastry\"},\"parameters\":[{\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}]}},\"components\":{\"schemas\":{\"Pastry\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}},\"example\":{\"name\":\"My Pastry\",\"description\":\"A short description os my pastry\",\"size\":\"M\",\"price\":4.5,\"status\":\"available\"}}},\"securitySchemes\":{\"clientId\":{\"type\":\"apiKey\",\"name\":\"Client-Id\",\"in\":\"header\"},\"clientSecret\":{\"type\":\"apiKey\",\"name\":\"Client-Secret\",\"in\":\"header\"}}},\"tags\":[{\"name\":\"pastry\",\"description\":\"Pastry resource\"}]}";

      String jsonExpectedSpecNodeResult = "{\"openapi\":\"3.0.2\",\"info\":{\"title\":\"API Pastry - 2.0\",\"version\":\"2.0.0\",\"description\":\"API definition of API Pastry sample app\",\"contact\":{\"name\":\"Laurent Broudoux\",\"url\":\"http://github.com/lbroudoux\",\"email\":\"laurent.broudoux@gmail.com\"},\"license\":{\"name\":\"MIT License\",\"url\":\"https://opensource.org/licenses/MIT\"}},\"paths\":{\"/pastry\":{\"summary\":\"Global operations on pastries\",\"get\":{\"tags\":[\"pastry\"],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"type\":\"array\",\"items\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}}},\"description\":\"Get list of pastries\"}},\"operationId\":\"GetPastries\",\"summary\":\"Get list of pastries\"}},\"/pastry/{name}\":{\"summary\":\"Specific operation on pastry\",\"get\":{\"parameters\":[{\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}},\"text/xml\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}},\"description\":\"Pastry with specified name\"}},\"operationId\":\"GetPastryByName\",\"summary\":\"Get Pastry by name\",\"description\":\"Get Pastry by name\",\"security\":[{\"clientSecret\":[],\"clientId\":[]}]},\"patch\":{\"requestBody\":{\"content\":{\"application/json\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}},\"text/xml\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}},\"required\":true},\"parameters\":[{\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}],\"responses\":{\"200\":{\"content\":{\"application/json\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}},\"text/xml\":{\"schema\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}}}}},\"description\":\"Changed pastry\"}},\"operationId\":\"PatchPastry\",\"summary\":\"Patch existing pastry\"},\"parameters\":[{\"name\":\"name\",\"description\":\"pastry name\",\"schema\":{\"type\":\"string\"},\"in\":\"path\",\"required\":true}]}},\"components\":{\"schemas\":{\"Pastry\":{\"title\":\"Root Type for Pastry\",\"description\":\"The root of the Pastry type's schema.\",\"type\":\"object\",\"properties\":{\"name\":{\"description\":\"Name of this pastry\",\"type\":\"string\"},\"description\":{\"description\":\"A short description of this pastry\",\"type\":\"string\"},\"size\":{\"description\":\"Size of pastry (S, M, L)\",\"type\":\"string\"},\"price\":{\"format\":\"double\",\"description\":\"Price (in USD) of this pastry\",\"type\":\"number\"},\"status\":{\"description\":\"Status in stock (available, out_of_stock)\",\"type\":\"string\"}},\"example\":{\"name\":\"My Pastry\",\"description\":\"A short description os my pastry\",\"size\":\"M\",\"price\":4.5,\"status\":\"available\"}}},\"securitySchemes\":{\"clientId\":{\"type\":\"apiKey\",\"name\":\"Client-Id\",\"in\":\"header\"},\"clientSecret\":{\"type\":\"apiKey\",\"name\":\"Client-Secret\",\"in\":\"header\"}}},\"tags\":[{\"name\":\"pastry\",\"description\":\"Pastry resource\"}]}";

      // Create a ObjectMapper
      ObjectMapper mapper = new ObjectMapper();
      // Create a test JsonNode with references and tags
      JsonNode specNode = mapper.readTree(jsonSpecNode);

      // Call the resolveReferenceAndRemoveTagsInNode method
      AICopilotHelper.resolveReferenceAndRemoveTokensInNode(specNode, specNode, new HashMap<>(), new HashSet<>());

      // Verify that the references and tags are removed correctly
      assertEquals(jsonExpectedSpecNodeResult, specNode.toString());
   }

   @Test
   void testRemoveTokensFromSpec() throws Exception {
      // Create a sample specification
      String specification = """
            openapi: 3.0.2
            info:
              title: API Pastry - 2.0
              version: 2.0.0
              description: API definition of API Pastry sample app
              contact:
                name: Laurent Broudoux
                url: http://github.com/lbroudoux
                email: laurent.broudoux@gmail.com
              license:
                name: MIT License
                url: https://opensource.org/licenses/MIT
            paths:
              /pastry:
                summary: Global operations on pastries
                get:
                  tags:
                    - pastry
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            type: array
                            items:
                              $ref: '#/components/schemas/Pastry'
                          examples:
                            pastries_json:
                              value:
                                - name: Baba Rhum
                                  description: Delicieux Baba au Rhum pas calorique du tout
                                  size: L
                                  price: 3.2
                                  status: available
                                - name: Divorces
                                  description: Delicieux Divorces pas calorique du tout
                                  size: M
                                  price: 2.8
                                  status: available
                                - name: Tartelette Fraise
                                  description: Delicieuse Tartelette aux Fraises fraiches
                                  size: S
                                  price: 2
                                  status: available
                      description: Get list of pastries
                  operationId: GetPastries
                  summary: Get list of pastries
              /pastry/{name}:
                summary: Specific operation on pastry
                get:
                  parameters:
                    - examples:
                        Eclair Cafe:
                          value: Eclair Cafe
                        Eclair Cafe Xml:
                          value: Eclair Cafe
                        Millefeuille:
                          value: Millefeuille
                      name: name
                      description: pastry name
                      schema:
                        type: string
                      in: path
                      required: true
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Pastry'
                          examples:
                            Eclair Cafe:
                              value:
                                name: Eclair Cafe
                                description: Delicieux Eclair au Cafe pas calorique du tout
                                size: M
                                price: 2.5
                                status: available
                            Millefeuille:
                              value:
                                name: Millefeuille
                                description: Delicieux Millefeuille pas calorique du tout
                                size: L
                                price: 4.4
                                status: available
                        text/xml:
                          schema:
                            $ref: '#/components/schemas/Pastry'
                          examples:
                            Eclair Cafe Xml:
                              value: |-
                                <pastry>
                                    <name>Eclair Cafe</name>
                                    <description>Delicieux Eclair au Cafe pas calorique du tout</description>
                                    <size>M</size>
                                    <price>2.5</price>
                                    <status>available</status>
                                </pastry>
                      description: Pastry with specified name
                  operationId: GetPastryByName
                  summary: Get Pastry by name
                  description: Get Pastry by name
                  security:
                    - clientSecret: []
                      clientId: []
                patch:
                  requestBody:
                    content:
                      application/json:
                        schema:
                          $ref: '#/components/schemas/Pastry'
                        examples:
                          Eclair Cafe:
                            value:
                              price: 2.6
                      text/xml:
                        schema:
                          $ref: '#/components/schemas/Pastry'
                        examples:
                          Eclair Cafe Xml:
                            value: "<pastry>\n\t<price>2.6</price>\n</pastry>"
                    required: true
                  parameters:
                    - examples:
                        Eclair Cafe:
                          value: Eclair Cafe
                        Eclair Cafe Xml:
                          value: Eclair Cafe
                      name: name
                      description: pastry name
                      schema:
                        type: string
                      in: path
                      required: true
                  responses:
                    '200':
                      content:
                        application/json:
                          schema:
                            $ref: '#/components/schemas/Pastry'
                          examples:
                            Eclair Cafe:
                              value:
                                name: Eclair Cafe
                                description: Delicieux Eclair au Cafe pas calorique du tout
                                size: M
                                price: 2.6
                                status: available
                        text/xml:
                          schema:
                            $ref: '#/components/schemas/Pastry'
                          examples:
                            Eclair Cafe Xml:
                              value: |-
                                <pastry>
                                    <name>Eclair Cafe</name>
                                    <description>Delicieux Eclair au Cafe pas calorique du tout</description>
                                    <size>M</size>
                                    <price>2.6</price>
                                    <status>available</status>
                                </pastry>
                      description: Changed pastry
                  operationId: PatchPastry
                  summary: Patch existing pastry
                parameters:
                  - name: name
                    description: pastry name
                    schema:
                      type: string
                    in: path
                    required: true
            components:
              schemas:
                Pastry:
                  title: Root Type for Pastry
                  description: The root of the Pastry type's schema.
                  type: object
                  properties:
                    name:
                      description: Name of this pastry
                      type: string
                    description:
                      description: A short description of this pastry
                      type: string
                    size:
                      description: Size of pastry (S, M, L)
                      type: string
                    price:
                      format: double
                      description: Price (in USD) of this pastry
                      type: number
                    status:
                      description: Status in stock (available, out_of_stock)
                      type: string
                  example:
                    name: My Pastry
                    description: A short description os my pastry
                    size: M
                    price: 4.5
                    status: available
              securitySchemes:
                clientId:
                  type: apiKey
                  name: Client-Id
                  in: header
                clientSecret:
                  type: apiKey
                  name: Client-Secret
                  in: header
            tags:
              - name: pastry
                description: Pastry resource
              """;
      ;

      String operationName = "GET /pastry/{name}";

      // Call the removeTagsFromOpenAPISpec method
      String result = AICopilotHelper.removeTokensFromSpec(specification, operationName);

      // Expected result after removing tags
      String expectedResult = """
            ---
            openapi: "3.0.2"
            info:
              title: "API Pastry - 2.0"
              version: "2.0.0"
              description: "API definition of API Pastry sample app"
              contact:
                name: "Laurent Broudoux"
                url: "http://github.com/lbroudoux"
                email: "laurent.broudoux@gmail.com"
              license:
                name: "MIT License"
                url: "https://opensource.org/licenses/MIT"
            paths:
              /pastry/{name}:
                get:
                  parameters:
                  - name: "name"
                    description: "pastry name"
                    schema:
                      type: "string"
                    in: "path"
                    required: true
                  responses:
                    "200":
                      content:
                        application/json:
                          schema:
                            title: "Root Type for Pastry"
                            description: "The root of the Pastry type's schema."
                            type: "object"
                            properties:
                              name:
                                description: "Name of this pastry"
                                type: "string"
                              description:
                                description: "A short description of this pastry"
                                type: "string"
                              size:
                                description: "Size of pastry (S, M, L)"
                                type: "string"
                              price:
                                format: "double"
                                description: "Price (in USD) of this pastry"
                                type: "number"
                              status:
                                description: "Status in stock (available, out_of_stock)"
                                type: "string"
                        text/xml:
                          schema:
                            title: "Root Type for Pastry"
                            description: "The root of the Pastry type's schema."
                            type: "object"
                            properties:
                              name:
                                description: "Name of this pastry"
                                type: "string"
                              description:
                                description: "A short description of this pastry"
                                type: "string"
                              size:
                                description: "Size of pastry (S, M, L)"
                                type: "string"
                              price:
                                format: "double"
                                description: "Price (in USD) of this pastry"
                                type: "number"
                              status:
                                description: "Status in stock (available, out_of_stock)"
                                type: "string"
                      description: "Pastry with specified name"
                  operationId: "GetPastryByName"
                  summary: "Get Pastry by name"
                  description: "Get Pastry by name"
              """;

      // Verify the result
      assertEquals(expectedResult, result);
   }

   @Test
   void testRemoveTokensFromSpecWithCircularDependencies() throws Exception {
      String specification = FileUtils.readFileToString(
            new File("target/test-classes/io/github/microcks/util/ai/openwealth-custodyServicesAPI.yaml"),
            StandardCharsets.UTF_8);

      String operationName = "GET /accounts/{accountId}/positions";
      // Call the removeTokensFromSpec method
      String result = AICopilotHelper.removeTokensFromSpec(specification, operationName);

      //System.err.println("Result: " + result);
      assertNotNull(result);
   }

   @Test
   void testRemoveTokensFromLightSpecWithCircularDependencies() throws Exception {
      String specification = FileUtils.readFileToString(
            new File("target/test-classes/io/github/microcks/util/ai/stripe-light.yaml"), StandardCharsets.UTF_8);

      String operationName = "GET /v1/products";
      // Call the removeTokensFromSpec method
      String result = AICopilotHelper.removeTokensFromSpec(specification, operationName);

      //System.err.println("Result: " + result);
      assertNotNull(result);
   }

   @Test
   void testRemoveTokensFromHugeSpecWithCircularDependencies() throws Exception {
      String specification = FileUtils.readFileToString(
            new File("target/test-classes/io/github/microcks/util/ai/stripe-spec3.yaml"), StandardCharsets.UTF_8);

      String operationName = "GET /v1/products";
      // Call the removeTokensFromSpec method
      String result = AICopilotHelper.removeTokensFromSpec(specification, operationName);

      // Fallback resolution with global resolution must have been called... and failed because of TextBuffer overflow
      // Then, it should default to the complete specification....
      // At least, it doesn't fail!
      //System.err.println("Result: " + result);
      assertNotNull(result);
   }
}
