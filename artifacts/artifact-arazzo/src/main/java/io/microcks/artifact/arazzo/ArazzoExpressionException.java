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
package io.microcks.artifact.arazzo;

/**
 * Why a runtime expression has no value: an unknown input or step output, a missing response header, a JSON pointer
 * leading nowhere...
 * @author SebastienDegodez
 */
public class ArazzoExpressionException extends RuntimeException {

   /**
    * @param message Why, shown to the API developer
    */
   public ArazzoExpressionException(String message) {
      super(message);
   }
}
