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
package io.github.microcks.util.ai;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.theokanning.openai.completion.chat.ChatCompletionChoice;
import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatCompletionResult;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.completion.chat.ChatMessageRole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This is an implementation of {@code AICopilot} using OpenAI API.
 * @author laurent
 */
public class OpenAICopilot implements AICopilot {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(OpenAICopilot.class);


   /** Configuration parameters holding the OpenAI API key. */
   public static final String API_KEY_CONFIG = "api-key";

   /** Configuration parameters holding the timeout in seconds for API calls. */
   public static final String TIMEOUT_KEY_CONFIG = "timeout";

   /** Configuration parameters holding the name of model to use. */
   public static final String MODEL_KEY_CONFIG = "model";

   /** Configuration parameters holding the maximum number of tokens to use. */
   public static final String MAX_TOKENS_KEY_CONFIG = "maxTokens";

   /** The mandatory configuration keys required by this implementation. */
   public static final String[] MANDATORY_CONFIG_KEYS = {API_KEY_CONFIG};


   private static final String BASE_URL = "https://api.openai.com/";

   private RestTemplate restTemplate;

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
      // Finally retrieve the OpenAI Api key.
      apiKey = configuration.get(API_KEY_CONFIG);

      // Initialize a Rest template for interacting with OpenAI API.
      // We need to register a custom Jackson converter to handle serialization of name and function_call of messages.
      restTemplate = new RestTemplateBuilder()
            .setReadTimeout(timeout)
            .additionalMessageConverters(mappingJacksonHttpMessageConverter())
            .build();
   }

   /**
    * Get mandatory configuration parameters.
    * @return The mandatory configuration keys required by this implementation
    */
   public static final String[] getMandatoryConfigKeys() {
      return MANDATORY_CONFIG_KEYS;
   }

   @Override
   public List<? extends Exchange> suggestSampleExchanges(Service service, String operationName, Resource contract, int number) throws Exception {
      // Build a prompt reusing templates and elements from AICopilotHelper.
      StringBuilder promptBuilder = new StringBuilder(AICopilotHelper.getOpenAPIOperationPromptIntro(operationName, number));
      promptBuilder.append("\n");
      promptBuilder.append(AICopilotHelper.YAML_FORMATTING_PROMPT);
      promptBuilder.append("\n");
      promptBuilder.append(AICopilotHelper.getRequestResponseExampleYamlFormattingDirective(1));
      promptBuilder.append("\n###\n");
      promptBuilder.append(AICopilotHelper.removeExamplesFromOpenAPISpec(contract.getContent()));

      log.debug("Asking OpenAI to suggest samples for this prompt: {}", promptBuilder);

      final List<ChatMessage> messages = new ArrayList<>();
      final ChatMessage assistantMessage = new ChatMessage(ChatMessageRole.ASSISTANT.value(), promptBuilder.toString());
      messages.add(assistantMessage);

      ChatCompletionRequest chatCompletionRequest = ChatCompletionRequest.builder()
            .model(model)
            .messages(messages)
            .n(1)
            .maxTokens(maxTokens)
            .logitBias(new HashMap<>()).build();

      // Build a full HttpEntity as we need to specify authentication headers.
      HttpEntity<ChatCompletionRequest> request = new HttpEntity<>(chatCompletionRequest, createAuthenticationHeaders());
      ChatCompletionResult completionResult = restTemplate.exchange(BASE_URL + "/v1/chat/completions",
                  HttpMethod.POST, request, ChatCompletionResult.class).getBody();

      if (completionResult != null) {
         ChatCompletionChoice choice = completionResult.getChoices().get(0);
         log.debug("Got this raw output from OpenAI: {}", choice.getMessage().getContent());

         // Find the matching operation on service.
         Optional<Operation> operation = service.getOperations().stream()
               .filter(op -> operationName.equals(op.getName()))
               .findFirst();

         // Use the helper to parse output content before returning.
         if (operation.isPresent()) {
            return AICopilotHelper.parseRequestResponseTemplatizedOutput(operation.get(), choice.getMessage().getContent());
         }
      }
      // Return empty list.
      return new ArrayList<>();
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
      mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
      mapper.addMixIn(ChatCompletionRequest.class, ChatCompletionRequestMixIn.class);
      return mapper;
   }

   private HttpHeaders createAuthenticationHeaders(){
      HttpHeaders headers = new HttpHeaders();
      headers.set("Authorization", "Bearer " + apiKey);
      return headers;
   }
}
