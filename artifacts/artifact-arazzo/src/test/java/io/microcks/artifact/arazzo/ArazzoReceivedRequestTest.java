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
package io.microcks.artifact.arazzo;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the values a request an application sent carries where the parameters and request body replacements of a
 * step put theirs.
 * @author SebastienDegodez
 */
class ArazzoReceivedRequestTest {

   private static final ArazzoReceivedRequest CONFIRM = new ArazzoReceivedRequest("/orders/{orderId}/lines/{line}",
         "/orders/o%2042/lines/2", "notify=true&channel=e%20mail&empty=&flag",
         Map.of("X-Request-Id", List.of("q-1", "q-2")),
         "{\"orderId\": \"o-42\", \"amount\": 25.0, \"items\": [{\"sku\": \"ABC-1\"}]}");

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = { "path | orderId | o 42", "path | line | 2",
         "path | missing | null", "query | notify | true", "query | channel | e mail", "query | empty | ''",
         "query | flag | ''", "query | missing | null", "header | x-request-id | q-1", "header | missing | null",
         "cookie | session | null" })
   void aParameterIsReadWhereTheStepPutsIt(String in, String name, String value) {
      assertEquals(Optional.ofNullable(value), CONFIRM.parameter(in, name));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = { "/orderId | o-42", "/amount | 25.0",
         "/items/0/sku | ABC-1", "/missing | null", "/items/3 | null" })
   void aBodyValueIsReadAtItsJsonPointer(String pointer, String value) {
      assertEquals(Optional.ofNullable(value), CONFIRM.bodyValue(pointer).map(String::valueOf));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = { "null | /orderId", "'<order/>' | /orderId" })
   void aBodyThatIsNotJsonHasNoValue(String body, String pointer) {
      ArazzoReceivedRequest request = new ArazzoReceivedRequest("/orders", "/orders", null, Map.of(), body);

      assertEquals(Optional.empty(), request.bodyValue(pointer));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = { "/orders/{orderId} | /orders/o-42/confirm",
         "/orders/{orderId}/confirm | /orders/o-42", "/orders/{orderId}/confirm | /payments/o-42/confirm" })
   void aPathThatDoesNotFollowTheTemplateHasNoPathParameter(String template, String path) {
      ArazzoReceivedRequest request = new ArazzoReceivedRequest(template, path, null, Map.of(), null);

      assertEquals(Optional.empty(), request.parameter("path", "orderId"));
      assertEquals(Optional.empty(), request.parameter("query", "orderId"));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "/orders/{order.id} | /orders/o-42 | order.id",
         "/orders({orderId}) | /orders(o-42) | orderId" })
   void aTemplateIsReadLiterallyAroundItsParameters(String template, String path, String name) {
      ArazzoReceivedRequest request = new ArazzoReceivedRequest(template, path, null, Map.of(), null);

      assertEquals(Optional.of("o-42"), request.parameter("path", name));
   }
}
