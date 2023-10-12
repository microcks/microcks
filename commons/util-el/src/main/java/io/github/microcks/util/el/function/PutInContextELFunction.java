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
package io.github.microcks.util.el.function;

import io.github.microcks.util.el.EvaluationContext;

/**
 * Implementation of ELFunction that puts a previous result into an evaluation context variable.
 * @author laurent
 */
public class PutInContextELFunction implements ELFunction {

   @Override
   public String evaluate(EvaluationContext evaluationContext, String... args) {
      if (args != null && args.length > 1) {
         evaluationContext.setVariable(args[0], args[1]);
      }
      return "";
   }
}
