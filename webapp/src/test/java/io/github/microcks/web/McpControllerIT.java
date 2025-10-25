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
package io.github.microcks.web;

import io.github.microcks.util.ai.McpSchema;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for the MCP controller.
 * @author laurent
 */
class McpControllerIT extends AbstractBaseIT {

   record McpSSEFrame(String key, String value) {
   }

   private final ObjectMapper mapper = new ObjectMapper();

   private List<McpSSEFrame> sseFrames;
   private ExecutorService sseClientExecutor;

   @BeforeEach
   void setUp() {
      sseFrames = new ArrayList<>();
      sseClientExecutor = Executors.newSingleThreadExecutor();
   }

   @AfterEach
   void tearDown() {
      sseClientExecutor.shutdown();
   }

   @Test
   void testOpenAPIHttpSSEEndpoint() throws Exception {
      // Update Petstore from the tutorial reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-1.0.0-openapi.yaml", true);

      // Define the runnable for the SSE client.
      Runnable sseClientRunnable = () -> {
         try {
            restTemplate.execute("/mcp/Petstore+API/1.0.0/sse", HttpMethod.GET, request -> {}, response -> {
               String line;
               try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody()));) {
                  while ((line = bufferedReader.readLine()) != null) {
                     parseAndStoreMcpSseFrame(line, sseFrames);
                  }
               } catch (IOException e) {
                  System.err.println("Caught exception while reading SSE response: " + e.getMessage());
               }
               return response;
            });
         } catch (Exception e) {
            System.err.println("Caught exception while executing SSE client: " + e.getMessage());
         }
      };
      sseClientExecutor.execute(sseClientRunnable);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(250);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      String messageEndpoint = null;
      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("endpoint", frame.value());
         } else if (frame.key().equals("data")) {
            messageEndpoint = frame.value();
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }
      assertNotNull(messageEndpoint);

      // Now clear the frames and send an initialize request to the message endpoint.
      sseFrames.clear();
      String initializeRequest = """
            {
               "jsonrpc": "2.0",
               "method": "initialize",
               "params": {
                  "protocolVersion": "2024-11-05",
                  "capabilities": {},
                  "clientInfo": {
                     "name": "test-client",
                     "version": "1.0.0"
                  }
               }
            }
            """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));

      ResponseEntity<String> response = restTemplate.postForEntity(messageEndpoint,
            new HttpEntity<>(initializeRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.InitializeResult result = mapper.convertValue(rpcResponse.result(),
                  McpSchema.InitializeResult.class);
            assertEquals("Petstore API MCP server", result.serverInfo().name());
            assertEquals("1.0.0", result.serverInfo().version());
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }

      // Now clear the frames and send a tools/list request to the message endpoint.
      sseFrames.clear();
      String toolsListRequest = """
            {
               "jsonrpc": "2.0",
               "method": "tools/list"
            }
            """;

      response = restTemplate.postForEntity(messageEndpoint, new HttpEntity<>(toolsListRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.ListToolsResult result = mapper.convertValue(rpcResponse.result(),
                  McpSchema.ListToolsResult.class);
            assertEquals(4, result.tools().size());
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }

      // Now clear the frames and finallu send a tools/call request to the message endpoint.
      sseFrames.clear();
      String toolsCallRequest = """
            {
               "jsonrpc": "2.0",
               "method": "tools/call",
               "params": {
                  "name": "get_pets_id",
                  "arguments": {
                     "id": "2"
                  }
               }
            }
            """;

      response = restTemplate.postForEntity(messageEndpoint, new HttpEntity<>(toolsCallRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.CallToolResult result = mapper.convertValue(rpcResponse.result(), McpSchema.CallToolResult.class);
            assertTrue(result.content().getFirst() instanceof McpSchema.TextContent);
            McpSchema.TextContent content = (McpSchema.TextContent) result.content().getFirst();
            assertEquals("{\"id\":2,\"name\":\"Tigresse\"}", content.text());
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }
   }

   @Test
   void testGrpcHttpSSEEndpoint() throws Exception {
      // Update Petstore from the tutorial reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/petstore-v1.proto", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/grpc/petstore-v1-examples.yaml", false);

      // Define the runnable for the SSE client.
      Runnable sseClientRunnable = () -> {
         try {
            restTemplate.execute("/mcp/org.acme.petstore.v1.PetstoreService/v1/sse", HttpMethod.GET, request -> {},
                  response -> {
                     String line;
                     try (BufferedReader bufferedReader = new BufferedReader(
                           new InputStreamReader(response.getBody()));) {
                        while ((line = bufferedReader.readLine()) != null) {
                           parseAndStoreMcpSseFrame(line, sseFrames);
                        }
                     } catch (IOException e) {
                        System.err.println("Caught exception while reading SSE response: " + e.getMessage());
                     }
                     return response;
                  });
         } catch (Exception e) {
            System.err.println("Caught exception while executing SSE client: " + e.getMessage());
         }
      };
      sseClientExecutor.execute(sseClientRunnable);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(250);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      String messageEndpoint = null;
      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("endpoint", frame.value());
         } else if (frame.key().equals("data")) {
            messageEndpoint = frame.value();
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }
      assertNotNull(messageEndpoint);

      // Now clear the frames and send an initialize request to the message endpoint.
      sseFrames.clear();
      String initializeRequest = """
            {
               "jsonrpc": "2.0",
               "method": "initialize",
               "params": {
                  "protocolVersion": "2024-11-05",
                  "capabilities": {},
                  "clientInfo": {
                     "name": "test-client",
                     "version": "1.0.0"
                  }
               }
            }
            """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));

      ResponseEntity<String> response = restTemplate.postForEntity(messageEndpoint,
            new HttpEntity<>(initializeRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.InitializeResult result = mapper.convertValue(rpcResponse.result(),
                  McpSchema.InitializeResult.class);
            assertEquals("org.acme.petstore.v1.PetstoreService MCP server", result.serverInfo().name());
            assertEquals("v1", result.serverInfo().version());
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }

      // Now clear the frames and send a tools/list request to the message endpoint.
      sseFrames.clear();
      String toolsListRequest = """
            {
               "jsonrpc": "2.0",
               "method": "tools/list"
            }
            """;

      response = restTemplate.postForEntity(messageEndpoint, new HttpEntity<>(toolsListRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.ListToolsResult result = mapper.convertValue(rpcResponse.result(),
                  McpSchema.ListToolsResult.class);
            assertEquals(3, result.tools().size());
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }

      // Now clear the frames and finallu send a tools/call request to the message endpoint.
      sseFrames.clear();
      String toolsCallRequest = """
            {
               "jsonrpc": "2.0",
               "method": "tools/call",
               "params": {
                  "name": "searchPets",
                  "arguments": {
                     "name": "k"
                  }
               }
            }
            """;

      response = restTemplate.postForEntity(messageEndpoint, new HttpEntity<>(toolsCallRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.CallToolResult result = mapper.convertValue(rpcResponse.result(), McpSchema.CallToolResult.class);
            assertTrue(result.content().getFirst() instanceof McpSchema.TextContent);
            McpSchema.TextContent content = (McpSchema.TextContent) result.content().getFirst();
            assertEquals("{\n" + "  \"pets\": [\n" + "    {\n" + "      \"id\": 3,\n" + "      \"name\": \"Maki\"\n"
                  + "    },\n" + "    {\n" + "      \"id\": 4,\n" + "      \"name\": \"Toufik\"\n" + "    }\n" + "  ]\n"
                  + "}", content.text());
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }
   }

   @Test
   void testGraphQLHttpSSEEndpoint() throws Exception {
      // Update Petstore from the tutorial reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/petstore-1.0.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/petstore-1.0-examples.yaml", false);

      // Define the runnable for the SSE client.
      Runnable sseClientRunnable = () -> {
         try {
            restTemplate.execute("/mcp/Petstore+Graph+API/1.0/sse", HttpMethod.GET, request -> {}, response -> {
               String line;
               try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody()));) {
                  while ((line = bufferedReader.readLine()) != null) {
                     parseAndStoreMcpSseFrame(line, sseFrames);
                  }
               } catch (IOException e) {
                  System.err.println("Caught exception while reading SSE response: " + e.getMessage());
               }
               return response;
            });
         } catch (Exception e) {
            System.err.println("Caught exception while executing SSE client: " + e.getMessage());
         }
      };
      sseClientExecutor.execute(sseClientRunnable);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(250);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      String messageEndpoint = null;
      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("endpoint", frame.value());
         } else if (frame.key().equals("data")) {
            messageEndpoint = frame.value();
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }
      assertNotNull(messageEndpoint);

      // Now clear the frames and send an initialize request to the message endpoint.
      sseFrames.clear();
      String initializeRequest = """
            {
               "jsonrpc": "2.0",
               "method": "initialize",
               "params": {
                  "protocolVersion": "2025-06-18",
                  "capabilities": {},
                  "clientInfo": {
                     "name": "test-client",
                     "version": "1.0.0"
                  }
               }
            }
            """;

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM));

      ResponseEntity<String> response = restTemplate.postForEntity(messageEndpoint,
            new HttpEntity<>(initializeRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.InitializeResult result = mapper.convertValue(rpcResponse.result(),
                  McpSchema.InitializeResult.class);
            assertEquals("Petstore Graph API MCP server", result.serverInfo().name());
            assertEquals("1.0", result.serverInfo().version());
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }

      // Now clear the frames and send a tools/list request to the message endpoint.
      sseFrames.clear();
      String toolsListRequest = """
            {
               "jsonrpc": "2.0",
               "method": "tools/list"
            }
            """;

      response = restTemplate.postForEntity(messageEndpoint, new HttpEntity<>(toolsListRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.ListToolsResult result = mapper.convertValue(rpcResponse.result(),
                  McpSchema.ListToolsResult.class);
            assertEquals(4, result.tools().size());
         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }

      // Now clear the frames and finallu send a tools/call request to the message endpoint.
      sseFrames.clear();
      String toolsCallRequest = """
            {
               "jsonrpc": "2.0",
               "method": "tools/call",
               "params": {
                  "name": "createPet",
                  "arguments": {
                     "newPet": {
                        "name": "Rusty",
                        "color": "stripped"
                     }
                  }
               }
            }
            """;

      response = restTemplate.postForEntity(messageEndpoint, new HttpEntity<>(toolsCallRequest, headers), String.class);

      // SSE emitter is async so wait a few millis before checking.
      Thread.sleep(200);

      // Check we got 3 frames and that we're able to extract the message endpoint.
      assertEquals(3, sseFrames.size());

      for (McpSSEFrame frame : sseFrames) {
         if (frame.key().equals("event")) {
            assertEquals("message", frame.value());
         } else if (frame.key().equals("data")) {
            McpSchema.JSONRPCResponse rpcResponse = mapper.readValue(frame.value(), McpSchema.JSONRPCResponse.class);
            McpSchema.CallToolResult result = mapper.convertValue(rpcResponse.result(), McpSchema.CallToolResult.class);
            assertTrue(result.content().getFirst() instanceof McpSchema.TextContent);
            McpSchema.TextContent content = (McpSchema.TextContent) result.content().getFirst();

            assertTrue(content.text().contains("\"id\":"));
            assertTrue(content.text().contains("\"name\":\"Rusty\""));
            assertTrue(content.text().contains("\"color\":\"stripped\"}"));

         } else if (frame.key().equals("id")) {
            // Got and id frame, ignore it.
         } else {
            fail("Unknown SSE frame: " + frame.key());
         }
      }
   }

   private void parseAndStoreMcpSseFrame(String line, List<McpSSEFrame> sseFrames) {
      if (line.contains(":")) {
         String key = line.substring(0, line.indexOf(':'));
         String value = line.substring(line.indexOf(':') + 1).trim();
         sseFrames.add(new McpSSEFrame(key, value));
      }
   }
}
