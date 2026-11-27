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
 * An implementation of {@code Expression} that returns the result of the first expression that is not null or empty.
 * @author laurent
 */
public class FallbackExpression implements Expression {

   public static final String FALLBACK_MARKER = "||";

   public static final String FALLBACK_MARKER_SPLIT_REGEX = "\\|\\|";

   private final Expression[] expressions;

   public FallbackExpression(Expression[] expressions) {
      this.expressions = expressions;
   }

   @Override
   public String getValue(EvaluationContext context) {
      String result = null;
      for (Expression expression : expressions) {
         result = expression.getValue(context);
         if (result != null && !result.isEmpty() && !"null".equals(result)) {
            break;
         }
      }
      return result;
   }
}
