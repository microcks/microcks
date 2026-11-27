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

import io.microcks.domain.Header;
import io.microcks.domain.Operation;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.ServiceStateRepository;
import io.microcks.service.OpenTelemetryResolverService;
import io.microcks.service.ProxyService;
import io.microcks.testsupport.MockServletRequest;
import io.microcks.util.delay.DelaySpec;
import io.microcks.util.tracing.CommonAttributes;
import io.microcks.util.tracing.CommonEvents;
import io.microcks.web.arazzo.ArazzoSessionResponder;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import jakarta.enterprise.event.Event;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests of answering a REST invocation with the example a verification session set its operation to fail with: the
 * example is answered as is, rendered and delayed like a dispatched one, without dispatching the invocation, and the
 * session never fills it with its values: the injected example wins over the consistent answer of the session.
 * @author SebastienDegodez
 */
class RestInvocationProcessorInjectedFailureTest {

   private final ResponseRepository responseRepository = mock(ResponseRepository.class);
   private final ArazzoSessionResponder arazzoSessionResponder = mock(ArazzoSessionResponder.class);
   private final List<SpanData> ended = new ArrayList<>();
   private final SdkTracerProvider tracerProvider = SdkTracerProvider.builder().addSpanProcessor(new SpanProcessor() {
      @Override
      public void onStart(Context parentContext, ReadWriteSpan span) {
         // Only ended spans are looked at.
      }

      @Override
      public boolean isStartRequired() {
         return false;
      }

      @Override
      public void onEnd(ReadableSpan span) {
         ended.add(span.toSpanData());
      }

      @Override
      public boolean isEndRequired() {
         return true;
      }
   }).build();

   @AfterEach
   void closeTracerProvider() {
      tracerProvider.close();
   }

   @Test
   void theInjectedExampleIsAnsweredWithItsStatusHeadersAndRenderedBody() {
      RestInvocationProcessor processor = processor();
      HttpServletRequest request = new MockServletRequest();
      long startTime = System.currentTimeMillis();

      ResponseResult answer;
      Span span = tracerProvider.get("test").spanBuilder("POST /rest/{service}/{version}/**").startSpan();
      try (Scope scope = span.makeCurrent()) {
         answer = processor.processInjectedFailure(chargePayment(), startTime, new DelaySpec(60L, "fixed"),
               "{\"orderId\": \"o-42\", \"amount\": 25.0}", request, cardRefused());
      } finally {
         span.end();
      }

      assertEquals(402, answer.status().value());
      assertEquals("{\"orderId\": \"o-42\", \"status\": \"REFUSED\"}",
            new String(answer.content(), StandardCharsets.UTF_8));
      assertEquals("application/json;charset=UTF-8", answer.headers().getContentType().toString());
      assertEquals(List.of("insufficient-funds"), answer.headers().get("X-Decline-Reason"));
      assertTrue(System.currentTimeMillis() - startTime >= 60, "the delay asked for was not applied");
      assertEquals("card refused",
            ended.getFirst().getEvents().stream()
                  .filter(event -> event.getName().equals(CommonEvents.RESPONSE_SELECTED.getEventName())).findFirst()
                  .orElseThrow().getAttributes().get(CommonAttributes.RESPONSE_NAME));
      verifyNoInteractions(responseRepository, arazzoSessionResponder);
   }

   private RestInvocationProcessor processor() {
      OpenTelemetryResolverService openTelemetry = mock(OpenTelemetryResolverService.class);
      when(openTelemetry.getOpenTelemetry()).thenReturn(OpenTelemetry.noop());
      return new RestInvocationProcessor(mock(ServiceStateRepository.class), responseRepository,
            mock(Event.class), mock(Event.class), mock(Event.class), mock(ProxyService.class), openTelemetry,
            arazzoSessionResponder);
   }

   private static MockInvocationContext chargePayment() {
      Service payment = new Service();
      payment.setId("payment-api");
      payment.setName("Payment API");
      payment.setVersion("1.0.0");
      Operation operation = new Operation();
      operation.setName("POST /payments");
      operation.setMethod("POST");
      return new MockInvocationContext(payment, operation, "/payments");
   }

   /** The example card refused, its body rendered from the request. */
   private static Response cardRefused() {
      Response response = new Response();
      response.setName("card refused");
      response.setStatus("402");
      response.setMediaType("application/json");
      response.setContent("{\"orderId\": \"{{ request.body/orderId }}\", \"status\": \"REFUSED\"}");
      response.setHeaders(Set.of(new Header("X-Decline-Reason", Set.of("insufficient-funds"))));
      return response;
   }
}
