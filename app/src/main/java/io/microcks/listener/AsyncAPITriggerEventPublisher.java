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
package io.github.microcks.listener;

import io.github.microcks.event.AsyncAPITriggerCommand;
import io.github.microcks.event.AsyncAPITriggerEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Application event listener that send a message on event publication channel on incoming AsyncAPITriggerEvent.
 * @author laurent
 */
@Component
@ConditionalOnProperty(value = "async-api.enabled", havingValue = "true", matchIfMissing = true)
public class AsyncAPITriggerEventPublisher implements ApplicationListener<AsyncAPITriggerEvent> {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AsyncAPITriggerEventPublisher.class);


   private final EventPublicationChannel channel;


   public AsyncAPITriggerEventPublisher(EventPublicationChannel channel) {
      this.channel = channel;
   }

   @Override
   public void onApplicationEvent(AsyncAPITriggerEvent event) {
      log.debug("Received a AsyncAPITriggerEvent on {}", event.getServiceId());

      // Build and send an AsyncAPITriggerCommand that wraps all elements.
      AsyncAPITriggerCommand command = new AsyncAPITriggerCommand(event.getServiceId(), event.getOperation(),
            event.getRequest(), event.getResponse(), System.currentTimeMillis());

      try {
         channel.sendAsyncAPITriggerCommand(command);
         log.debug("Processing of AsyncAPITriggerEvent done !");
      } catch (Exception e) {
         // This is best effort sending, just log the exception.
         log.error("Failed sending AsyncAPITriggerEvent to correct channel", e);
      }
   }
}
