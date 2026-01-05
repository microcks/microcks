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

/**
 * An engine that can evaluate String templates holding some Expression Language {@code Expression}. Engine can be
 * customized setting new expression delimiters (prefix and suffix). It embeds an {@code EvaluationContext} that can
 * also be customized with variables and function registration before template evaluation using the {@code getValue()}
 * method.
 * @author laurent
 */
public class TemplateEngine {

   public static final String DEFAULT_EXPRESSION_PREFIX = "{{";
   public static final String DEFAULT_EXPRESSION_SUFFIX = "}}";

   private String expressionPrefix = DEFAULT_EXPRESSION_PREFIX;
   private String expressionSuffix = DEFAULT_EXPRESSION_SUFFIX;

   private final EvaluationContext context = new EvaluationContext();

   protected TemplateEngine() {
   }

   public String getExpressionPrefix() {
      return expressionPrefix;
   }

   public void setExpressionPrefix(String expressionPrefix) {
      this.expressionPrefix = expressionPrefix;
   }

   public String getExpressionSuffix() {
      return expressionSuffix;
   }

   public void setExpressionSuffix(String expressionSuffix) {
      this.expressionSuffix = expressionSuffix;
   }

   public EvaluationContext getContext() {
      return context;
   }

   /**
    * Evaluate the given string template, finding expressions within and evaluating them.
    * @param template The string template to render.
    * @return The rendered value of string template.
    */
   public String getValue(String template) {
      StringBuilder builder = new StringBuilder();

      // Just delegate parsing stuffs to Expression parser to retrieve all the expressions ordered.
      Expression[] expressions = ExpressionParser.parseExpressions(template, context, expressionPrefix,
            expressionSuffix);

      // Now just go through expressions and evaluate them.
      for (Expression expression : expressions) {
         builder.append(expression.getValue(context));
      }

      return builder.toString();
   }
}
