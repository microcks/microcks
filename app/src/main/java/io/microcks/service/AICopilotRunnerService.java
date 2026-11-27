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

package io.microcks.service;

import io.microcks.domain.Exchange;
import io.microcks.domain.Operation;
import io.microcks.domain.Resource;
import io.microcks.domain.Service;
import io.microcks.security.UserInfo;
import io.microcks.util.ai.AICopilot;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A service that runs the AI Copilot on asynchronous/long-running tasks.
 * @author laurent
 */
@ApplicationScoped
public class AICopilotRunnerService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AICopilotRunnerService.class);

   private AICopilot copilot;
   private final ServiceService serviceService;


   /**
    * Build a new AICopilotRunnerService with the required dependencies.
    * @param serviceService The service service.
    * @param copilot        The optional AI Copilot
    */
   @Inject
   public AICopilotRunnerService(ServiceService serviceService, Instance<AICopilot> copilot) {
      this.serviceService = serviceService;
      if (copilot.isResolvable()) {
         this.copilot = copilot.get();
      }
   }

   /**
    * Generate samples for all operations of a service using asynchronous/completable future pattern.
    * @param service  The service to generate samples for.
    * @param contract The contract to generate samples for.
    * @param userInfo The user information for user having launched the generation.
    * @return A Future wrapping generation success flag.
    */
   public CompletableFuture<Boolean> generateSamplesForService(Service service, Resource contract, UserInfo userInfo) {
      return CompletableFuture.supplyAsync(() -> generateSamples(service, contract, userInfo));
   }

   private boolean generateSamples(Service service, Resource contract, UserInfo userInfo) {
      for (Operation operation : service.getOperations()) {
         try {
            List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation, contract, 3);
            if (exchanges == null || exchanges.isEmpty()) {
               log.warn("No AI Copilot samples were generated for service {} operation {}", service.getName(),
                     operation.getName());
               continue;
            }
            serviceService.addAICopilotExchangesToServiceOperation(service.getId(), operation.getName(),
                  (List<Exchange>) exchanges, userInfo);
         } catch (Exception e) {
            log.error("Caught an exception while generating samples for service {} operation {}", service.getName(),
                  operation.getName(), e);
         }
      }
      return true;
   }
}
