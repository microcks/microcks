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
package io.github.microcks.minion.async;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.event.ChangeType;
import io.github.microcks.event.ServiceViewChangeEvent;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;

@ApplicationScoped
/**
 * This bean is responsible for listening the incoming <code>ServiceViewChangeEvent</code> on
 * <code>microcks-services-updates</code> Kafka topic and updating the AsyncMockRepository accordingly.
 * @author laurent
 */
public class AsyncMockDefinitionUpdater {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   @ConfigProperty(name= "minion.restricted-frequencies")
   Long[] restrictedFrequencies;

   @Inject
   AsyncMockRepository mockRepository;

   @Incoming("microcks-services-updates")
   public void onServiceUpdate(ServiceViewChangeEvent serviceViewChangeEvent) {
      logger.info("Received a new change event [" + serviceViewChangeEvent.getChangeType() + "] for '"
            + serviceViewChangeEvent.getServiceId() + "', at " + serviceViewChangeEvent.getTimestamp());

      // Remove existing definitions or add/update existing for EVENT services.
      if (serviceViewChangeEvent.getChangeType().equals(ChangeType.DELETED)) {
         logger.info("Removing mock definitions for " + serviceViewChangeEvent.getServiceId());
         mockRepository.removeMockDefinitions(serviceViewChangeEvent.getServiceId());
      } else {
         // Only deal with service of type EVENT...
         if (serviceViewChangeEvent.getServiceView() != null && serviceViewChangeEvent.getServiceView().getService().getType().equals(ServiceType.EVENT)) {
            // Browse and check operation regarding restricted frequencies and supported bindings.
            for (Operation operation : serviceViewChangeEvent.getServiceView().getService().getOperations()) {
               if (Arrays.asList(restrictedFrequencies).contains(operation.getDefaultDelay())
                     && operation.getBindings().keySet().stream().anyMatch(Arrays.asList(supportedBindings)::contains)) {

                  logger.info("Found '" + operation.getName() + "' as a candidate for async message mocking");
                  // Build an Async mock definition and store it into repository.
                  AsyncMockDefinition mockDefinition = new AsyncMockDefinition(serviceViewChangeEvent.getServiceView().getService(), operation,
                        serviceViewChangeEvent.getServiceView().getMessagesMap().get(operation.getName()).stream()
                              .filter(e -> e instanceof UnidirectionalEvent)
                              .map(e -> ((UnidirectionalEvent)e).getEventMessage())
                              .collect(Collectors.toList())
                  );
                  mockRepository.storeMockDefinition(mockDefinition);
               }
            }
         }
      }
   }
}
