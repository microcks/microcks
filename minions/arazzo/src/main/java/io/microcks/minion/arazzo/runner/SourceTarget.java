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

import java.net.URI;
import java.util.Map;

/**
 * Where the steps calling a source description send their requests.
 * @param baseUrl    The base URL the paths of the operations are appended to
 * @param operations The method and path of each operation of the source, by operationId
 * @param mocks      Whether the source is played on the Microcks mocks: the secret of a run authenticates the calls
 *                   to implementations only, never those to the mocks
 * @author SebastienDegodez
 */
public record SourceTarget(URI baseUrl, Map<String, OperationTarget> operations, boolean mocks) {

   /**
    * A source played on an implementation.
    * @param baseUrl    The base URL of the implementation
    * @param operations The method and path of each operation of the source, by operationId
    */
   public SourceTarget(URI baseUrl, Map<String, OperationTarget> operations) {
      this(baseUrl, operations, false);
   }

   /**
    * @param baseUrl    The base URL of the mocks of the source in Microcks
    * @param operations The method and path of each operation of the source, by operationId
    * @return A source played on the Microcks mocks
    */
   public static SourceTarget onMocks(URI baseUrl, Map<String, OperationTarget> operations) {
      return new SourceTarget(baseUrl, operations, true);
   }
}
