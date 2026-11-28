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

import io.microcks.domain.Operation;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.domain.workflow.WorkflowSessionRecordedCall;
import io.microcks.service.arazzo.ArazzoSessionAnswerService;
import io.microcks.web.MockInvocationContext;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests of the response of the REST mocks to an invocation naming a verification session, or naming none.
 * @author SebastienDegodez
 */
class ArazzoSessionResponderTest {

   private final ArazzoSessionAnswerService arazzoSessionAnswerService = mock(ArazzoSessionAnswerService.class);
   private final ArazzoSessionResponder responder = new ArazzoSessionResponder(arazzoSessionAnswerService);
   private final Response dispatched = new Response();

   @Test
   void anInvocationNamingASessionIsAnsweredByTheSessionAsReceived() {
      Response answer = new Response();
      when(arazzoSessionAnswerService.answer(eq("s-1"), any(), eq(dispatched))).thenReturn(Optional.of(answer));

      assertSame(answer, responder.respond(confirmOrder(), request("x-microcks-session", "s-1"), null, dispatched));

      ArgumentCaptor<WorkflowSessionRecordedCall> received = ArgumentCaptor.forClass(WorkflowSessionRecordedCall.class);
      verify(arazzoSessionAnswerService).answer(eq("s-1"), received.capture(), eq(dispatched));
      assertEquals(List.of("Order API", "POST /orders/{orderId}/confirm", "/orders/o-42/confirm"),
            List.of(received.getValue().getServiceName(), received.getValue().getOperationName(),
                  received.getValue().getPath()));
   }

   @Test
   void anInvocationTheSessionDoesNotAnswerKeepsTheDispatchedResponse() {
      when(arazzoSessionAnswerService.answer(eq("s-1"), any(), eq(dispatched))).thenReturn(Optional.empty());

      assertSame(dispatched, responder.respond(confirmOrder(), request("baggage", "microcks-session=s-1"), null,
            dispatched));
   }

   @Test
   void anInvocationNamingNoSessionKeepsTheDispatchedResponse() {
      assertSame(dispatched,
            responder.respond(confirmOrder(), request("Accept", "application/json"), null, dispatched));

      verifyNoInteractions(arazzoSessionAnswerService);
   }

   private static PostedRequest request(String header, String value) {
      return new PostedRequest("/rest/Order+API/1.0.0/orders/o-42/confirm", null).withHeader(header, value);
   }

   private static MockInvocationContext confirmOrder() {
      Service orderApi = new Service();
      orderApi.setName("Order API");
      orderApi.setVersion("1.0.0");
      Operation confirm = new Operation();
      confirm.setName("POST /orders/{orderId}/confirm");
      return new MockInvocationContext(orderApi, confirm, "/orders/o-42/confirm");
   }
}
