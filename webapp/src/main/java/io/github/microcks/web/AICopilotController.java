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
import io.github.microcks.service.ExchangeSelection;
import io.github.microcks.service.ImportExportService;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.ai.AICopilot;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * A controller for interacting with optional AI Copilot in Microcks.
 * @author laurent
 */
@RestController
@RequestMapping("/api/copilot")
public class AICopilotController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(AICopilotController.class);

   private AICopilot copilot;

   private final ServiceService serviceService;
   private final ImportExportService importExportService;
   private final ServiceRepository serviceRepository;
   private final ResourceRepository resourceRepository;


   /**
    * Build a AICopilotController with required dependencies.
    * @param serviceService The service to managed Services objects
    * @param importExportService The service to manage import/export of data
    * @param serviceRepository  The repository for Services
    * @param resourceRepository The repository for Resources
    * @param copilot            The optional AI Copilot
    */
   public AICopilotController(ServiceService serviceService, ImportExportService importExportService,
         ServiceRepository serviceRepository, ResourceRepository resourceRepository, Optional<AICopilot> copilot) {
      this.serviceService = serviceService;
      this.importExportService = importExportService;
      this.serviceRepository = serviceRepository;
      this.resourceRepository = resourceRepository;
      copilot.ifPresent(aiCopilot -> this.copilot = aiCopilot);
   }

   @GetMapping(value = "/samples/{id:.+}")
   public ResponseEntity<?> getSamplesSuggestions(@PathVariable("id") String serviceId,
         @RequestParam(value = "operation") String operationName) {
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
         } else if (service.getType() == ServiceType.GRPC) {
            resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.PROTOBUF_SCHEMA);
         }

         // Find the matching operation on service.
         Optional<Operation> operation = service.getOperations().stream()
               .filter(op -> operationName.equals(op.getName())).findFirst();

         if (resources != null && !resources.isEmpty() && operation.isPresent()) {
            try {
               List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation.get(),
                     resources.get(0), 2);
               return new ResponseEntity<>(exchanges, HttpStatus.OK);
            } catch (Exception e) {
               log.error("Caught and exception while generating samples", e);
               return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
            }
         }
      }
      log.error("At least one mandatory parameters (serviceId, operationName or contract) is missing");
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }

   @PostMapping(value = "/samples/{id:.+}")
   public ResponseEntity<?> addSamplesSuggestions(@PathVariable("id") String serviceId,
         @RequestParam(value = "operation") String operationName, @RequestBody List<Exchange> exchanges,
         UserInfo userInfo) {
      log.debug("Adding new AI samples to service {} and operation {}", serviceId, operationName);
      boolean result = serviceService.addAICopilotExchangesToServiceOperation(serviceId, operationName, exchanges,
            userInfo);
      if (result) {
         return new ResponseEntity<>(HttpStatus.CREATED);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @PostMapping(value = "/samples/{id:.+}/cleanup")
   public ResponseEntity<?> removeExchanges(@PathVariable("id") String serviceId,
         @RequestBody ExchangeSelection exchangeSelection, UserInfo userInfo) {
      log.debug("Cleaning AI samples from service {} and multiple operations", serviceId);
      boolean result = serviceService.removeAICopilotExchangesFromService(serviceId, exchangeSelection, userInfo);
      if (result) {
         return new ResponseEntity<>(HttpStatus.NO_CONTENT);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @PostMapping(value = "/samples/{id:.+}/export")
   public ResponseEntity<Object> exportExchanges(@PathVariable("id") String serviceId,
         @RequestBody ExchangeSelection exchangeSelection, UserInfo userInfo) {
      log.debug("Generating AI samples export for service {}", serviceId);

      try {
         byte[] body = importExportService.exportExchangeSelection(serviceId, exchangeSelection, userInfo)
               .getBytes(StandardCharsets.UTF_8);
         HttpHeaders responseHeaders = new HttpHeaders();
         responseHeaders.setContentType(MediaType.APPLICATION_JSON);
         responseHeaders.set("Content-Disposition", "attachment; filename=microcks-repository.json");
         responseHeaders.setContentLength(body.length);

         return new ResponseEntity<>(body, responseHeaders, HttpStatus.OK);
      } catch (Exception e) {
         log.error("Exception while exporting the Exchanges selection", e);
         return new ResponseEntity<>("Exception while exporting the Exchanges selection. Check server logs.",
               HttpStatus.INTERNAL_SERVER_ERROR);
      }
   }
}
