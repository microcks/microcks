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
package io.microcks.minion.arazzo.runner;

import io.microcks.artifact.arazzo.ArazzoExpressionContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * What the runtime expressions of a step can see: the inputs of the workflow, the outputs of the steps before it and,
 * once it is called, its response.
 * @param inputs   The inputs of the workflow, by name
 * @param steps    The outputs of the steps executed so far, by step id then output name
 * @param response The response of the step; null before it is called
 * @author SebastienDegodez
 */
record EvaluationContext(Map<String, Object> inputs, Map<String, Map<String, Object>> steps, HttpOutcome response)
      implements ArazzoExpressionContext {

   private static final ObjectMapper JSON = new ObjectMapper();

   /**
    * @param response The response of the step
    * @return This context, seeing the response of the step
    */
   EvaluationContext with(HttpOutcome response) {
      return new EvaluationContext(inputs, steps, response);
   }

   /** The response, for the expressions that need one. */
   HttpOutcome responseFor(String expression) {
      if (response == null) {
         throw new ArazzoRunException("No response yet for " + expression);
      }
      return response;
   }

   @Override
   public int statusCode(String expression) {
      return responseFor(expression).status();
   }

   @Override
   public Map<String, List<String>> responseHeaders(String expression) {
      return responseFor(expression).headers();
   }

   /** The body of the response read as JSON, or as text when it is not JSON. */
   @Override
   public Object responseBody(String expression) {
      String body = responseFor(expression).body();
      try {
         return JSON.readValue(body, Object.class);
      } catch (JsonProcessingException notJson) {
         return body;
      }
   }
}
