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

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.util.DispatchStyles;

import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
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

   @Mock
   private RestTemplate restTemplate;

   @Test
   void shouldRetryRequestResponseParsingWithErrorHistory() throws Exception {
      OpenAICopilot copilot = new OpenAICopilot(Map.of(OpenAICopilot.API_KEY_CONFIG, "test-key"));
      ReflectionTestUtils.setField(copilot, "restTemplate", restTemplate);

      when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            eq(ChatCompletionResult.class))).thenReturn(ResponseEntity.ok(buildCompletionResult(INVALID_YAML)),
                  ResponseEntity.ok(buildCompletionResult(VALID_REQUEST_RESPONSE_YAML)));

      Service service = buildRestService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildRestContract();

      List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation, contract, 1);

      assertEquals(1, exchanges.size());
      ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
      verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), requestCaptor.capture(),
            eq(ChatCompletionResult.class));

      String retryPrompt = extractPrompt(requestCaptor.getAllValues().get(1));
      String expectedError = extractRequestResponseParseError(service, operation, INVALID_YAML);
      assertTrue(retryPrompt.contains("<turn_1>"));
      assertTrue(retryPrompt.contains("The previous output failed with this error: `" + expectedError + "`."));
      assertTrue(retryPrompt.contains("<previous_output>\n" + INVALID_YAML + "\n</previous_output>"));
      assertTrue(retryPrompt.contains("Please regenerate a valid example and avoid this issue."));
   }

   @Test
   void shouldRetryEventParsingWithErrorHistory() throws Exception {
      OpenAICopilot copilot = new OpenAICopilot(Map.of(OpenAICopilot.API_KEY_CONFIG, "test-key"));
      ReflectionTestUtils.setField(copilot, "restTemplate", restTemplate);

      when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            eq(ChatCompletionResult.class))).thenReturn(ResponseEntity.ok(buildCompletionResult(INVALID_YAML)),
                  ResponseEntity.ok(buildCompletionResult(VALID_EVENT_YAML)));

      Service service = buildEventService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildEventContract();

      List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation, contract, 1);

      assertEquals(1, exchanges.size());
      assertTrue(exchanges.getFirst() instanceof UnidirectionalEvent);

      ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
      verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), requestCaptor.capture(),
            eq(ChatCompletionResult.class));

      String retryPrompt = extractPrompt(requestCaptor.getAllValues().get(1));
      String expectedError = extractEventParseError(INVALID_YAML);
      assertTrue(retryPrompt.contains("<turn_1>"));
      assertTrue(retryPrompt.contains(expectedError));
      assertTrue(retryPrompt.contains("<previous_output>\n" + INVALID_YAML + "\n</previous_output>"));
   }

   @Test
   void shouldReturnEmptyListAfterMaxRetriesAreExhausted() throws Exception {
      OpenAICopilot copilot = new OpenAICopilot(Map.of(OpenAICopilot.API_KEY_CONFIG, "test-key"));
      ReflectionTestUtils.setField(copilot, "restTemplate", restTemplate);

      when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            eq(ChatCompletionResult.class))).thenReturn(ResponseEntity.ok(buildCompletionResult(INVALID_YAML)),
                  ResponseEntity.ok(buildCompletionResult(INVALID_YAML)),
                  ResponseEntity.ok(buildCompletionResult(INVALID_YAML)),
                  ResponseEntity.ok(buildCompletionResult(INVALID_YAML)),
                  ResponseEntity.ok(buildCompletionResult(INVALID_YAML)),
                  ResponseEntity.ok(buildCompletionResult(INVALID_YAML)));

      Service service = buildRestService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildRestContract();

      List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation, contract, 1);

      assertTrue(exchanges.isEmpty());
      verify(restTemplate, times(6)).exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            eq(ChatCompletionResult.class));
   }

   @Test
   void shouldUseMaxCompletionTokensForGpt5Models() throws Exception {
      OpenAICopilot copilot = new OpenAICopilot(
            Map.of(OpenAICopilot.API_KEY_CONFIG, "test-key", OpenAICopilot.MODEL_KEY_CONFIG, "gpt-5"));
      ReflectionTestUtils.setField(copilot, "restTemplate", restTemplate);

      when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            eq(ChatCompletionResult.class)))
                  .thenReturn(ResponseEntity.ok(buildCompletionResult(VALID_REQUEST_RESPONSE_YAML)));

      Service service = buildRestService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildRestContract();

      copilot.suggestSampleExchanges(service, operation, contract, 1);

      ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
      verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), requestCaptor.capture(),
            eq(ChatCompletionResult.class));

      Map<String, Object> payload = extractPayload(requestCaptor.getValue());
      assertTrue(OpenAICopilot.usesMaxCompletionTokens("gpt-5"));
      assertTrue(OpenAICopilot.usesMaxCompletionTokens("GPT-5-mini"));
      assertFalse(OpenAICopilot.supportsLogitBias("gpt-5"));
      assertTrue(payload.containsKey("max_completion_tokens"));
      assertFalse(payload.containsKey("max_tokens"));
      assertFalse(payload.containsKey("logit_bias"));
   }

   @Test
   void shouldUseLegacyMaxTokensForNonGpt5Models() throws Exception {
      OpenAICopilot copilot = new OpenAICopilot(
            Map.of(OpenAICopilot.API_KEY_CONFIG, "test-key", OpenAICopilot.MODEL_KEY_CONFIG, "gpt-4o-mini"));
      ReflectionTestUtils.setField(copilot, "restTemplate", restTemplate);

      when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
            eq(ChatCompletionResult.class)))
                  .thenReturn(ResponseEntity.ok(buildCompletionResult(VALID_REQUEST_RESPONSE_YAML)));

      Service service = buildRestService();
      Operation operation = service.getOperations().getFirst();
      Resource contract = buildRestContract();

      copilot.suggestSampleExchanges(service, operation, contract, 1);

      ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
      verify(restTemplate).exchange(anyString(), eq(HttpMethod.POST), requestCaptor.capture(),
            eq(ChatCompletionResult.class));

      Map<String, Object> payload = extractPayload(requestCaptor.getValue());
      assertFalse(OpenAICopilot.usesMaxCompletionTokens("gpt-4o-mini"));
      assertFalse(OpenAICopilot.usesMaxCompletionTokens(null));
      assertTrue(OpenAICopilot.supportsLogitBias("gpt-4o-mini"));
      assertTrue(OpenAICopilot.supportsLogitBias(null));
      assertTrue(payload.containsKey("max_tokens"));
      assertFalse(payload.containsKey("max_completion_tokens"));
      assertTrue(payload.containsKey("logit_bias"));
   }

   private ChatCompletionResult buildCompletionResult(String content) {
      ChatMessage message = new ChatMessage("assistant", content);
      ChatCompletionChoice choice = new ChatCompletionChoice();
      choice.setIndex(0);
      choice.setMessage(message);

      ChatCompletionResult result = new ChatCompletionResult();
      result.setChoices(List.of(choice));
      return result;
   }

   private String extractPrompt(HttpEntity entity) {
      Map<String, Object> request = extractPayload(entity);
      List<ChatMessage> messages = (List<ChatMessage>) request.get("messages");
      return messages.getFirst().getContent();
   }

   private Map<String, Object> extractPayload(HttpEntity entity) {
      return (Map<String, Object>) entity.getBody();
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
