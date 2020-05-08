/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util.el;

import io.github.microcks.util.el.function.NowELFunction;
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
      context.setVariable("request", new EvaluableRequest("{'name'= 'Laurent'}", null));

      Expression[] expressions = ExpressionParser.parseExpressions(template, context, "{{", "}}");

      assertEquals(4, expressions.length);
      assertTrue(expressions[0] instanceof LiteralExpression);
      assertTrue(expressions[1] instanceof VariableReferenceExpression);
      assertTrue(expressions[2] instanceof LiteralExpression);
      assertTrue(expressions[3] instanceof FunctionExpression);

      assertEquals("Hello ", ((LiteralExpression)expressions[0]).getValue(context));
      assertEquals(" it's ", ((LiteralExpression)expressions[2]).getValue(context));
   }
}
