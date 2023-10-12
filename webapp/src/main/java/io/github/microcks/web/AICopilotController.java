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
package io.github.microcks.web;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.security.UserInfo;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.ai.AICopilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

/**
 * A controller for interacting with optional AI Copilot in Microcks.
 * @author laurent
 */
@RestController
@RequestMapping("/api/copilot")
public class AICopilotController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(AICopilotController.class);

   @Autowired(required = false)
   private AICopilot copilot;

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private ServiceService serviceService;

   /**
    *
    * @param serviceId
    * @param operationName
    * @return
    */
   @RequestMapping(value = "/samples/{id:.+}", method = RequestMethod.GET)
   public ResponseEntity<?> getSamplesSuggestions(
         @PathVariable("id") String serviceId,
         @RequestParam(value = "operation") String operationName
      ) {
      log.debug("Retrieving service with id {}", serviceId);

      Service service = null;
      // serviceId may have the form of <service_name>:<service_version>
      if (serviceId.contains(":")) {
         String name = serviceId.substring(0, serviceId.indexOf(':'));
         String version = serviceId.substring(serviceId.indexOf(':') + 1);

         // If service name was encoded with '+' instead of '%20', replace them.
         if (name.contains("+")) {
            name = name.replace('+', ' ');
         }
         service = serviceRepository.findByNameAndVersion(name, version);
      } else {
         service = serviceRepository.findById(serviceId).orElse(null);
      }

      if (service != null) {
         log.debug("We found service, now looking for required contract...");
         List<Resource> resources = null;
         if (service.getType() == ServiceType.REST) {
            resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.OPEN_API_SPEC);
         } else if (service.getType() == ServiceType.GRAPHQL) {
            resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.GRAPHQL_SCHEMA);
         } else if (service.getType() == ServiceType.EVENT) {
            resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.ASYNC_API_SPEC);
         }

         // Find the matching operation on service.
         Optional<Operation> operation = service.getOperations().stream()
               .filter(op -> operationName.equals(op.getName()))
               .findFirst();

         if (!resources.isEmpty() && !operation.isEmpty()) {
            try {
               List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation.get(), resources.get(0), 2);
               return new ResponseEntity<>(exchanges, HttpStatus.OK);
            } catch (Exception e) {
               log.error("Caught and exception while generating samples", e);
               return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
            }
         }
      }
      log.error("At least one mandatory parameters (serviceId, operationName or contract) is missing");
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }

   @RequestMapping(value = "/samples/{id:.+}", method = RequestMethod.POST)
   public ResponseEntity<?> addSamplesSuggestions(
         @PathVariable("id") String serviceId,
         @RequestParam(value = "operation") String operationName,
         @RequestBody List<Exchange> exchanges,
         UserInfo userInfo
      ) {
      log.debug("Adding new AI samples to service {} and operation {}", serviceId, operationName);
      boolean result = serviceService.addExchangesToServiceOperation(serviceId, operationName, exchanges, userInfo);
      if (result){
         return new ResponseEntity<>(HttpStatus.CREATED);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }
}
