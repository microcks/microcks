/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.listener;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.event.ServiceUpdateEvent;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.service.MessageService;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.domain.ServiceView;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.kafka.core.KafkaTemplate;
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
@ConditionalOnProperty(value="async-api.enabled", havingValue="true", matchIfMissing=true)
public class EventServiceUpdatePublisher implements ApplicationListener<ServiceUpdateEvent> {

   /** A commons logger for diagnostic messages. */
   private static Log log = LogFactory.getLog(EventServiceUpdatePublisher.class);

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private MessageService messageService;

   @Autowired
   private KafkaTemplate<String, ServiceView> kafkaTemplate;

   @Override
   @Async
   public void onApplicationEvent(ServiceUpdateEvent event) {
      log.debug("Received a ServiceUpdateEvent on " + event.getServiceId());
      Service service = serviceRepository.findById(event.getServiceId()).orElse(null);

      if (service != null) {
         // Put messages into a map where key is operation name.
         Map<String, List<? extends Exchange>> messagesMap = new HashMap<>();
         for (Operation operation : service.getOperations()) {
            if (service.getType() == ServiceType.EVENT) {
               // If an event, we should explicitly retrieve event messages.
               List<UnidirectionalEvent> events = messageService.getEventByOperation(
                     IdBuilder.buildOperationId(service, operation));
               messagesMap.put(operation.getName(), events);
            } else {
               // Otherwise we have traditional request / response pairs.
               List<RequestResponsePair> pairs = messageService.getRequestResponseByOperation(
                     IdBuilder.buildOperationId(service, operation));
               messagesMap.put(operation.getName(), pairs);
            }
         }

         ServiceView serviceView = new ServiceView(service, messagesMap);
         kafkaTemplate.send("microcks-services-updates", service.getId(), serviceView);
         log.debug("Processing of ServiceUpdateEvent done !");
      }
   }
}
