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
class ExpressionEvaluatorTest {

   private static final EvaluationContext CONTEXT = new EvaluationContext(
         Map.of("pastry", "Eclair Cafe", "address", Map.of("city", "Lille")),
         Map.of("list", Map.of("first", "Baba Rhum", "pastries", List.of("Baba Rhum", "Divorces"))),
         new HttpOutcome(201, Map.of("X-Trace", List.of("t-1", "t-2")), "[{\"id\":7}]"));

   private final ExpressionEvaluator evaluator = new ExpressionEvaluator();

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "$statusCode | 201", "$response.body | [{id=7}]", "$response.body#/0/id | 7",
         "$response.header.x-trace | t-1", "$inputs.pastry | Eclair Cafe", "$inputs.address#/city | Lille",
         "$steps.list.outputs.first | Baba Rhum", "$steps.list.outputs.pastries#/1 | Divorces",
         "application/json | application/json" })
   void eachSupportedExpressionHasAValue(String expression, String value) {
      assertEquals(value, String.valueOf(evaluator.resolve(expression, CONTEXT)));
   }

   @Test
   void aBodyThatIsNotJsonIsText() {
      assertEquals("<pastry/>",
            evaluator.evaluate("$response.body", CONTEXT.with(new HttpOutcome(200, Map.of(), "<pastry/>"))));
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
            assertThrows(ArazzoRunException.class, () -> evaluator.evaluate(expression, CONTEXT)).getMessage());
   }

   @ParameterizedTest
   @CsvSource({ "$statusCode", "$response.body", "$response.header.X-Trace" })
   void anExpressionOnTheResponseHasNoValueBeforeTheCall(String expression) {
      EvaluationContext beforeTheCall = new EvaluationContext(Map.of(), Map.of(), null);

      assertEquals("No response yet for " + expression,
            assertThrows(ArazzoRunException.class, () -> evaluator.evaluate(expression, beforeTheCall)).getMessage());
   }
}
