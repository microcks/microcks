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
import io.microcks.web.dto.WebhookRegistrationRequestDTO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.core.Response;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for WebhookController.
 */
@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

   @Mock
   private ServiceService serviceService;

   @Mock
   private WebhookRegistrationRepository webhookRegistrationRepository;

   @InjectMocks
   private WebhookController controller;

   @Test
   void shouldRegisterWebhookSuccessfullyForStandardOperation() {
      // Setup test service and operation
      String serviceId = "60f123456789abcdef012345";
      String opName = "newPet WEBHOOK";
      Service service = createService(serviceId, opName, "POST");

      when(serviceService.getServiceById(serviceId)).thenReturn(service);

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      request.setOperationId(serviceId + "-" + opName);
      request.setTargetUrl("https://example.com/webhook");

      Response response = controller.registerToWebhook(request);

      assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
      assertTrue(response.getEntity() instanceof WebhookRegistration);

      WebhookRegistration saved = (WebhookRegistration) response.getEntity();
      assertEquals("https://example.com/webhook", saved.getTargetUrl());
      assertEquals(serviceId + "-" + opName, saved.getOperationId());
      assertEquals("POST", saved.getOperationMethod());
      assertEquals(3000L, saved.getFrequency());
      assertEquals(5, saved.getErrorCountThreshold());
      assertNotNull(saved.getExpiresAt());
   }

   @Test
   void shouldRegisterWebhookSuccessfullyForOperationWithHyphens() {
      // Operation name has hyphens
      String serviceId = "60f123456789abcdef012345";
      String opName = "order-placed-event WEBHOOK";
      Service service = createService(serviceId, opName, "POST");

      when(serviceService.getServiceById(serviceId)).thenReturn(service);

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      request.setOperationId(serviceId + "-" + opName);
      request.setTargetUrl("https://example.com/orders");

      Response response = controller.registerToWebhook(request);

      assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
      assertTrue(response.getEntity() instanceof WebhookRegistration);

      WebhookRegistration saved = (WebhookRegistration) response.getEntity();
      assertEquals(serviceId + "-" + opName, saved.getOperationId());
   }

   @Test
   void shouldRegisterWebhookSuccessfullyForOperationWithEncodedSlashes() {
      // Operation name in service has slashes, but frontend encodes slashes as '!'
      String serviceId = "60f123456789abcdef012345";
      String opName = "POST /api/v1/user-profiles";
      Service service = createService(serviceId, opName, "POST");

      when(serviceService.getServiceById(serviceId)).thenReturn(service);

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      // Frontend replaces '/' with '!'
      request.setOperationId(serviceId + "-POST !api!v1!user-profiles");
      request.setTargetUrl("https://example.com/users");

      Response response = controller.registerToWebhook(request);

      assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
      assertTrue(response.getEntity() instanceof WebhookRegistration);

      WebhookRegistration saved = (WebhookRegistration) response.getEntity();
      assertEquals(serviceId + "-" + opName, saved.getOperationId());
      assertEquals("POST", saved.getOperationMethod());
   }

   @Test
   void shouldReturnNotFoundWhenOperationIdIsInvalidOrNull() {
      // Null operationId
      WebhookRegistrationRequestDTO request1 = new WebhookRegistrationRequestDTO();
      Response response1 = controller.registerToWebhook(request1);
      assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response1.getStatus());
      assertEquals("OperationId is invalid", response1.getEntity());

      // No hyphen
      WebhookRegistrationRequestDTO request2 = new WebhookRegistrationRequestDTO();
      request2.setOperationId("invalidOperationIdWithoutHyphen");
      Response response2 = controller.registerToWebhook(request2);
      assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response2.getStatus());
      assertEquals("OperationId is invalid", response2.getEntity());

      // Starts with hyphen (empty serviceId)
      WebhookRegistrationRequestDTO request3 = new WebhookRegistrationRequestDTO();
      request3.setOperationId("-someOperation");
      Response response3 = controller.registerToWebhook(request3);
      assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response3.getStatus());
      assertEquals("OperationId is invalid", response3.getEntity());

      verify(serviceService, never()).getServiceById(any());
      verify(webhookRegistrationRepository, never()).persistOrUpdate(any(WebhookRegistration.class));
   }

   @Test
   void shouldReturnNotFoundWhenServiceNotFound() {
      String serviceId = "nonExistingServiceId";
      when(serviceService.getServiceById(serviceId)).thenReturn(null);

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      request.setOperationId(serviceId + "-someOperation");

      Response response = controller.registerToWebhook(request);

      assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
      assertEquals("Service not found", response.getEntity());
   }

   @Test
   void shouldReturnNotFoundWhenOperationNotFound() {
      String serviceId = "60f123456789abcdef012345";
      Service service = createService(serviceId, "existingOperation", "POST");
      when(serviceService.getServiceById(serviceId)).thenReturn(service);

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      request.setOperationId(serviceId + "-nonExistingOperation");

      Response response = controller.registerToWebhook(request);

      assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
      assertEquals("Operation not found", response.getEntity());
   }

   @Test
   void shouldListWebhookRegistrationsForOperationDecodingExclamationMarks() {
      String opIdWithExclamation = "60f123456789abcdef012345-POST !api!v1!users";
      String decodedOpId = "60f123456789abcdef012345-POST /api/v1/users";

      WebhookRegistration reg = new WebhookRegistration();
      reg.setOperationId(decodedOpId);
      when(webhookRegistrationRepository.findByOperationId(eq(decodedOpId), any(Page.class), any(Sort.class)))
            .thenReturn(List.of(reg));

      List<WebhookRegistration> result = controller.listWebhookRegistrationsForOperation(opIdWithExclamation, 0, 20);

      assertEquals(1, result.size());
      assertEquals(decodedOpId, result.get(0).getOperationId());
      verify(webhookRegistrationRepository).findByOperationId(eq(decodedOpId),
            argThat(p -> p.index == 0 && p.size == 20), any(Sort.class));
   }

   @Test
   void shouldCountWebhookRegistrationsForOperationDecodingExclamationMarks() {
      String opIdWithExclamation = "60f123456789abcdef012345-POST !api!v1!users";
      String decodedOpId = "60f123456789abcdef012345-POST /api/v1/users";

      when(webhookRegistrationRepository.countByOperationId(decodedOpId)).thenReturn(3L);

      Map<String, Long> result = controller.countWebhookRegistrationsForOperation(opIdWithExclamation);

      assertEquals(3L, result.get("counter"));
      verify(webhookRegistrationRepository).countByOperationId(decodedOpId);
   }

   @Test
   void shouldUnregisterWebhookWhenFound() {
      String regId = "reg123";
      WebhookRegistration reg = new WebhookRegistration();
      reg.setId(regId);
      when(webhookRegistrationRepository.findById(regId)).thenReturn(reg);

      Response response = controller.unregisterFromWebhook(regId);

      assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
      verify(webhookRegistrationRepository).deleteById(regId);
   }

   @Test
   void shouldReturnNotFoundWhenUnregisteringNonExistingWebhook() {
      String regId = "nonExistingReg";
      when(webhookRegistrationRepository.findById(regId)).thenReturn(null);

      Response response = controller.unregisterFromWebhook(regId);

      assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
      verify(webhookRegistrationRepository, never()).deleteById(any());
   }

   private Service createService(String serviceId, String operationName, String method) {
      Service service = new Service();
      service.setId(serviceId);
      service.setName("Test Service");
      service.setVersion("1.0");

      Operation operation = new Operation();
      operation.setName(operationName);
      operation.setMethod(method);

      service.setOperations(List.of(operation));
      return service;
   }
}
