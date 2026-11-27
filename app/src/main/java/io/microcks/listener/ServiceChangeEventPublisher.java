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

import io.microcks.domain.Exchange;
import io.microcks.domain.Operation;
import io.microcks.domain.RequestResponsePair;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.event.ChangeType;
import io.microcks.event.ServiceChangeEvent;
import io.microcks.event.ServiceViewChangeEvent;
import io.microcks.repository.ServiceRepository;
import io.microcks.service.MessageService;
import io.microcks.util.IdBuilder;
import io.microcks.domain.ServiceView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.enterprise.inject.Instance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application event listener that send a message on event publication channel on incoming ServiceUpdateEvent.
 * @author laurent
 */
@ApplicationScoped
public class ServiceChangeEventPublisher {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ServiceChangeEventPublisher.class);


   private final ServiceRepository serviceRepository;
   private final MessageService messageService;
   private final Instance<EventPublicationChannel> channels;
   private final boolean enabled;


   /**
    * Create a new ServiceChangeEventPublisher with required dependencies.
    * @param serviceRepository the repository for Service objects
    * @param messageService    the service for Message objects
    * @param channels          the channel for ServiceChangeEvent
    * @param enabled           whether AsyncAPI support is enabled
    */
   public ServiceChangeEventPublisher(ServiceRepository serviceRepository, MessageService messageService,
         Instance<EventPublicationChannel> channels,
         @ConfigProperty(name = "async-api.enabled", defaultValue = "true") boolean enabled) {
      this.serviceRepository = serviceRepository;
      this.messageService = messageService;
      this.channels = channels;
      this.enabled = enabled;
   }

   public void onApplicationEvent(@ObservesAsync ServiceChangeEvent event) {
      if (!enabled || channels.isUnsatisfied()) {
         return;
      }
      EventPublicationChannel channel = channels.get();
      log.debug("Received a ServiceChangeEvent on {}", event.getServiceId());

      ServiceView serviceView = null;
      if (event.getChangeType() != ChangeType.DELETED) {
         Service service = serviceRepository.findById(event.getServiceId());

         if (service != null) {
            // Put messages into a map where key is operation name.
            Map<String, List<? extends Exchange>> messagesMap = new HashMap<>();
            for (Operation operation : service.getOperations()) {
               if (service.getType() == ServiceType.EVENT || service.getType() == ServiceType.GENERIC_EVENT) {
                  // If an event, we should explicitly retrieve event messages.
                  List<? extends Exchange> events = messageService
                        .getEventByOperation(IdBuilder.buildOperationId(service, operation));
                  messagesMap.put(operation.getName(), events);
               } else {
                  // Otherwise we have traditional request / response pairs.
                  List<RequestResponsePair> pairs = messageService
                        .getRequestResponseByOperation(IdBuilder.buildOperationId(service, operation));
                  messagesMap.put(operation.getName(), pairs);
               }
            }

            serviceView = new ServiceView(service, messagesMap);
         }
      }

      // Build and send a ServiceViewChangeEvent that wraps ServiceView.
      ServiceViewChangeEvent serviceViewChangeEvent = new ServiceViewChangeEvent(event.getServiceId(), serviceView,
            event.getChangeType(), System.currentTimeMillis());
      try {
         channel.sendServiceViewChangeEvent(serviceViewChangeEvent);
         log.debug("Processing of ServiceChangeEvent done !");
      } catch (Exception e) {
         // This is best effort sending, just log the exception.
         log.error("Failed sending ServiceChangeEvent to correct channel", e);
      }
   }
}
