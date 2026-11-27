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

import io.github.microcks.minion.async.AsyncMockDefinition;

/**
 * Interface for request-reply handlers that listen for incoming request messages and send correlated replies. Each
 * handler manages a single request-reply operation for a specific protocol binding.
 *
 * @author adamhicks
 */
public interface RequestReplyHandler {

   /**
    * Start the handler to begin listening for request messages.
    *
    * @throws Exception if the handler cannot be started
    */
   void start() throws Exception;

   /**
    * Stop the handler and clean up all resources.
    */
   void stop();

   /**
    * Check if the handler is currently running.
    *
    * @return true if the handler is running, false otherwise
    */
   boolean isRunning();

   /**
    * Get the mock definition this handler is managing.
    *
    * @return the AsyncMockDefinition
    */
   AsyncMockDefinition getMockDefinition();
}
