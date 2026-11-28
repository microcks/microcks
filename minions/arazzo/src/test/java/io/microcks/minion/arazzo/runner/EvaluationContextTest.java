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

import io.microcks.artifact.arazzo.ArazzoExpressionEvaluator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of what the runtime expressions of a step see of the response the step received.
 * @author SebastienDegodez
 */
class EvaluationContextTest {

   private static final EvaluationContext ANSWERED = new EvaluationContext(Map.of(), Map.of(),
         new HttpOutcome(201, Map.of("X-Trace", List.of("t-1", "t-2")), "[{\"id\":7}]"));

   private final ArazzoExpressionEvaluator evaluator = new ArazzoExpressionEvaluator();

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "$statusCode | 201", "$response.body#/0/id | 7",
         "$response.header.x-trace | t-1" })
   void theExpressionsOnTheResponseSeeTheResponseReceived(String expression, String value) {
      assertEquals(value, String.valueOf(evaluator.evaluate(expression, ANSWERED)));
   }

   @Test
   void aBodyThatIsNotJsonIsText() {
      assertEquals("<pastry/>",
            evaluator.evaluate("$response.body", ANSWERED.with(new HttpOutcome(200, Map.of(), "<pastry/>"))));
   }

}
