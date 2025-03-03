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

package io.github.microcks.service;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;
import io.github.microcks.security.UserInfo;
import io.github.microcks.util.ai.AICopilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A service that runs the AI Copilot on asynchronous/long-running tasks.
 * @author laurent
 */
@org.springframework.stereotype.Service
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
   public AICopilotRunnerService(ServiceService serviceService, Optional<AICopilot> copilot) {
      this.serviceService = serviceService;
      copilot.ifPresent(aiCopilot -> this.copilot = aiCopilot);
   }

   /**
    * Generate samples for all operations of a service using asynchronous/completable future pattern.
    * @param service  The service to generate samples for.
    * @param contract The contract to generate samples for.
    * @param userInfo The user information for user having launched the generation.
    * @return A Future wrapping generation success flag.
    */
   @Async
   public CompletableFuture<Boolean> generateSamplesForService(Service service, Resource contract, UserInfo userInfo) {
      for (Operation operation : service.getOperations()) {
         try {
            List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation, contract, 3);
            serviceService.addAICopilotExchangesToServiceOperation(service.getId(), operation.getName(),
                  (List<Exchange>) exchanges, userInfo);
         } catch (Exception e) {
            log.error("Caught an exception while generating samples", e);
            return CompletableFuture.completedFuture(false);
         }
      }
      return CompletableFuture.completedFuture(true);
   }
}
