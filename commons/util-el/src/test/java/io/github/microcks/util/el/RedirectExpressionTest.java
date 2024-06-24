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

import io.github.microcks.util.el.function.PutInContextELFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for RedirectExpression class.
 * @author laurent
 */
class RedirectExpressionTest {

   @Test
   void testLiteralRedirect() {
      Expression[] expressions = new Expression[] { new LiteralExpression("hello"), new LiteralExpression("world") };

      RedirectExpression exp = new RedirectExpression(expressions);
      String result = exp.getValue(new EvaluationContext());
      assertEquals("hello", result);
   }

   @Test
   void testLiteralRedirectToContext() {
      EvaluationContext context = new EvaluationContext();

      Expression[] expressions = new Expression[] { new LiteralExpression("hello"),
            new FunctionExpression(new PutInContextELFunction(), new String[] { "greeting" }) };

      RedirectExpression exp = new RedirectExpression(expressions);
      String result = exp.getValue(context);
      assertEquals("hello", result);
      assertEquals("hello", context.lookupVariable("greeting"));
   }

   @Test
   void testLiteralRedirectToMultiContext() {
      EvaluationContext context = new EvaluationContext();

      Expression[] expressions = new Expression[] { new LiteralExpression("hello"),
            new FunctionExpression(new PutInContextELFunction(), new String[] { "greeting1" }),
            new FunctionExpression(new PutInContextELFunction(), new String[] { "greeting2" }) };

      RedirectExpression exp = new RedirectExpression(expressions);
      String result = exp.getValue(context);
      assertEquals("hello", result);
      assertEquals("hello", context.lookupVariable("greeting1"));
      assertEquals("hello", context.lookupVariable("greeting2"));
   }
}
