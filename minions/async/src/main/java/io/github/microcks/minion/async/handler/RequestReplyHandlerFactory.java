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
package io.github.microcks.minion.async.handler;

import io.github.microcks.domain.Binding;
import io.github.microcks.minion.async.AsyncMockDefinition;

/**
 * Factory interface for creating protocol-specific request-reply handlers. Each protocol implementation should provide
 * a CDI bean implementing this interface, annotated with the appropriate qualifier to indicate its supported binding
 * type.
 *
 * @author rootp1
 */
public interface RequestReplyHandlerFactory {

   /**
    * Create a request-reply handler for the given mock definition and binding.
    *
    * @param definition The mock definition to create a handler for
    * @param binding    The protocol binding information
    * @return A new RequestReplyHandler instance, or null if the factory cannot create a handler for the given binding
    */
   RequestReplyHandler createHandler(AsyncMockDefinition definition, Binding binding);
}
