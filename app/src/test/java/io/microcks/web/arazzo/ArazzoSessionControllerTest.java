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
package io.microcks.web.arazzo;

import io.microcks.domain.workflow.WorkflowSession;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.service.arazzo.ArazzoRunRefusedException;
import io.microcks.service.arazzo.ArazzoSessionService;
import io.microcks.web.arazzo.ArazzoSessionController.ArazzoSessionRequest;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests of the session endpoints as a test calls them, on the session use case.
 * @author SebastienDegodez
 */
class ArazzoSessionControllerTest {

   private final ArazzoSessionService arazzoSessionService = mock(ArazzoSessionService.class);
   private final ArazzoSessionController controller = new ArazzoSessionController(arazzoSessionService);

   @Test
   void anOpenedSessionIsCreatedAtItsUrl() throws Exception {
      WorkflowSession session = new WorkflowSession();
      session.setId("s-1");
      when(arazzoSessionService.openSession("Order saga:1.0.0", "placeOrderSaga", Map.of("customerId", "c-7"),
            Map.of())).thenReturn(Optional.of(session));

      Response opened = controller
            .openSession(new ArazzoSessionRequest("Order saga:1.0.0", "placeOrderSaga", Map.of("customerId", "c-7")));

      assertEquals(201, opened.getStatus());
      assertEquals(URI.create("/api/workflows/sessions/s-1"), opened.getLocation());
      assertSame(session, opened.getEntity());
   }

   @Test
   void aSessionIsOpenedWithTheStepsItSetsToFail() throws Exception {
      WorkflowSession session = new WorkflowSession();
      session.setId("s-2");
      Map<String, String> failures = Map.of("chargePayment", "card refused");
      when(arazzoSessionService.openSession("Order saga:1.0.0", "compensatePaymentRefused", null, failures))
            .thenReturn(Optional.of(session));

      Response opened = controller
            .openSession(new ArazzoSessionRequest("Order saga:1.0.0", "compensatePaymentRefused", null, failures));

      assertEquals(201, opened.getStatus());
      assertSame(session, opened.getEntity());
   }

   @Test
   void noSessionIsOpenedOnAnUnknownDocument() throws Exception {
      when(arazzoSessionService.openSession("unknown", "placeOrderSaga", null, Map.of())).thenReturn(Optional.empty());

      assertEquals(404,
            controller.openSession(new ArazzoSessionRequest("unknown", "placeOrderSaga", null)).getStatus());
   }

   @Test
   void aRefusedSessionIsABadRequestTellingWhyInPlainText() throws Exception {
      when(arazzoSessionService.openSession("Order saga:1.0.0", "cancelSaga", null, Map.of()))
            .thenThrow(new ArazzoRunRefusedException("Unknown workflow: cancelSaga"));

      Response refused = controller
            .openSession(new ArazzoSessionRequest("Order saga:1.0.0", "cancelSaga", null));

      assertEquals(400, refused.getStatus());
      assertEquals(MediaType.TEXT_PLAIN_TYPE, refused.getMediaType());
      assertEquals("Unknown workflow: cancelSaga", refused.getEntity());
   }

   @Test
   void aClosedSessionHasNoContentAndAnUnknownSessionIsNotFound() {
      when(arazzoSessionService.closeSession("s-1")).thenReturn(true);

      assertEquals(204, controller.closeSession("s-1").getStatus());
      assertEquals(404, controller.closeSession("unknown").getStatus());
   }

   @Test
   void theVerificationOfASessionIsReturnedAndAnUnknownSessionIsNotFound() {
      WorkflowResult verification = new WorkflowResult();
      when(arazzoSessionService.verify("s-1")).thenReturn(Optional.of(verification));
      when(arazzoSessionService.verify("unknown")).thenReturn(Optional.empty());

      Response verified = controller.verify("s-1");

      assertEquals(200, verified.getStatus());
      assertSame(verification, verified.getEntity());
      assertEquals(404, controller.verify("unknown").getStatus());
   }
}
