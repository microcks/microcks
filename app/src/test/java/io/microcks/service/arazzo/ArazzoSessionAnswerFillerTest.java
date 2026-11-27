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

import io.microcks.artifact.arazzo.ArazzoOperationCall;
import io.microcks.artifact.arazzo.ArazzoParameter;
import io.microcks.artifact.arazzo.ArazzoReceivedRequest;
import io.microcks.artifact.arazzo.ArazzoRequestBody;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.domain.Header;
import io.microcks.domain.Parameter;
import io.microcks.domain.Request;
import io.microcks.service.arazzo.ArazzoSessionVerifier.ReceivedCall;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests of filling the example a step answers with in a session: where the step puts each value in the request, the
 * value the example request holds there, and the places of the example response holding it.
 * @author SebastienDegodez
 */
class ArazzoSessionAnswerFillerTest {

   private static final Map<String, Map<String, Object>> ORDER_O_42 = Map.of("createOrder",
         Map.of("id", "o-42", "amount", 25.0));

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = {
         "members and items at any depth | {\"id\": \"o-1\", \"lines\": [{\"order\": \"o-1\"}], \"refs\": [\"o-1\", \"x\"]} "
               + "| {\"id\":\"o-42\",\"lines\":[{\"order\":\"o-42\"}],\"refs\":[\"o-42\",\"x\"]}",
         "a number by its text | {\"amount\": 10.0, \"status\": \"PAID\"} | {\"amount\":25.0,\"status\":\"PAID\"}",
         "a body that is not JSON, as it is | <order>o-1</order> | <order>o-1</order>" })
   void theValuesOfTheExampleAreReplacedInItsResponse(String what, String body, String filled) {
      assertEquals(filled, ArazzoSessionAnswerFiller.filled(body, Map.of("o-1", "o-42", "10.0", 25.0)));
   }

   @Test
   void aResponseWithNothingToReplaceIsKeptAsItIs() {
      assertEquals("{ \"id\" : \"o-1\" }", ArazzoSessionAnswerFiller.filled("{ \"id\" : \"o-1\" }", Map.of()));
      assertNull(ArazzoSessionAnswerFiller.filled(null, Map.of("o-1", "o-42")));
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = {
         "a path parameter the request carries is reflected | path | orderId | $steps.createOrder.outputs.id "
               + "| o-1=o-7",
         "a header parameter, whatever the case of its name | header | X-Order | $steps.createOrder.outputs.id "
               + "| o-1=o-7",
         "a query parameter the request carries is reflected | query | status | PENDING | PAID=CONFIRMED",
         "a parameter the request does not carry takes the output of an earlier step | query | ref "
               + "| $steps.createOrder.outputs.id | R-1=o-42",
         "a parameter the request does not carry takes an input of the session | query | ref | $inputs.ref "
               + "| R-1=r-5",
         "a constant the request does not carry is not a value of the session | query | ref | o-5 | ''",
         "an output without value leaves the example as it is | query | ref | $steps.createOrder.outputs.status "
               + "| ''",
         "a parameter the example request does not hold | query | page | $steps.createOrder.outputs.id | ''" })
   void eachParameterOfTheStepTellsWhereItsValueGoes(String what, String in, String name, String value,
         String replacements) {
      Request example = new Request();
      example.setQueryParameters(
            List.of(parameter("orderId", "o-1"), parameter("status", "PAID"), parameter("ref", "R-1")));
      example.setHeaders(new LinkedHashSet<>(
            List.of(new Header("Accept", Set.of("application/json")), new Header("x-order", Set.of("o-1")))));
      ArazzoReceivedRequest received = new ArazzoReceivedRequest("/orders/{orderId}", "/orders/o-7", "status=CONFIRMED",
            Map.of("x-order", List.of("o-7")), null);

      assertEquals(replacements,
            text(ArazzoSessionAnswerFiller.replacements(step(List.of(new ArazzoParameter(name, in, value)), null),
                  example, received, new ReceivedCall(Map.of("ref", "r-5"), ORDER_O_42, null))));
   }

   @Test
   void eachRequestBodyReplacementOfTheStepTellsWhereItsValueGoes() {
      Request example = new Request();
      example.setContent("{\"orderId\": \"o-1\", \"amount\": 10.0}");
      ArazzoReceivedRequest received = new ArazzoReceivedRequest("/payments", "/payments", null, Map.of(),
            "{\"orderId\": \"o-7\", \"currency\": \"EUR\"}");
      Map<String, String> targets = new HashMap<>(Map.of("/orderId", "$steps.createOrder.outputs.id", "/amount",
            "$steps.createOrder.outputs.amount", "/currency", "EUR"));

      // The request carries the order, which is reflected; it carries no amount, which the session gives.
      assertEquals("10.0=25.0, o-1=o-7",
            text(ArazzoSessionAnswerFiller.replacements(step(List.of(), new ArazzoRequestBody("", "", targets)),
                  example, received, session(ORDER_O_42))));
   }

   @Test
   void anOutputWhoseValueIsNullLeavesTheExampleAsItIs() {
      Request example = new Request();
      example.setContent("{\"orderId\": \"o-1\"}");
      ArazzoReceivedRequest received = new ArazzoReceivedRequest("/payments", "/payments", null, Map.of(), "{}");
      Map<String, Object> noId = new HashMap<>();
      noId.put("id", null);

      assertEquals("",
            text(ArazzoSessionAnswerFiller.replacements(
                  step(List.of(), new ArazzoRequestBody("", "", Map.of("/orderId", "$steps.createOrder.outputs.id"))),
                  example, received, session(Map.of("createOrder", noId)))));
   }

   private static ArazzoStep step(List<ArazzoParameter> parameters, ArazzoRequestBody requestBody) {
      return new ArazzoStep("confirmOrder", null, new ArazzoOperationCall("orderApi", "confirmOrder", null), parameters,
            requestBody, List.of(), Map.of(), List.of(), List.of(), null, List.of());
   }

   private static ReceivedCall session(Map<String, Map<String, Object>> outputs) {
      return new ReceivedCall(Map.of(), outputs, null);
   }

   private static Parameter parameter(String name, String value) {
      Parameter parameter = new Parameter();
      parameter.setName(name);
      parameter.setValue(value);
      return parameter;
   }

   /** The replacements as sorted text, {@code <example value>=<value>}, comma separated. */
   private static String text(Map<String, Object> replacements) {
      return String.join(", ",
            replacements.entrySet().stream().map(entry -> entry.getKey() + "=" + entry.getValue()).sorted().toList());
   }
}
