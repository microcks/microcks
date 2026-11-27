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
package io.microcks.minion.async;

import io.microcks.event.AsyncAPITriggerCommand;
import io.microcks.minion.async.producer.ProducerManager;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * This bean is responsible for applying the <code>AsyncAPITriggerCommand</code> received from Microcks
 * <code>/api/asyncapi-triggers</code> WebSocket endpoint and triggering the production of asynchronous messages
 * accordingly.
 * @author laurent
 */
@ApplicationScoped
public class AsyncMockProducerTrigger {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private final ProducerManager producerManager;

   /**
    * Creates a new AsyncMockProducerTrigger with required dependencies.
    * @param producerManager The producer manager to use for producing messages
    */
   public AsyncMockProducerTrigger(ProducerManager producerManager) {
      this.producerManager = producerManager;
   }

   public void applyAsyncAPITriggerCommand(AsyncAPITriggerCommand asyncAPITriggerCommand) {
      producerManager.triggerAsyncMockMessages(asyncAPITriggerCommand);
   }
}
