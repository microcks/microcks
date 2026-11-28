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
 * Refuses an Arazzo document that is invalid Arazzo, or that uses a version or a feature outside the subset Microcks
 * supports.
 * @author SebastienDegodez
 */
public class UnsupportedArazzoDocumentException extends Exception {

   /**
    * Build the refusal with its reason.
    * @param reason Why the document is refused, shown to the API developer
    */
   public UnsupportedArazzoDocumentException(String reason) {
      super(reason);
   }
}
