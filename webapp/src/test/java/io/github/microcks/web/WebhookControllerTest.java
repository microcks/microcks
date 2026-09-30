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
import io.github.microcks.web.dto.WebhookRegistrationRequestDTO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
      when(webhookRegistrationRepository.save(any(WebhookRegistration.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      request.setOperationId(serviceId + "-" + opName);
      request.setTargetUrl("https://example.com/webhook");

      ResponseEntity<Object> response = controller.registerToWebhook(request);

      assertEquals(HttpStatus.CREATED, response.getStatusCode());
      assertTrue(response.getBody() instanceof WebhookRegistration);

      WebhookRegistration saved = (WebhookRegistration) response.getBody();
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
      when(webhookRegistrationRepository.save(any(WebhookRegistration.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      request.setOperationId(serviceId + "-" + opName);
      request.setTargetUrl("https://example.com/orders");

      ResponseEntity<Object> response = controller.registerToWebhook(request);

      assertEquals(HttpStatus.CREATED, response.getStatusCode());
      assertTrue(response.getBody() instanceof WebhookRegistration);

      WebhookRegistration saved = (WebhookRegistration) response.getBody();
      assertEquals(serviceId + "-" + opName, saved.getOperationId());
   }

   @Test
   void shouldRegisterWebhookSuccessfullyForOperationWithEncodedSlashes() {
      // Operation name in service has slashes, but frontend encodes slashes as '!'
      String serviceId = "60f123456789abcdef012345";
      String opName = "POST /api/v1/user-profiles";
      Service service = createService(serviceId, opName, "POST");

      when(serviceService.getServiceById(serviceId)).thenReturn(service);
      when(webhookRegistrationRepository.save(any(WebhookRegistration.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      // Frontend replaces '/' with '!'
      request.setOperationId(serviceId + "-POST !api!v1!user-profiles");
      request.setTargetUrl("https://example.com/users");

      ResponseEntity<Object> response = controller.registerToWebhook(request);

      assertEquals(HttpStatus.CREATED, response.getStatusCode());
      assertTrue(response.getBody() instanceof WebhookRegistration);

      WebhookRegistration saved = (WebhookRegistration) response.getBody();
      assertEquals(serviceId + "-" + opName, saved.getOperationId());
      assertEquals("POST", saved.getOperationMethod());
   }

   @Test
   void shouldReturnNotFoundWhenOperationIdIsInvalidOrNull() {
      // Null operationId
      WebhookRegistrationRequestDTO request1 = new WebhookRegistrationRequestDTO();
      ResponseEntity<Object> response1 = controller.registerToWebhook(request1);
      assertEquals(HttpStatus.NOT_FOUND, response1.getStatusCode());
      assertEquals("OperationId is invalid", response1.getBody());

      // No hyphen
      WebhookRegistrationRequestDTO request2 = new WebhookRegistrationRequestDTO();
      request2.setOperationId("invalidOperationIdWithoutHyphen");
      ResponseEntity<Object> response2 = controller.registerToWebhook(request2);
      assertEquals(HttpStatus.NOT_FOUND, response2.getStatusCode());
      assertEquals("OperationId is invalid", response2.getBody());

      // Starts with hyphen (empty serviceId)
      WebhookRegistrationRequestDTO request3 = new WebhookRegistrationRequestDTO();
      request3.setOperationId("-someOperation");
      ResponseEntity<Object> response3 = controller.registerToWebhook(request3);
      assertEquals(HttpStatus.NOT_FOUND, response3.getStatusCode());
      assertEquals("OperationId is invalid", response3.getBody());

      verify(serviceService, never()).getServiceById(any());
      verify(webhookRegistrationRepository, never()).save(any());
   }

   @Test
   void shouldReturnNotFoundWhenServiceNotFound() {
      String serviceId = "nonExistingServiceId";
      when(serviceService.getServiceById(serviceId)).thenReturn(null);

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      request.setOperationId(serviceId + "-someOperation");

      ResponseEntity<Object> response = controller.registerToWebhook(request);

      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
      assertEquals("Service not found", response.getBody());
   }

   @Test
   void shouldReturnNotFoundWhenOperationNotFound() {
      String serviceId = "60f123456789abcdef012345";
      Service service = createService(serviceId, "existingOperation", "POST");
      when(serviceService.getServiceById(serviceId)).thenReturn(service);

      WebhookRegistrationRequestDTO request = new WebhookRegistrationRequestDTO();
      request.setOperationId(serviceId + "-nonExistingOperation");

      ResponseEntity<Object> response = controller.registerToWebhook(request);

      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
      assertEquals("Operation not found", response.getBody());
   }

   @Test
   void shouldListWebhookRegistrationsForOperationDecodingExclamationMarks() {
      String opIdWithExclamation = "60f123456789abcdef012345-POST !api!v1!users";
      String decodedOpId = "60f123456789abcdef012345-POST /api/v1/users";

      WebhookRegistration reg = new WebhookRegistration();
      reg.setOperationId(decodedOpId);
      when(webhookRegistrationRepository.findByOperationId(eq(decodedOpId), any(PageRequest.class)))
            .thenReturn(List.of(reg));

      List<WebhookRegistration> result = controller.listWebhookRegistrationsForOperation(opIdWithExclamation, 0, 20);

      assertEquals(1, result.size());
      assertEquals(decodedOpId, result.get(0).getOperationId());
      verify(webhookRegistrationRepository).findByOperationId(eq(decodedOpId),
            eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdOn"))));
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
      when(webhookRegistrationRepository.findById(regId)).thenReturn(Optional.of(reg));

      ResponseEntity<String> response = controller.unregisterFromWebhook(regId);

      assertEquals(HttpStatus.OK, response.getStatusCode());
      verify(webhookRegistrationRepository).deleteById(regId);
   }

   @Test
   void shouldReturnNotFoundWhenUnregisteringNonExistingWebhook() {
      String regId = "nonExistingReg";
      when(webhookRegistrationRepository.findById(regId)).thenReturn(Optional.empty());

      ResponseEntity<String> response = controller.unregisterFromWebhook(regId);

      assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
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
