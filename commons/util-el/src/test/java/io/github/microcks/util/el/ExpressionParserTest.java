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

import io.github.microcks.util.el.function.NowELFunction;
import io.github.microcks.util.el.function.PutInContextELFunction;
import io.github.microcks.util.el.function.UUIDELFunction;

import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;

/**
 * A TestCase for ExpressionParser class.
 * @author laurent
 */
public class ExpressionParserTest {

   @Test
   public void testParseExpressions() {
      String template = "Hello {{ request.body/name}} it's {{now() }}";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("now", NowELFunction.class);
      context.setVariable("request", new EvaluableRequest("{'name': 'Laurent'}", null));

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");

      assertEquals(4, expressions.length);
      assertTrue(expressions[0] instanceof LiteralExpression);
      assertTrue(expressions[1] instanceof VariableReferenceExpression);
      assertTrue(expressions[2] instanceof LiteralExpression);
      assertTrue(expressions[3] instanceof FunctionExpression);

      assertEquals("Hello ", ((LiteralExpression) expressions[0]).getValue(context));
      assertEquals(" it's ", ((LiteralExpression) expressions[2]).getValue(context));
   }

   @Test
   public void testRedirectParseExpressions() {
      String template = "Hello {{ guid() > put(id) }} world! This is my {{ id }}";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("guid", UUIDELFunction.class);
      context.registerFunction("put", PutInContextELFunction.class);

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");

      assertEquals(4, expressions.length);
      assertTrue(expressions[0] instanceof LiteralExpression);
      assertTrue(expressions[1] instanceof RedirectExpression);
      assertTrue(expressions[2] instanceof LiteralExpression);
      assertTrue(expressions[3] instanceof VariableReferenceExpression);

      String guidValue = expressions[1].getValue(context);
      String contextValue = expressions[3].getValue(context);
      assertEquals(guidValue, contextValue);
   }

   @Test
   public void testXpathExpressionWithNestedFunction() {
      String template = "Hello {{ request.body//*[local-name() = 'name'] }} it's {{ now() }}";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("now", NowELFunction.class);
      context.setVariable("request", new EvaluableRequest(
            "<ns:request xmlns:ns=\"http://example.com/ns\"><ns:name>Laurent</ns:name></ns:request>", null));

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");

      assertEquals(4, expressions.length);
      assertTrue(expressions[0] instanceof LiteralExpression);
      assertTrue(expressions[1] instanceof VariableReferenceExpression);
      assertTrue(expressions[2] instanceof LiteralExpression);
      assertTrue(expressions[3] instanceof FunctionExpression);

      assertEquals("Laurent", ((VariableReferenceExpression) expressions[1]).getValue(context));
   }

   @Test
   public void testXpathAttributeExpressionWithNestedFunction() {
      String template = "Hello {{ request.body/request/name/@firstname }} it's {{ now() }}";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("now", NowELFunction.class);
      context.setVariable("request", new EvaluableRequest("<request><name firstname=\"Laurent\"/></request>", null));

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");

      assertEquals(4, expressions.length);
      assertTrue(expressions[0] instanceof LiteralExpression);
      assertTrue(expressions[1] instanceof VariableReferenceExpression);
      assertTrue(expressions[2] instanceof LiteralExpression);
      assertTrue(expressions[3] instanceof FunctionExpression);

      assertEquals("Laurent", ((VariableReferenceExpression) expressions[1]).getValue(context));
   }
}
