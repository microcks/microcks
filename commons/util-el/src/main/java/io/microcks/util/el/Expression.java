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
 * Simple interface representing an EL expression. Expression just render a value regarding a specific
 * {@code EvaluationContext}.
 * @author laurent
 */
public interface Expression {

   /**
    * Render this expression value within this evaluation context.
    * @param context The context of current evaluation
    * @return This expression rendered value
    */
   String getValue(EvaluationContext context);
}
