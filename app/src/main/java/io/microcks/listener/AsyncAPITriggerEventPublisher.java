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
package io.microcks.listener;

import io.microcks.event.AsyncAPITriggerCommand;
import io.microcks.event.AsyncAPITriggerEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;

/**
 * Application event listener that send a message on event publication channel on incoming AsyncAPITriggerEvent.
 * @author laurent
 */
@ApplicationScoped
public class AsyncAPITriggerEventPublisher {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AsyncAPITriggerEventPublisher.class);


   private final Instance<EventPublicationChannel> channels;
   private final boolean enabled;


   public AsyncAPITriggerEventPublisher(Instance<EventPublicationChannel> channels,
         @ConfigProperty(name = "async-api.enabled", defaultValue = "true") boolean enabled) {
      this.channels = channels;
      this.enabled = enabled;
   }

   public void onApplicationEvent(@ObservesAsync AsyncAPITriggerEvent event) {
      if (!enabled || channels.isUnsatisfied()) {
         return;
      }
      EventPublicationChannel channel = channels.get();
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
