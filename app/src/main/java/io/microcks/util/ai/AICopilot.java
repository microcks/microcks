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
package io.github.microcks.util.ai;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;

import java.util.List;

/**
 * This is the service interface that holds the different features of AI Copilot in Microcks.
 * @author laurent
 */
public interface AICopilot {

   /**
    * Suggest/generate sample exchanges for an operation of a Service, from a specification. Depending on contract type
    * (OpenAPI, AsyncAPI, GraphQL, ...) the implementation may adapt the way it asks for generation and handle the
    * result parsing.
    * @param service   The Service to generate sample exchanges for
    * @param operation The Service operation to generate sample exchanges for
    * @param contract  The contract on which sample exchange will be based
    * @param number    The number of requested samples.
    * @return A list of exchanges, size of list may be equal of lower than number if generation is incomplete.
    * @throws Exception If generation cannot be done (parsing errors, timeout, connection issues, reached quotas, ...)
    */
   List<? extends Exchange> suggestSampleExchanges(Service service, Operation operation, Resource contract, int number)
         throws Exception;
}
