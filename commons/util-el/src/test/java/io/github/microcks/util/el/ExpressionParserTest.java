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
import io.github.microcks.util.el.function.RandomBooleanELFunction;
import io.github.microcks.util.el.function.UUIDELFunction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * A TestCase for ExpressionParser class.
 * @author laurent
 */
class ExpressionParserTest {

   @Test
   void testParseExpressions() {
      String template = "Hello {{ request.body/name}} it's {{now() }}";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("now", NowELFunction.class);
      context.setVariable("request", new EvaluableRequest("{'name': 'Laurent'}", null));

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");

      assertEquals(4, expressions.length);
      assertInstanceOf(LiteralExpression.class, expressions[0]);
      assertInstanceOf(VariableReferenceExpression.class, expressions[1]);
      assertInstanceOf(LiteralExpression.class, expressions[2]);
      assertInstanceOf(FunctionExpression.class, expressions[3]);

      assertEquals("Hello ", ((LiteralExpression) expressions[0]).getValue(context));
      assertEquals(" it's ", ((LiteralExpression) expressions[2]).getValue(context));
   }

   @Test
   void testRedirectParseExpressions() {
      String template = "Hello {{ guid() > put(id) }} world! This is my {{ id }}";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("guid", UUIDELFunction.class);
      context.registerFunction("put", PutInContextELFunction.class);

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");

      assertEquals(4, expressions.length);
      assertInstanceOf(LiteralExpression.class, expressions[0]);
      assertInstanceOf(RedirectExpression.class, expressions[1]);
      assertInstanceOf(LiteralExpression.class, expressions[2]);
      assertInstanceOf(VariableReferenceExpression.class, expressions[3]);

      String guidValue = expressions[1].getValue(context);
      String contextValue = expressions[3].getValue(context);
      assertEquals(guidValue, contextValue);
   }

   @Test
   void testFallbackParseExpression() {
      String template = "Bar value: {{ request.body/bar || randomBoolean() }}";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("randomBoolean", RandomBooleanELFunction.class);
      context.setVariable("request", new EvaluableRequest("{\"foo\": \"Foo\"}", null));

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");

      assertEquals(2, expressions.length);
      assertInstanceOf(LiteralExpression.class, expressions[0]);
      assertInstanceOf(FallbackExpression.class, expressions[1]);

      assertEquals("Bar value: ", expressions[0].getValue(context));
      String barValue = expressions[1].getValue(context);
      assertTrue("true".equals(barValue) || "false".equals(barValue));
   }

   @Test
   void testFallbackWithLiteralParseExpression() {
      String template = "result: {{ request.body/key || '[]'}}";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.setVariable("request", new EvaluableRequest("{\"key\": \"value\"}", null));

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");
      assertEquals(2, expressions.length);
      assertInstanceOf(LiteralExpression.class, expressions[0]);
      assertInstanceOf(FallbackExpression.class, expressions[1]);

      // Check value of fallback expression.
      FallbackExpression fbe = (FallbackExpression) expressions[1];
      assertEquals("value", fbe.getValue(context));

      // Now change request to not have 'key' and check fallback to literal.
      context.setVariable("request", new EvaluableRequest("{\"otherKey\": \"value\"}", null));
      expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");
      fbe = (FallbackExpression) expressions[1];
      assertEquals("[]", fbe.getValue(context));
   }

   @Test
   void testXpathExpressionWithNestedFunction() {
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
   void testXpathAttributeExpressionWithNestedFunction() {
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

   @Test
   void testParseExpressionWithEscapeCharacter() {
      String template1 = "id : {{{uuid()}}}";
      String template2 = "name : #{{request.body/name}}#";

      // Build a suitable context.
      EvaluationContext context = new EvaluationContext();
      context.registerFunction("uuid", UUIDELFunction.class);
      context.setVariable("request", new EvaluableRequest("{\"name\": \"Laurent\"}", null));
      Expression[] expressions;

      //Test for template1
      expressions = ExpressionParser.parseExpressions(template1, context, "{{", "}}");
      assertEquals(4, expressions.length);
      assertInstanceOf(LiteralExpression.class, expressions[0]);
      assertInstanceOf(LiteralExpression.class, expressions[1]);
      assertInstanceOf(FunctionExpression.class, expressions[2]);
      assertInstanceOf(LiteralExpression.class, expressions[3]);

      assertEquals("{", ((LiteralExpression) expressions[1]).getValue(context));
      assertEquals("}", ((LiteralExpression) expressions[3]).getValue(context));

      //Test for template2
      expressions = ExpressionParser.parseExpressions(template2, context, "{{", "}}");
      assertEquals(3, expressions.length);
      assertInstanceOf(LiteralExpression.class, expressions[0]);
      assertInstanceOf(VariableReferenceExpression.class, expressions[1]);
      assertInstanceOf(LiteralExpression.class, expressions[2]);

      assertEquals("name : #", ((LiteralExpression) expressions[0]).getValue(context));
      assertEquals("#", ((LiteralExpression) expressions[2]).getValue(context));
   }
}
