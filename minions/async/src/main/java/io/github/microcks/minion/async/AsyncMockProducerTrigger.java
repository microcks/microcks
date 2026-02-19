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
package io.github.microcks.minion.async;

import io.github.microcks.event.AsyncAPITriggerCommand;
import io.github.microcks.minion.async.producer.ProducerManager;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

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

   @Incoming("microcks-asyncapi-triggers")
   public void onAsyncAPITriggerCommand(AsyncAPITriggerCommand asyncAPITriggerCommand) {
      logger.debugf("Received AsyncAPI trigger command for service %s", asyncAPITriggerCommand.getServiceId());
      producerManager.triggerAsyncMockMessages(asyncAPITriggerCommand);
   }
}
