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
package io.microcks.web;

import io.microcks.domain.Operation;
import io.microcks.domain.Service;
import io.microcks.domain.WebhookRegistration;
import io.microcks.repository.WebhookRegistrationRepository;
import io.microcks.service.ServiceService;
import io.microcks.util.SafeLogger;
import io.microcks.web.dto.WebhookRegistrationRequestDTO;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Rest controller for API that allow the management of Webhook registrations.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class WebhookController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(WebhookController.class);

   private final ServiceService serviceService;
   private final WebhookRegistrationRepository webhookRegistrationRepository;

   @Inject
   public WebhookController(ServiceService serviceService,
         WebhookRegistrationRepository webhookRegistrationRepository) {
      this.serviceService = serviceService;
      this.webhookRegistrationRepository = webhookRegistrationRepository;
   }

   @GET
   @Path("/webhooks/operation/{operationId}")
   public List<WebhookRegistration> listWebhookRegistrationsForOperation(@PathParam("operationId") String operationId,
         @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("20") int size) {
      operationId = operationId.replace('!', '/');
      log.debug("Getting webhook registrations list for operation '{}', page {} and size {}", operationId, page, size);
      return webhookRegistrationRepository.findByOperationId(operationId, Page.of(page, size),
            Sort.descending("createdOn"));
   }

   @GET
   @Path("/webhooks/operation/{operationId}/count")
   public Map<String, Long> countWebhookRegistrationsForOperation(@PathParam("operationId") String operationId) {
      operationId = operationId.replace('!', '/');
      log.debug("Counting tests for operationId...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", webhookRegistrationRepository.countByOperationId(operationId));
      return counter;
   }

   @POST
   @Path("/webhooks")
   public Response registerToWebhook(WebhookRegistrationRequestDTO registrationRequest) {
      log.debug("Registering webhook at '{}' for operation '{}'", registrationRequest.getTargetUrl(),
            registrationRequest.getOperationId());

      // Check Service and Operation exist.
      String rawOperationId = registrationRequest.getOperationId();
      int delimiterIdx = rawOperationId != null ? rawOperationId.indexOf('-') : -1;
      if (rawOperationId == null || delimiterIdx <= 0) {
         log.error("Invalid operationId received for webhook registration");
         return Response.status(Response.Status.NOT_FOUND).entity("OperationId is invalid").build();
      }

      String serviceId = rawOperationId.substring(0, delimiterIdx);
      String operationName = rawOperationId.substring(delimiterIdx + 1).replace('!', '/');
      String operationId = serviceId + "-" + operationName;

      Service service = serviceService.getServiceById(serviceId);
      if (service == null) {
         log.error("Service with id '{}' not found", serviceId);
         return Response.status(Response.Status.NOT_FOUND).entity("Service not found").build();
      }
      Operation operation = service.getOperations().stream().filter(op -> op.getName().equals(operationName))
            .findFirst().orElse(null);
      if (operation == null) {
         log.error("Operation with name '{}' not found", operationName);
         return Response.status(Response.Status.NOT_FOUND).entity("Operation not found").build();
      }

      // Create a new registration with properties.
      WebhookRegistration webhookRegistration = new WebhookRegistration();
      webhookRegistration.setTargetUrl(registrationRequest.getTargetUrl());
      webhookRegistration.setOperationId(operationId);
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

      webhookRegistrationRepository.persistOrUpdate(webhookRegistration);
      return Response.status(Response.Status.CREATED).entity(webhookRegistration).build();
   }

   @DELETE
   @Path("/webhooks/{id}")
   public Response unregisterFromWebhook(@PathParam("id") String webhookRegistrationId) {
      log.debug("Removing webhook registration with id {}", webhookRegistrationId);
      WebhookRegistration result = webhookRegistrationRepository.findById(webhookRegistrationId);
      if (result != null) {
         webhookRegistrationRepository.deleteById(webhookRegistrationId);
         return Response.status(Response.Status.OK).build();
      }
      return Response.status(Response.Status.NOT_FOUND).build();
   }
}
