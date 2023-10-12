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

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * This is a test case for class AICopilotHelper.
 * @author laurent
 */
public class AICopilotHelperTest {

   @Test
   public void testParseRequestResponseOutputYaml() {
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
   public void testParseEventMessageOutputYaml() {
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
   public void testParseRequestResponseOutputYamlWithMD() {
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
   public void testParseRequestResponseOutputYamlWithMD2() {
      String aiResponse = """
            ### Example 1
                        
            ```yaml
            - example: 1
              request:
                url: /pastry/ChocolateCroissant
                headers:
                  accept: application/json
                body:\s
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

   }
   @Test
   public void testParseRequestResponseOutputSplitWithJson() {
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
         e.printStackTrace();
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
}
