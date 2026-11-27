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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the runtime expressions a workflow can use, evaluated after a step answered.
 * @author SebastienDegodez
 */
class ArazzoExpressionEvaluatorTest {

   private static final Context CONTEXT = new Context(
         Map.of("pastry", "Eclair Cafe", "address", Map.of("city", "Lille")),
         Map.of("list", Map.of("first", "Baba Rhum", "pastries", List.of("Baba Rhum", "Divorces"))), 201,
         Map.of("X-Trace", List.of("t-1", "t-2")), List.of(Map.of("id", 7)));

   private final ArazzoExpressionEvaluator evaluator = new ArazzoExpressionEvaluator();

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "$statusCode | 201", "$response.body | [{id=7}]", "$response.body#/0/id | 7",
         "$response.header.x-trace | t-1", "$inputs.pastry | Eclair Cafe", "$inputs.address#/city | Lille",
         "$steps.list.outputs.first | Baba Rhum", "$steps.list.outputs.pastries#/1 | Divorces",
         "application/json | application/json" })
   void eachSupportedExpressionHasAValue(String expression, String value) {
      assertEquals(value, String.valueOf(evaluator.resolve(expression, CONTEXT)));
   }

   @Test
   void theBodyIsWhatTheContextReadsOfTheResponse() {
      assertEquals("<pastry/>",
            evaluator.evaluate("$response.body", new Context(Map.of(), Map.of(), 200, Map.of(), "<pastry/>")));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "$response.header.X-Missing | No response header X-Missing",
         "$inputs.missing | Unknown input: missing", "$steps.missing.outputs.first | Unknown step: missing",
         "$steps.list.outputs.missing | Unknown output of step list: missing",
         "$steps.list | Unsupported runtime expression: $steps.list",
         "$steps.outputs.first | Unsupported runtime expression: $steps.outputs.first",
         "$steps..outputs.first | Unsupported runtime expression: $steps..outputs.first",
         "$request.body | Unsupported runtime expression: $request.body",
         "$response.body#/1/id | No value at JSON pointer /1/id in $response.body" })
   void anExpressionWithoutValueIsReportedWithTheReason(String expression, String reason) {
      assertEquals(reason,
            assertThrows(ArazzoExpressionException.class, () -> evaluator.evaluate(expression, CONTEXT)).getMessage());
   }

   /** A response already read, as the minion and Microcks give it. */
   private record Context(Map<String, Object> inputs, Map<String, Map<String, Object>> steps, int status,
         Map<String, List<String>> headers, Object body) implements ArazzoExpressionContext {

      @Override
      public int statusCode(String expression) {
         return status;
      }

      @Override
      public Map<String, List<String>> responseHeaders(String expression) {
         return headers;
      }

      @Override
      public Object responseBody(String expression) {
         return body;
      }
   }
}
