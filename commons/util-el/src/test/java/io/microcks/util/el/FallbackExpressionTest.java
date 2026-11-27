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

package io.github.microcks.util.el;

import io.github.microcks.util.el.function.RandomBooleanELFunction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for FallbackExpression class.
 * @author laurent
 */
class FallbackExpressionTest {

   @Test
   void testLiteralFallback() {
      Expression[] expressions = new Expression[] { new LiteralExpression("hello"), new LiteralExpression("world") };

      FallbackExpression exp = new FallbackExpression(expressions);
      String result = exp.getValue(new EvaluationContext());
      assertEquals("hello", result);
   }

   @Test
   void testVariableReferenceFallbackToRandom() {
      // First case: the first expression is not null or empty.
      String jsonString = """
            {
                "foo": "Foo",
                "bar": "Bar"
            }
            """;
      EvaluableRequest request = new EvaluableRequest(jsonString, null);

      Expression[] expressions = new Expression[] { new VariableReferenceExpression(request, "body/bar"),
            new FunctionExpression(new RandomBooleanELFunction(), new String[] {}) };

      FallbackExpression exp = new FallbackExpression(expressions);
      String result = exp.getValue(new EvaluationContext());
      assertEquals("Bar", result);

      // Second case: the first expression is null or empty (eg: no 'bar' field in JSON).
      jsonString = """
            {
                "foo": "Foo"
            }
            """;
      request = new EvaluableRequest(jsonString, null);

      expressions = new Expression[] { new VariableReferenceExpression(request, "body/bar"),
            new FunctionExpression(new RandomBooleanELFunction(), new String[] {}) };

      exp = new FallbackExpression(expressions);
      result = exp.getValue(new EvaluationContext());
      assertTrue(result.equals("true") || result.equals("false"));
   }
}
