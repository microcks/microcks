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

import io.microcks.artifact.arazzo.ArazzoCriterion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the success criteria a step can declare, evaluated on the response of Eclair Cafe.
 * @author SebastienDegodez
 */
class ConditionEvaluatorTest {

   private static final EvaluationContext CONTEXT = new EvaluationContext(Map.of("newPrice", 2.6), Map.of(),
         new HttpOutcome(200, Map.of("Content-Type", List.of("application/json")),
               "{\"name\":\"Eclair Cafe\",\"price\":2.5,\"available\":true,\"stock\":null}"));

   private final ConditionEvaluator evaluator = new ConditionEvaluator();

   @ParameterizedTest
   @CsvSource(delimiter = ';', quoteCharacter = '"', value = { "1 == 2.0 ; false", "2 == 2.0 ; true", "1 != 2.0 ; true",
         "2 != 2.0 ; false", "1 < 2.0 ; true", "2 < 2.0 ; false", "1 <= 2.0 ; true", "2 <= 2.0 ; true",
         "3 <= 2.0 ; false", "2 > 2.0 ; false", "3 > 2.0 ; true", "1 >= 2.0 ; false", "2 >= 2.0 ; true",
         "3 >= 2.0 ; true", "'a b' == 'a b' ; true", "'2' == 2 ; false", "'a' != 'b' ; true", "'a' != 'a' ; false",
         "true ; true", "false ; false", "true && true ; true", "true && false ; false", "false && true ; false",
         "false || true ; true", "true || false ; true", "false || false ; false", "true || false && false ; true",
         "null == null ; true", "'  spaced ' == '  spaced ' ; true" })
   void literalsCompareAndCombine(String condition, boolean held) {
      assertEquals(new CriterionResult(condition, List.of(), held), evaluate(condition));
   }

   @ParameterizedTest
   @CsvSource(delimiter = ';', quoteCharacter = '"', value = { "$statusCode == 200 ; true ; $statusCode=200",
         "$response.body#/price < 3 ; true ; $response.body#/price=2.5",
         "$response.body#/name == 'Eclair Cafe' ; true ; $response.body#/name=Eclair Cafe",
         "$response.body#/available ; true ; $response.body#/available=true",
         "$response.body#/stock == null ; true ; $response.body#/stock=null",
         "$response.body#/price == $inputs.newPrice ; false ; $response.body#/price=2.5, $inputs.newPrice=2.6",
         "  $statusCode == 404 || $response.body#/price >= 2.5  ; true ; $statusCode=200, $response.body#/price=2.5" })
   void runtimeExpressionsAreComparedAndTheirValuesKept(String condition, boolean held, String values) {
      CriterionResult result = evaluate(condition);

      assertEquals(held, result.held());
      assertEquals(values, String.join(", ",
            result.values().stream().map(value -> value.expression() + "=" + value.value()).toList()));
   }

   @Test
   void aRegexCriterionMatchesThePatternAgainstItsContext() {
      assertEquals(
            new CriterionResult("json$",
                  List.of(new ExpressionValue("$response.header.content-type", "application/json")), true),
            evaluator.evaluate(new ArazzoCriterion("json$", "$response.header.content-type", "regex"), CONTEXT));
   }

   @ParameterizedTest
   @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
         "$statusCode = 200 ; Invalid condition: $statusCode = 200",
         "$statusCode == ; Invalid condition: $statusCode ==", "true false ; Invalid condition: true false",
         "200 == abc ; Invalid condition: 200 == abc", "&& true ; Invalid condition: && true",
         "'open == 'open' ; Invalid condition: 'open == 'open'",
         "$response.body#/name ; Not a boolean: Eclair Cafe in condition: $response.body#/name",
         "$response.body#/name < 3 ; Operator < needs numbers, got: Eclair Cafe and 3" })
   void aMalformedConditionIsRefusedWithTheReason(String condition, String reason) {
      assertEquals(reason, assertThrows(ArazzoRunException.class, () -> evaluate(condition)).getMessage());
   }

   private CriterionResult evaluate(String condition) {
      return evaluator.evaluate(new ArazzoCriterion(condition, null, "simple"), CONTEXT);
   }
}
