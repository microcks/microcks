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
package io.microcks.minion.arazzo.runner;

/**
 * Why a step or a workflow cannot be executed: a runtime expression without value, a malformed condition...
 * @author SebastienDegodez
 */
public class ArazzoRunException extends RuntimeException {

   /**
    * @param message Why, shown to the API developer
    */
   public ArazzoRunException(String message) {
      super(message);
   }
}
