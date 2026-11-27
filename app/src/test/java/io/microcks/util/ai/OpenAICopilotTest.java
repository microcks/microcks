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
package io.microcks.util.ai;

import io.microcks.domain.Exchange;
import io.microcks.domain.Operation;
import io.microcks.domain.Resource;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.domain.UnidirectionalEvent;
import io.microcks.util.DispatchStyles;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAICopilotTest {

   private static final String INVALID_YAML = """
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
             body: [
         """;

   private static final String VALID_REQUEST_RESPONSE_YAML = """
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
         """;

   private static final String VALID_EVENT_YAML = """
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
         """;

   private FakeOpenAI fake;

   @BeforeEach
   void startFakeOpenAI() throws IOException {
      fake = new FakeOpenAI();
   }

   @AfterEach
   void stopFakeOpenAI() {
      fake.stop();
   }

   private OpenAICopilot copilot(Map<String, String> extraConfig) {
      Map<String, String> config = new HashMap<>(extraConfig);
      config.put(OpenAICopilot.API_KEY_CONFIG, "test-key");
      config.put(OpenAICopilot.API_URL_CONFIG, fake.url());
      return new OpenAICopilot(config);
   }

   /** Local OpenAI-like endpoint replaying queued completions and recording the JSON request payloads. */
   private static class FakeOpenAI {
      private final HttpServer server;
      private final Queue<String> completions = new ConcurrentLinkedQueue<>();
      private final List<Map<String, Object>> requests = new CopyOnWriteArrayList<>();
      private final ObjectMapper mapper = new ObjectMapper();

      FakeOpenAI() throws IOException {
         server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
         server.createContext("/v1/chat/completions", exchange -> {
            try {
               requests.add(mapper.readValue(exchange.getRequestBody().readAllBytes(), Map.class));
               String content = completions.poll();
               byte[] body = mapper.writeValueAsBytes(Map.of("choices",
                     List.of(Map.of("index", 0, "message", Map.of("role", "assistant", "content", content)))));
               exchange.getResponseHeaders().add("Content-Type", "application/json");
               exchange.sendResponseHeaders(200, body.length);
               exchange.getResponseBody().write(body);
            } finally {
               exchange.close();
            }
         });
         server.start();
      }

      void enqueue(String... contents) {
         completions.addAll(List.of(contents));
      }

      List<Map<String, Object>> requests() {
         return requests;
      }

      String url() {
         return "http://127.0.0.1:" + server.getAddress().getPort();
      }

      void stop() {
         server.stop(0);
      }
   }

   @Test
   void shouldRetryRequestResponseParsingWithErrorHistory() throws Exception {
      OpenAICopilot copilot = copilot(Map.of());

      fake.enqueue(INVALID_YAML, VALID_REQUEST_RESPONSE_YAML);

      Service service = buildRestService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildRestContract();

      List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation, contract, 1);

      assertEquals(1, exchanges.size());
      assertEquals(2, fake.requests().size());

      String retryPrompt = extractPrompt(fake.requests().get(1));
      String expectedError = extractRequestResponseParseError(service, operation, INVALID_YAML);
      assertTrue(retryPrompt.contains("<turn_1>"));
      assertTrue(retryPrompt.contains("The previous output failed with this error: `" + expectedError + "`."));
      assertTrue(retryPrompt.contains("<previous_output>\n" + INVALID_YAML + "\n</previous_output>"));
      assertTrue(retryPrompt.contains("Please regenerate a valid example and avoid this issue."));
   }

   @Test
   void shouldRetryEventParsingWithErrorHistory() throws Exception {
      OpenAICopilot copilot = copilot(Map.of());

      fake.enqueue(INVALID_YAML, VALID_EVENT_YAML);

      Service service = buildEventService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildEventContract();

      List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation, contract, 1);

      assertEquals(1, exchanges.size());
      assertTrue(exchanges.getFirst() instanceof UnidirectionalEvent);

      assertEquals(2, fake.requests().size());

      String retryPrompt = extractPrompt(fake.requests().get(1));
      String expectedError = extractEventParseError(INVALID_YAML);
      assertTrue(retryPrompt.contains("<turn_1>"));
      assertTrue(retryPrompt.contains(expectedError));
      assertTrue(retryPrompt.contains("<previous_output>\n" + INVALID_YAML + "\n</previous_output>"));
   }

   @Test
   void shouldReturnEmptyListAfterMaxRetriesAreExhausted() throws Exception {
      OpenAICopilot copilot = copilot(Map.of());

      fake.enqueue(INVALID_YAML, INVALID_YAML, INVALID_YAML, INVALID_YAML, INVALID_YAML, INVALID_YAML);

      Service service = buildRestService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildRestContract();

      List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation, contract, 1);

      assertTrue(exchanges.isEmpty());
      assertEquals(6, fake.requests().size());
   }

   @Test
   void shouldUseMaxCompletionTokensForGpt5Models() throws Exception {
      OpenAICopilot copilot = copilot(Map.of(OpenAICopilot.MODEL_KEY_CONFIG, "gpt-5"));

      fake.enqueue(VALID_REQUEST_RESPONSE_YAML);

      Service service = buildRestService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildRestContract();

      copilot.suggestSampleExchanges(service, operation, contract, 1);

      assertEquals(1, fake.requests().size());

      Map<String, Object> payload = fake.requests().getFirst();
      assertTrue(OpenAICopilot.usesMaxCompletionTokens("gpt-5"));
      assertTrue(OpenAICopilot.usesMaxCompletionTokens("GPT-5-mini"));
      assertFalse(OpenAICopilot.supportsLogitBias("gpt-5"));
      assertTrue(payload.containsKey("max_completion_tokens"));
      assertFalse(payload.containsKey("max_tokens"));
      assertFalse(payload.containsKey("logit_bias"));
   }

   @Test
   void shouldUseLegacyMaxTokensForNonGpt5Models() throws Exception {
      OpenAICopilot copilot = copilot(Map.of(OpenAICopilot.MODEL_KEY_CONFIG, "gpt-4o-mini"));

      fake.enqueue(VALID_REQUEST_RESPONSE_YAML);

      Service service = buildRestService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildRestContract();

      copilot.suggestSampleExchanges(service, operation, contract, 1);

      assertEquals(1, fake.requests().size());

      Map<String, Object> payload = fake.requests().getFirst();
      assertFalse(OpenAICopilot.usesMaxCompletionTokens("gpt-4o-mini"));
      assertFalse(OpenAICopilot.usesMaxCompletionTokens(null));
      assertTrue(OpenAICopilot.supportsLogitBias("gpt-4o-mini"));
      assertTrue(OpenAICopilot.supportsLogitBias(null));
      assertTrue(payload.containsKey("max_tokens"));
      assertFalse(payload.containsKey("max_completion_tokens"));
      assertTrue(payload.containsKey("logit_bias"));
   }

   private String extractPrompt(Map<String, Object> request) {
      List<Map<String, Object>> messages = (List<Map<String, Object>>) request.get("messages");
      return (String) messages.getFirst().get("content");
   }

   private String extractRequestResponseParseError(Service service, Operation operation, String content) {
      try {
         AICopilotHelper.parseRequestResponseTemplateOutput(service, operation, content);
      } catch (Exception e) {
         return e.getMessage();
      }
      throw new AssertionError("Expected request/response parsing to fail");
   }

   private String extractEventParseError(String content) {
      try {
         AICopilotHelper.parseUnidirectionalEventTemplateOutput(content);
      } catch (Exception e) {
         return e.getMessage();
      }
      throw new AssertionError("Expected event parsing to fail");
   }

   private Service buildRestService() {
      Service service = new Service();
      service.setId("service-1");
      service.setName("Pastry API");
      service.setType(ServiceType.REST);

      Operation operation = new Operation();
      operation.setName("GET /pastries/{name}");
      operation.setDispatcher(DispatchStyles.URI_PARTS);
      operation.setDispatcherRules("name");
      service.setOperations(List.of(operation));
      return service;
   }

   private Resource buildRestContract() {
      Resource contract = new Resource();
      contract.setContent("""
            openapi: 3.0.2
            info:
              title: API Pastry
              version: "1.0.0"
            paths:
              /pastries/{name}:
                get:
                  parameters:
                    - name: name
                      in: path
                      required: true
                      schema:
                        type: string
                  responses:
                    '200':
                      description: Pastry
                      content:
                        application/json:
                          schema:
                            type: object
                            properties:
                              name:
                                type: string
            """);
      return contract;
   }

   private Service buildEventService() {
      Service service = new Service();
      service.setId("service-2");
      service.setName("User signed-up API");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE user/signedup");
      service.setOperations(List.of(operation));
      return service;
   }

   private Resource buildEventContract() {
      Resource contract = new Resource();
      contract.setContent("""
            asyncapi: '2.6.0'
            info:
              title: User signed-up API
              version: '1.0.0'
            channels:
              user/signedup:
                subscribe:
                  message:
                    payload:
                      type: object
                      properties:
                        id:
                          type: string
            """);
      return contract;
   }
}
