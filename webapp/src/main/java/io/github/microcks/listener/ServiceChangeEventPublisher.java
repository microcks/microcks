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

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.event.ChangeType;
import io.github.microcks.event.ServiceChangeEvent;
import io.github.microcks.event.ServiceViewChangeEvent;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.service.MessageService;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.domain.ServiceView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Application event listener that send a message on Kafka topic on incoming ServiceUpdateEvent.
 * @author laurent
 */
@Component
@ConditionalOnProperty(value = "async-api.enabled", havingValue = "true", matchIfMissing = true)
public class ServiceChangeEventPublisher implements ApplicationListener<ServiceChangeEvent> {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ServiceChangeEventPublisher.class);


   private final ServiceRepository serviceRepository;
   private final MessageService messageService;
   private final ServiceChangeEventChannel channel;


   /**
    * Create a new ServiceChangeEventPublisher with required dependencies.
    * @param serviceRepository the repository for Service objects
    * @param messageService    the service for Message objects
    * @param channel           the channel for ServiceChangeEvent
    */
   public ServiceChangeEventPublisher(ServiceRepository serviceRepository, MessageService messageService,
         ServiceChangeEventChannel channel) {
      this.serviceRepository = serviceRepository;
      this.messageService = messageService;
      this.channel = channel;
   }

   @Override
   @Async
   public void onApplicationEvent(ServiceChangeEvent event) {
      log.debug("Received a ServiceChangeEvent on {}", event.getServiceId());

      ServiceView serviceView = null;
      if (event.getChangeType() != ChangeType.DELETED) {
         Service service = serviceRepository.findById(event.getServiceId()).orElse(null);

         if (service != null) {
            // Put messages into a map where key is operation name.
            Map<String, List<? extends Exchange>> messagesMap = new HashMap<>();
            for (Operation operation : service.getOperations()) {
               if (service.getType() == ServiceType.EVENT || service.getType() == ServiceType.GENERIC_EVENT) {
                  // If an event, we should explicitly retrieve event messages.
                  List<UnidirectionalEvent> events = messageService
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
