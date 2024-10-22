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

import io.github.microcks.util.el.function.ELFunction;

/**
 * An implementation of {@code Expression} that invokes an {@code ELFunction}
 * @author laurent
 */
public class FunctionExpression implements Expression {

   private final ELFunction function;
   private final String[] functionArgs;
   private final boolean hasEscapeBraces;

   /**
    * Build a new function expression with a function and its invocation arguments.
    * @param function     The ELFunction associated to this expression
    * @param functionArgs The invocation arguments of this function
    */
   public FunctionExpression(ELFunction function, String[] functionArgs, boolean hasEscapeBraces) {
      this.function = function;
      this.functionArgs = functionArgs;
      this.hasEscapeBraces = hasEscapeBraces;
   }

   @Override
   public String getValue(EvaluationContext context) {
      if (hasEscapeBraces) {
         return "{" + function.evaluate(context, functionArgs) + "}";
      }
      return function.evaluate(context, functionArgs);
   }

   public ELFunction getFunction() {
      return function;
   }

   public String[] getFunctionArgs() {
      return functionArgs;
   }

   public boolean hasEscapeBraces() {
      return hasEscapeBraces;
   }
}
