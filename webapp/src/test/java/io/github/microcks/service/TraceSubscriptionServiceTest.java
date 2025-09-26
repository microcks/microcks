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

import io.github.microcks.event.TraceEvent;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test case for TraceSubscriptionService class.
 */
@ExtendWith(MockitoExtension.class)
class TraceSubscriptionServiceTest {

   private TraceSubscriptionService traceSubscriptionService;

   @Mock
   private SpanStorageService mockSpanStorageService;

   @BeforeEach
   void setUp() {
      traceSubscriptionService = new TraceSubscriptionService(mockSpanStorageService);
   }

   @Test
   @DisplayName("Should ignore events with empty spans")
   void shouldIgnoreEventsWithEmptySpans() {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      @SuppressWarnings("unused")
      SseEmitter emitter = traceSubscriptionService.subscribe(serviceName, operationName);

      TraceEvent event = new TraceEvent("trace-empty", serviceName, operationName);
      when(mockSpanStorageService.getSpansForTrace("trace-empty")).thenReturn(Collections.emptyList());

      // When
      traceSubscriptionService.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace("trace-empty");
   }

   @Test
   @DisplayName("Should process matching trace events")
   void shouldProcessMatchingTraceEvents() {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      String traceId = "trace-123";

      @SuppressWarnings("unused")
      SseEmitter emitter = traceSubscriptionService.subscribe(serviceName, operationName);

      List<ReadableSpan> spans = List.of(createMockSpanWithServiceInfo(traceId, "span-1", serviceName, operationName));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, serviceName, operationName);

      // When
      traceSubscriptionService.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
   }

   @Test
   @DisplayName("Should not process non-matching trace events")
   void shouldNotProcessNonMatchingTraceEvents() {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      String differentService = "order-service";
      String differentOperation = "create";
      String traceId = "trace-456";

      @SuppressWarnings("unused")
      SseEmitter emitter = traceSubscriptionService.subscribe(serviceName, operationName);

      // Create a simple mock span - no need to stub toSpanData() since it won't be called
      ReadableSpan span = Mockito.mock(ReadableSpan.class);
      List<ReadableSpan> spans = List.of(span);
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, differentService, differentOperation);

      // When
      traceSubscriptionService.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
   }

   @Test
   @DisplayName("Should send heartbeat without errors")
   void shouldSendHeartbeatWithoutErrors() {
      // Given
      @SuppressWarnings("unused")
      SseEmitter emitter = traceSubscriptionService.subscribe("user-service", "login");

      // When & Then
      assertDoesNotThrow(() -> traceSubscriptionService.sendHeartbeats());
   }

   // Helper methods for creating mock spans

   private ReadableSpan createMockSpanWithServiceInfo(String traceId, String spanId, String serviceName,
         String operationName) {
      ReadableSpan span = Mockito.mock(ReadableSpan.class);
      SpanData spanData = Mockito.mock(SpanData.class);

      // Only stub what's actually used by the TraceSubscriptionService
      when(span.toSpanData()).thenReturn(spanData);

      return span;
   }
}
