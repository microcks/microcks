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

import io.microcks.domain.EventMessage;
import io.microcks.domain.Operation;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.domain.ServiceView;
import io.microcks.minion.async.client.MicrocksTokenProvider;
import io.microcks.minion.async.client.MicrocksAPIConnector;
import io.microcks.minion.async.handler.RequestReplyHandlerManager;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Minion App for dealing with Async message mocks.
 *
 * @author laurent
 */
@ApplicationScoped
public class AsyncMinionApp {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private static final int SERVICES_FETCH_SIZE = 30;

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   @ConfigProperty(name = "minion.restricted-frequencies")
   Long[] restrictedFrequencies;

   final MicrocksAPIConnector microcksAPIConnector;
   final MicrocksTokenProvider tokenProvider;
   final AsyncMockRepository mockRepository;
   final SchemaRegistry schemaRegistry;
   final ProducerScheduler producerScheduler;
   final RequestReplyHandlerManager requestReplyHandlerManager;

   /**
    * Build a new instance of the AsyncMinionApp with required dependencies.
    *
    * @param microcksAPIConnector       to access Microcks API
    * @param tokenProvider              to reset the access token on failure
    * @param mockRepository             to store and retrieve mock definitions
    * @param schemaRegistry             to store and retrieve schema definitions
    * @param producerScheduler          to initiate producer jobs
    * @param requestReplyHandlerManager to manage request-reply handlers
    */
   public AsyncMinionApp(@RestClient MicrocksAPIConnector microcksAPIConnector, MicrocksTokenProvider tokenProvider,
         AsyncMockRepository mockRepository, SchemaRegistry schemaRegistry, ProducerScheduler producerScheduler,
         RequestReplyHandlerManager requestReplyHandlerManager) {
      this.microcksAPIConnector = microcksAPIConnector;
      this.tokenProvider = tokenProvider;
      this.mockRepository = mockRepository;
      this.schemaRegistry = schemaRegistry;
      this.producerScheduler = producerScheduler;
      this.requestReplyHandlerManager = requestReplyHandlerManager;
   }

   /**
    * Fully synchronize this minion with the Microcks API: definitions that no longer exist are dropped, existing ones
    * are reloaded and producer jobs / request-reply handlers are (re)started idempotently. Nothing is modified if
    * Microcks cannot be reached. This must be called on each (re)connection as change events are not replayed.
    */
   public synchronized void synchronize() {
      try {
         // Fetch everything first so that local state is untouched if Microcks fails midway.
         List<AsyncMockDefinition> definitions = fetchMockDefinitions();

         logger.infof("Replacing local state with %d mock definitions", definitions.size());
         mockRepository.getMocksDefinitions().stream().map(d -> d.getOwnerService().getId()).distinct().toList()
               .forEach(mockRepository::removeMockDefinitions);
         schemaRegistry.clearRegistry();
         requestReplyHandlerManager.stopAllHandlers();

         for (AsyncMockDefinition mockDefinition : definitions) {
            mockRepository.storeMockDefinition(mockDefinition);
         }
         definitions.stream().map(AsyncMockDefinition::getOwnerService).distinct()
               .forEach(schemaRegistry::updateRegistryForService);

         logger.info("Starting scheduling of all producer jobs...");
         producerScheduler.scheduleAllProducerJobs();

         logger.info("Starting all request-reply handlers...");
         requestReplyHandlerManager.startAllHandlers();

      } catch (RuntimeException e) {
         tokenProvider.reset();
         logger.error("Cannot call Microcks API to get Async APIs to mock...", e);
         throw new RuntimeException("Unable to synchronize the Minion due to connection exception", e);
      }
   }

   /** Retrieve all the mock definitions matching this minion constraints from Microcks API. */
   private List<AsyncMockDefinition> fetchMockDefinitions() {
      List<AsyncMockDefinition> definitions = new ArrayList<>();
      int page = 0;
      boolean fetchServices = true;
      while (fetchServices) {
         List<Service> services = microcksAPIConnector.listServices(page, SERVICES_FETCH_SIZE);
         for (Service service : services) {
            logger.debug("Found service " + service.getName() + " - " + service.getVersion());

            if (service.getType().equals(ServiceType.EVENT) || service.getType().equals(ServiceType.GENERIC_EVENT)) {
               // Find the operations matching this minion constraints..
               List<Operation> operations = service.getOperations().stream()
                     .filter(o -> Arrays.asList(restrictedFrequencies).contains(o.getDefaultDelay()))
                     .filter(
                           o -> o.getBindings().keySet().stream().anyMatch(Arrays.asList(supportedBindings)::contains))
                     .toList();

               if (!operations.isEmpty()) {
                  logger.info("Found " + operations.size() + " candidate operations in " + service.getName() + " - "
                        + service.getVersion());
                  ServiceView serviceView = microcksAPIConnector.getService(service.getId(), true);

                  for (Operation operation : operations) {
                     List<EventMessage> messages = AsyncMockDefinitionUpdater
                           .getMessages(serviceView.getMessagesMap().get(operation.getName()));
                     definitions.add(new AsyncMockDefinition(serviceView.getService(), operation, messages));
                  }
               }
            }
         }
         if (services.size() < SERVICES_FETCH_SIZE) {
            fetchServices = false;
         }
         page++;
      }
      return definitions;
   }
}
