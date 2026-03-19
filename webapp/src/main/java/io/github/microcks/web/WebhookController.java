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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.WebhookRegistration;
import io.github.microcks.repository.WebhookRegistrationRepository;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.web.dto.WebhookRegistrationRequestDTO;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A Rest controller for API that allow the management of Webhook registrations.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class WebhookController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(WebhookController.class);

   private final ServiceService serviceService;
   private final WebhookRegistrationRepository webhookRegistrationRepository;


   public WebhookController(ServiceService serviceService,
         WebhookRegistrationRepository webhookRegistrationRepository) {
      this.serviceService = serviceService;
      this.webhookRegistrationRepository = webhookRegistrationRepository;
   }

   @GetMapping("/webhooks/operation/{operationId}")
   public List<WebhookRegistration> listWebhookRegistrationsForOperation(
         @PathVariable("operationId") String operationId,
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
      log.debug("Getting webhook registrations list for operation '{}', page {} and size {}", operationId, page, size);
      return webhookRegistrationRepository.findByOperationId(operationId,
            PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdOn")));
   }

   @GetMapping("/webhooks/operation/{operationId}/count")
   public Map<String, Long> countWebhookRegistrationsForOperation(@PathVariable("operationId") String operationId) {
      log.debug("Counting tests for operationId...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", webhookRegistrationRepository.countByOperationId(operationId));
      return counter;
   }

   @PostMapping("/webhooks")
   public ResponseEntity<Object> registerToWebhook(@RequestBody WebhookRegistrationRequestDTO registrationRequest) {
      log.debug("Registering webhook at '{}' for operation '{}'", registrationRequest.getTargetUrl(),
            registrationRequest.getOperationId());

      // Check Service and Operation exist.
      String[] parts = registrationRequest.getOperationId().split("-");
      if (parts.length != 2) {
         log.error("Invalid operationId received for webhook registration");
         return new ResponseEntity<>("OperationId is invalid", HttpStatus.NOT_FOUND);
      }
      String serviceId = parts[0];
      Service service = serviceService.getServiceById(serviceId);
      if (service == null) {
         log.error("Service with id '{}' not found", serviceId);
         return new ResponseEntity<>("Service not found", HttpStatus.NOT_FOUND);
      }
      Operation operation = service.getOperations().stream().filter(op -> op.getName().equals(parts[1])).findFirst()
            .orElse(null);
      if (operation == null) {
         log.error("Operation with name '{}' not found", parts[1]);
         return new ResponseEntity<>("Operation not found", HttpStatus.NOT_FOUND);
      }

      // Create a new registration with properties.
      WebhookRegistration webhookRegistration = new WebhookRegistration();
      webhookRegistration.setTargetUrl(registrationRequest.getTargetUrl());
      webhookRegistration.setOperationId(registrationRequest.getOperationId());
      webhookRegistration.setOperationMethod(operation.getMethod());
      if (registrationRequest.getFrequency() != null) {
         webhookRegistration.setFrequency(registrationRequest.getFrequency());
      } else {
         webhookRegistration.setFrequency(3 * 1000L);
      }
      if (registrationRequest.getExpiresAt() != null) {
         webhookRegistration.setExpiresAt(registrationRequest.getExpiresAt());
      } else {
         webhookRegistration.setExpiresAt(new Date(System.currentTimeMillis() + (2 * 24 * 3600 * 1000L)));
      }
      if (registrationRequest.getErrorCountThreshold() != null) {
         webhookRegistration.setErrorCountThreshold(registrationRequest.getErrorCountThreshold());
      } else {
         webhookRegistration.setErrorCountThreshold(5);
      }

      webhookRegistration = webhookRegistrationRepository.save(webhookRegistration);
      return new ResponseEntity<>(webhookRegistration, HttpStatus.CREATED);
   }

   @DeleteMapping("/webhooks/{id}")
   public ResponseEntity<String> unregisterFromWebhook(@PathVariable("id") String webhookRegistrationId) {
      log.debug("Removing webhook registration with id {}", webhookRegistrationId);
      Optional<WebhookRegistration> result = webhookRegistrationRepository.findById(webhookRegistrationId);
      if (result.isPresent()) {
         webhookRegistrationRepository.deleteById(webhookRegistrationId);
         return new ResponseEntity<>(HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
   }
}
