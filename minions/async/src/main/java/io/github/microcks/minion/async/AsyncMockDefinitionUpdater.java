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
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.UnidirectionalEvent;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;

@ApplicationScoped
/**
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
   public void onServiceUpdate(ServiceView serviceView) {
      logger.info("Received a new Service update for '" + serviceView.getService().getName() + " - " + serviceView.getService().getVersion() + "'");

      // Only deal with service of type EVENT...
      if (serviceView.getService().getType().equals(ServiceType.EVENT)) {

         // Browse and check operation regarding restricted frequencies and supported bindings.
         for (Operation operation : serviceView.getService().getOperations()) {
            if (Arrays.asList(restrictedFrequencies).contains(operation.getDefaultDelay())
                  && operation.getBindings().keySet().stream().anyMatch(Arrays.asList(supportedBindings)::contains)) {

               logger.info("Found '" + operation.getName() + "' as a candidate for async message mocking");
               // Build an Async mock definition and store it into repository.
               AsyncMockDefinition mockDefinition = new AsyncMockDefinition(serviceView.getService(), operation,
                     serviceView.getMessagesMap().get(operation.getName()).stream()
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
