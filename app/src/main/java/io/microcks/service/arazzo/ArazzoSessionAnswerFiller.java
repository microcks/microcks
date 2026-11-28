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
package io.microcks.service.arazzo;

import io.microcks.artifact.arazzo.ArazzoExpressionContext;
import io.microcks.artifact.arazzo.ArazzoExpressionEvaluator;
import io.microcks.artifact.arazzo.ArazzoExpressionException;
import io.microcks.artifact.arazzo.ArazzoParameter;
import io.microcks.artifact.arazzo.ArazzoReceivedRequest;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.domain.Parameter;
import io.microcks.domain.Request;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Fills the example a step of a workflow answers with, in a session, with the values of the session. The example pairs
 * a request with its response. The step is read in reverse: each parameter and each request body replacement of the
 * step tells where a value goes in the request; the example request holds a value there; the places of the example
 * response holding that same value receive the value of the session. The value of the session is the value the request
 * carries there, as the mocks reflect what the application sent; when it carries none, the value of the runtime
 * expression of the step, from the inputs of the session and the outputs of its earlier steps.
 * @author SebastienDegodez
 */
final class ArazzoSessionAnswerFiller {

   private static final ObjectMapper JSON = new ObjectMapper();
   private static final ArazzoExpressionEvaluator EXPRESSIONS = new ArazzoExpressionEvaluator();

   private ArazzoSessionAnswerFiller() {
   }

   /**
    * @param step     The step the call is answered for
    * @param example  The request of the example the mocks answer with
    * @param received The request the mocks received for the step
    * @param session  What the runtime expressions of the step see in the session
    * @return The value of the session each value of the example request is replaced with, by the text of the example
    *         value
    */
   static Map<String, Object> replacements(ArazzoStep step, Request example, ArazzoReceivedRequest received,
         ArazzoExpressionContext session) {
      Map<String, Object> replacements = new LinkedHashMap<>();
      for (ArazzoParameter parameter : step.parameters()) {
         exampleParameter(example, parameter).ifPresent(
               exampleValue -> valueOf(parameter.value(), received.parameter(parameter.in(), parameter.name()), session)
                     .ifPresent(value -> replacements.put(exampleValue, value)));
      }
      if (step.requestBody() != null) {
         ArazzoReceivedRequest exampleRequest = new ArazzoReceivedRequest("", "", null, Map.of(), example.getContent());
         step.requestBody().replacements()
               .forEach((target, expression) -> exampleRequest.bodyValue(target)
                     .ifPresent(exampleValue -> valueOf(expression, received.bodyValue(target), session)
                           .ifPresent(value -> replacements.put(String.valueOf(exampleValue), value))));
      }
      return replacements;
   }

   /**
    * The value the request carries, as the mocks reflect what the application sent; else the value of the runtime
    * expression of the step in the session.
    */
   private static Optional<Object> valueOf(String value, Optional<Object> received, ArazzoExpressionContext session) {
      return received.or(() -> Optional.of(value).filter(ArazzoExpressionEvaluator::isExpression)
            .flatMap(expression -> evaluated(expression, session)));
   }

   private static Optional<Object> evaluated(String expression, ArazzoExpressionContext session) {
      try {
         return Optional.ofNullable(EXPRESSIONS.evaluate(expression, session));
      } catch (ArazzoExpressionException noValue) {
         // An input the session was not opened with, or the output of a step the session has not answered yet.
         return Optional.empty();
      }
   }

   /**
    * The value the example request holds where the step puts a parameter: Microcks keeps the path and query parameters
    * of an example request as its query parameters, and its header parameters as its headers.
    */
   private static Optional<String> exampleParameter(Request example, ArazzoParameter parameter) {
      if ("header".equals(parameter.in())) {
         return Optional.ofNullable(example.getHeaders()).stream().flatMap(Set::stream)
               .filter(header -> header.getName().equalsIgnoreCase(parameter.name()))
               .flatMap(header -> header.getValues().stream()).findFirst();
      }
      return Optional.ofNullable(example.getQueryParameters()).stream().flatMap(List::stream)
            .filter(query -> query.getName().equals(parameter.name())).map(Parameter::getValue).findFirst();
   }

   /**
    * @param body         The body of the example response
    * @param replacements The value each value of the example is replaced with, by the text of the example value
    * @return The body with the values replaced; the body as it is when there is nothing to replace or it is not JSON
    */
   static String filled(String body, Map<String, Object> replacements) {
      if (replacements.isEmpty()) {
         return body;
      }
      try {
         JsonNode tree = JSON.readTree(body);
         replace(tree, replacements);
         return tree.toString();
      } catch (IllegalArgumentException | JsonProcessingException notJson) {
         return body;
      }
   }

   /** Replace the scalar values of the members or items of a JSON tree, in place, at any depth. */
   private static void replace(JsonNode node, Map<String, Object> replacements) {
      if (node instanceof ObjectNode object) {
         object.properties().forEach(member -> member.setValue(replaced(member.getValue(), replacements)));
      } else if (node instanceof ArrayNode array) {
         for (int index = 0; index < array.size(); index++) {
            array.set(index, replaced(array.get(index), replacements));
         }
      }
   }

   private static JsonNode replaced(JsonNode node, Map<String, Object> replacements) {
      if (node.isValueNode() && replacements.containsKey(node.asText())) {
         return JSON.valueToTree(replacements.get(node.asText()));
      }
      replace(node, replacements);
      return node;
   }
}
