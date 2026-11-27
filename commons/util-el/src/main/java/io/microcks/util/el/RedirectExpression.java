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

import java.util.Arrays;

/**
 * An implementation of {@code Expression} that redirects the result of a first expression to other ones among a list.
 * If additional expressions are {@code FunctionExpression}, result is appended to the list of arguments before
 * expression invocation.
 * @author laurent
 */
public class RedirectExpression implements Expression {

   public static final char REDIRECT_MARKER = '>';

   public static final String REDIRECT_MARKER_SPLIT_REGEX = "\\>";

   private final Expression[] expressions;

   public RedirectExpression(Expression[] expressions) {
      this.expressions = expressions;
   }

   @Override
   public String getValue(EvaluationContext context) {
      String result = null;
      if (expressions.length > 0) {
         // Execute first expression for getting result.
         result = expressions[0].getValue(context);
         for (int i = 1; i < expressions.length; i++) {
            Expression exp = expressions[i];
            if (exp instanceof FunctionExpression functionExp) {
               // Clone this expression, enriching args with previous result.
               String[] clonedArgs = Arrays.copyOf(functionExp.getFunctionArgs(),
                     functionExp.getFunctionArgs().length + 1);
               clonedArgs[clonedArgs.length - 1] = result;
               FunctionExpression clonedExp = new FunctionExpression(functionExp.getFunction(), clonedArgs);
               clonedExp.getValue(context);
            }
         }
      }
      return result;
   }
}
