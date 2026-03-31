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
import io.github.microcks.util.DispatchStyles;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This is an implementation of {@code AICopilot} using OpenAI API.
 * @author laurent
 */
public class OpenAICopilot implements AICopilot {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenAICopilot.class);


   /** Configuration parameter holding the OpenAI API key. */
   public static final String API_KEY_CONFIG = "api-key";

   /** Configuration parameter holding the OpenAI API URL. */
   public static final String API_URL_CONFIG = "api-url";

   /** Configuration parameters holding the timeout in seconds for API calls. */
   public static final String TIMEOUT_KEY_CONFIG = "timeout";

   /** Configuration parameter holding the name of model to use. */
   public static final String MODEL_KEY_CONFIG = "model";

   /** Configuration parameter holding the maximum number of tokens to use. */
   public static final String MAX_TOKENS_KEY_CONFIG = "maxTokens";

   /** The mandatory configuration keys required by this implementation. */
   protected static final String[] MANDATORY_CONFIG_KEYS = { API_KEY_CONFIG };


   /** Default online URL for OpenAI API. */
   private static final String OPENAI_BASE_URL = "https://api.openai.com/";

   private static final String SECTION_DELIMITER = "\n#####\n";
   private static final int MAX_PARSE_RETRIES = 5;
   private static final String PREVIOUS_OUTPUT_TAG = "previous_output";
   private static final String RETRY_PROMPT_SUFFIX = "Please regenerate a valid example and avoid this issue.";

   private RestTemplate restTemplate;

   private String apiUrl = OPENAI_BASE_URL;

   private String apiKey;

   private Duration timeout = Duration.ofSeconds(20);

   private String model = "gpt-3.5-turbo";

   private int maxTokens = 2000;


   /**
    * Build a new OpenAICopilot with its configuration.
    * @param configuration The configuration for connecting to OpenAI services.
    */
   public OpenAICopilot(Map<String, String> configuration) {
      if (configuration.containsKey(TIMEOUT_KEY_CONFIG)) {
         try {
            timeout = Duration.ofSeconds(Integer.parseInt(configuration.get(TIMEOUT_KEY_CONFIG)));
         } catch (Exception e) {
            log.warn("Timeout was provided but cannot be parsed. Sticking to the default.");
         }
      }
      if (configuration.containsKey(MAX_TOKENS_KEY_CONFIG)) {
         try {
            maxTokens = Integer.parseInt(configuration.get(MAX_TOKENS_KEY_CONFIG));
         } catch (Exception e) {
            log.warn("MaxTokens was provided but cannot be parsed. Sticking to the default.");
         }
      }
      if (configuration.containsKey(MODEL_KEY_CONFIG)) {
         model = configuration.get(MODEL_KEY_CONFIG);
      }
      if (configuration.containsKey(API_URL_CONFIG)) {
         apiUrl = configuration.get(API_URL_CONFIG);
      }
      // Finally retrieve the OpenAI Api key.
      apiKey = configuration.get(API_KEY_CONFIG);

      // Initialize a Rest template for interacting with OpenAI API.
      // We need to register a custom Jackson converter to handle serialization of name and function_call of messages.
      restTemplate = new RestTemplateBuilder().setReadTimeout(timeout)
            .additionalMessageConverters(mappingJacksonHttpMessageConverter()).build();
   }

   /**
    * Get mandatory configuration parameters.
    * @return The mandatory configuration keys required by this implementation
    */
   public static final String[] getMandatoryConfigKeys() {
      return MANDATORY_CONFIG_KEYS;
   }

   @Override
   public List<? extends Exchange> suggestSampleExchanges(Service service, Operation operation, Resource contract,
         int number) throws Exception {
      String prompt = "";

      if (service.getType() == ServiceType.REST) {
         prompt = preparePromptForOpenAPI(operation, contract, number);
      } else if (service.getType() == ServiceType.GRAPHQL) {
         prompt = preparePromptForGraphQL(operation, contract, number);
      } else if (service.getType() == ServiceType.EVENT) {
         prompt = preparePromptForAsyncAPI(operation, contract, number);
      } else if (service.getType() == ServiceType.GRPC) {
         prompt = preparePromptForGrpc(service, operation, contract, number);
      }

      return suggestSampleExchangesWithRetry(service, operation, prompt);
   }

   private String preparePromptForOpenAPI(Operation operation, Resource contract, int number) throws Exception {
      StringBuilder prompt = new StringBuilder(
            AICopilotHelper.getOpenAPIOperationPromptIntro(operation.getName(), number));

      // Build a prompt reusing templates and elements from AICopilotHelper.
      prompt.append("\n");
      prompt.append(AICopilotHelper.YAML_FORMATTING_PROMPT);
      prompt.append("\n");
      prompt.append(AICopilotHelper.getRequestResponseExampleYamlFormattingDirective(number));
      prompt.append(SECTION_DELIMITER);
      prompt.append(AICopilotHelper.removeTokensFromSpec(contract.getContent(), operation.getName()));

      return prompt.toString();
   }

   private String preparePromptForGraphQL(Operation operation, Resource contract, int number) {
      StringBuilder prompt = new StringBuilder(
            AICopilotHelper.getGraphQLOperationPromptIntro(operation.getName(), number));

      // We need to indicate the name or variables we want.
      if (DispatchStyles.QUERY_ARGS.equals(operation.getDispatcher())) {
         StringBuilder variablesList = new StringBuilder();
         if (operation.getDispatcherRules().contains("&&")) {
            String[] variables = operation.getDispatcherRules().split("&&");
            for (int i = 0; i < variables.length; i++) {
               String variable = variables[i];
               variablesList.append("$").append(variable.trim());
               if (i < variables.length - 1) {
                  variablesList.append(", ");
               }
            }
         } else {
            variablesList.append("$").append(operation.getDispatcherRules());
         }
         prompt.append("Use only '").append(variablesList).append("' as variable identifiers.");
      }

      // Build a prompt reusing templates and elements from AICopilotHelper.
      prompt.append("\n");
      prompt.append(AICopilotHelper.YAML_FORMATTING_PROMPT);
      prompt.append("\n");
      prompt.append(AICopilotHelper.getRequestResponseExampleYamlFormattingDirective(number));
      prompt.append(SECTION_DELIMITER);
      prompt.append(contract.getContent());

      return prompt.toString();
   }

   private String preparePromptForAsyncAPI(Operation operation, Resource contract, int number) throws Exception {
      StringBuilder prompt = new StringBuilder(
            AICopilotHelper.getAsyncAPIOperationPromptIntro(operation.getName(), number));

      // Build a prompt reusing templates and elements from AICopilotHelper.
      prompt.append("\n");
      prompt.append(AICopilotHelper.YAML_FORMATTING_PROMPT);
      prompt.append("\n");
      prompt.append(AICopilotHelper.getUnidirectionalEventExampleYamlFormattingDirective(number));
      prompt.append(SECTION_DELIMITER);
      prompt.append(AICopilotHelper.removeTokensFromSpec(contract.getContent(), operation.getName()));

      return prompt.toString();
   }

   private String preparePromptForGrpc(Service service, Operation operation, Resource contract, int number)
         throws Exception {
      StringBuilder prompt = new StringBuilder(
            AICopilotHelper.getGrpcOperationPromptIntro(service.getName(), operation.getName(), number));

      // Build a prompt reusing templates and elements from AICopilotHelper.
      prompt.append("\n");
      prompt.append(AICopilotHelper.YAML_FORMATTING_PROMPT);
      prompt.append("\n");
      prompt.append(AICopilotHelper.getGrpcRequestResponseExampleYamlFormattingDirective(number));
      prompt.append(SECTION_DELIMITER);
      prompt.append(contract.getContent());

      return prompt.toString();
   }

   private MappingJackson2HttpMessageConverter mappingJacksonHttpMessageConverter() {
      MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
      converter.setObjectMapper(customObjectMapper());
      return converter;
   }

   private static ObjectMapper customObjectMapper() {
      ObjectMapper mapper = new ObjectMapper();
      mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
      return mapper;
   }

   private HttpHeaders createAuthenticationHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.set("Authorization", "Bearer " + apiKey);
      return headers;
   }

   private List<? extends Exchange> suggestSampleExchangesWithRetry(Service service, Operation operation,
         String basePrompt) throws Exception {
      List<RetryTurn> retryHistory = new ArrayList<>();

      for (int retryIndex = 0; retryIndex <= MAX_PARSE_RETRIES; retryIndex++) {
         String prompt = buildPromptWithRetryHistory(basePrompt, retryHistory);
         String content = requestCompletionContent(prompt);
         if (content == null) {
            log.warn("OpenAI returned no content for service {} operation {}", service.getName(), operation.getName());
            return new ArrayList<>();
         }

         try {
            return parseCompletionContent(service, operation, content);
         } catch (Exception e) {
            retryHistory.add(new RetryTurn(safeErrorMessage(e), content));
            if (retryIndex == MAX_PARSE_RETRIES) {
               log.error(
                     "Could not generate valid AI samples for service {} operation {} after {} retries. Last error: {}",
                     service.getName(), operation.getName(), MAX_PARSE_RETRIES, safeErrorMessage(e));
               return new ArrayList<>();
            }
            log.warn("Parsing OpenAI output failed for service {} operation {}. Retrying attempt {}/{}. Error: {}",
                  service.getName(), operation.getName(), retryIndex + 1, MAX_PARSE_RETRIES, safeErrorMessage(e));
         }
      }
      return new ArrayList<>();
   }

   private String requestCompletionContent(String prompt) {
      log.debug("Asking OpenAI to suggest samples for this prompt: {}", prompt);

      final List<ChatMessage> messages = Collections
            .singletonList(new ChatMessage(ChatMessageRole.ASSISTANT.value(), prompt));

      HttpEntity<Map<String, Object>> request = new HttpEntity<>(buildChatCompletionPayload(messages),
            createAuthenticationHeaders());
      ResponseEntity<ChatCompletionResult> responseEntity = restTemplate.exchange(apiUrl + "/v1/chat/completions",
            HttpMethod.POST, request, ChatCompletionResult.class);
      ChatCompletionResult completionResult = responseEntity.getBody();

      if (completionResult == null || completionResult.getChoices() == null
            || completionResult.getChoices().isEmpty()) {
         return null;
      }

      ChatCompletionChoice choice = completionResult.getChoices().get(0);
      if (choice == null || choice.getMessage() == null) {
         return null;
      }

      log.debug("Got this raw output from OpenAI: {}", choice.getMessage().getContent());
      return choice.getMessage().getContent();
   }

   private List<? extends Exchange> parseCompletionContent(Service service, Operation operation, String content)
         throws Exception {
      if (service.getType() == ServiceType.EVENT) {
         return AICopilotHelper.parseUnidirectionalEventTemplateOutput(content);
      }
      return AICopilotHelper.parseRequestResponseTemplateOutput(service, operation, content);
   }

   private String buildPromptWithRetryHistory(String basePrompt, List<RetryTurn> retryHistory) {
      if (retryHistory.isEmpty()) {
         return basePrompt;
      }

      StringBuilder prompt = new StringBuilder(basePrompt);
      prompt.append("\n");
      for (int i = 0; i < retryHistory.size(); i++) {
         RetryTurn retryTurn = retryHistory.get(i);
         prompt.append(String.format("<turn_%d>%n", i + 1));
         prompt.append("The previous output failed with this error: `").append(retryTurn.errorMessage()).append("`.\n");
         prompt.append("<").append(PREVIOUS_OUTPUT_TAG).append(">\n");
         prompt.append(retryTurn.previousOutput()).append("\n");
         prompt.append("</").append(PREVIOUS_OUTPUT_TAG).append(">\n");
         prompt.append(String.format("</turn_%d>%n", i + 1));
      }
      prompt.append(RETRY_PROMPT_SUFFIX);
      return prompt.toString();
   }

   private String safeErrorMessage(Exception exception) {
      return exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
   }

   private Map<String, Object> buildChatCompletionPayload(List<ChatMessage> messages) {
      Map<String, Object> payload = new HashMap<>();
      payload.put("model", model);
      payload.put("messages", messages);
      payload.put("n", 1);

      if (supportsLogitBias(model)) {
         payload.put("logit_bias", new HashMap<>());
      }

      String maxTokenParameter = usesMaxCompletionTokens(model) ? "max_completion_tokens" : "max_tokens";
      payload.put(maxTokenParameter, maxTokens);
      return payload;
   }

   static boolean usesMaxCompletionTokens(String model) {
      return model != null && model.toLowerCase().startsWith("gpt-5");
   }

   static boolean supportsLogitBias(String model) {
      return !usesMaxCompletionTokens(model);
   }

   private record RetryTurn(String errorMessage, String previousOutput) {
   }
}
