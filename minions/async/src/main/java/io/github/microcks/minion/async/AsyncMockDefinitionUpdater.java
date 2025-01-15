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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.event.ChangeType;
import io.github.microcks.event.ServiceViewChangeEvent;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.Arrays;

/**
 * This bean is responsible for listening the incoming <code>ServiceViewChangeEvent</code> on
 * <code>microcks-services-updates</code> Kafka topic and updating the AsyncMockRepository accordingly.
 * @author laurent
 */
@ApplicationScoped
public class AsyncMockDefinitionUpdater {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   @ConfigProperty(name = "minion.restricted-frequencies")
   Long[] restrictedFrequencies;

   final AsyncMockRepository mockRepository;
   final SchemaRegistry schemaRegistry;

   /**
    * Create a new AsyncMockDefinitionUpdater with required dependencies.
    * @param mockRepository The repository for mock definitions
    * @param schemaRegistry The registry for schema definitions
    */
   public AsyncMockDefinitionUpdater(AsyncMockRepository mockRepository, SchemaRegistry schemaRegistry) {
      this.mockRepository = mockRepository;
      this.schemaRegistry = schemaRegistry;
   }

   @Incoming("microcks-services-updates")
   public void onServiceUpdate(ServiceViewChangeEvent serviceViewChangeEvent) {
      logger.infof("Received a new change event [%s] for '%s', at %d", serviceViewChangeEvent.getChangeType(),
            serviceViewChangeEvent.getServiceId(), serviceViewChangeEvent.getTimestamp());

      applyServiceChangeEvent(serviceViewChangeEvent);
   }

   public void applyServiceChangeEvent(ServiceViewChangeEvent serviceViewChangeEvent) {
      // Remove existing definitions or add/update existing for EVENT services.
      if (serviceViewChangeEvent.getChangeType().equals(ChangeType.DELETED)) {
         logger.infof("Removing mock definitions for %s", serviceViewChangeEvent.getServiceId());
         mockRepository.removeMockDefinitions(serviceViewChangeEvent.getServiceId());
         schemaRegistry.clearRegistryForService(serviceViewChangeEvent.getServiceId());
      } else {
         // Only deal with service of type EVENT...
         if (serviceViewChangeEvent.getServiceView() != null && (serviceViewChangeEvent.getServiceView().getService()
               .getType().equals(ServiceType.EVENT)
               || serviceViewChangeEvent.getServiceView().getService().getType().equals(ServiceType.GENERIC_EVENT))) {

            // Browse and check operation regarding restricted frequencies and supported bindings.
            boolean scheduled = scheduleOperations(serviceViewChangeEvent.getServiceView());

            if (!scheduled) {
               logger.infof("Ensure to un-schedule %s on this minion. Removing definitions.",
                     serviceViewChangeEvent.getServiceId());
               mockRepository.removeMockDefinitions(serviceViewChangeEvent.getServiceId());
               schemaRegistry.clearRegistryForService(serviceViewChangeEvent.getServiceId());
            }
         }
      }
   }

   /** Browse and check operation regarding restricted frequencies and supported bindings. */
   private boolean scheduleOperations(ServiceView serviceView) {
      boolean scheduled = false;
      for (Operation operation : serviceView.getService().getOperations()) {
         if (Arrays.asList(restrictedFrequencies).contains(operation.getDefaultDelay())
               && operation.getBindings().keySet().stream().anyMatch(Arrays.asList(supportedBindings)::contains)) {

            logger.info("Found '" + operation.getName() + "' as a candidate for async message mocking");
            // Build an Async mock definition and store it into repository.
            AsyncMockDefinition mockDefinition = new AsyncMockDefinition(serviceView.getService(), operation,
                  serviceView.getMessagesMap().get(operation.getName()).stream()
                        .filter(UnidirectionalEvent.class::isInstance)
                        .map(e -> ((UnidirectionalEvent) e).getEventMessage()).toList());
            mockRepository.storeMockDefinition(mockDefinition);
            schemaRegistry.updateRegistryForService(mockDefinition.getOwnerService());
            scheduled = true;
         }
      }
      return scheduled;
   }
}
