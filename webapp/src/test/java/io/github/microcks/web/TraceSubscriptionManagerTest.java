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

import io.github.microcks.event.TraceEvent;
import io.github.microcks.service.SpanStorageService;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/**
 * Test case for TraceSubscriptionService class.
 */
@ExtendWith(MockitoExtension.class)
class TraceSubscriptionManagerTest {

   private TraceSubscriptionManager traceSubscriptionManager;

   @Mock
   private SpanStorageService mockSpanStorageService;

   @BeforeEach
   void setUp() {
      traceSubscriptionManager = new TraceSubscriptionManager(mockSpanStorageService);
   }

   @Test
   @DisplayName("Should ignore events with empty spans")
   void shouldIgnoreEventsWithEmptySpans() throws IOException {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      String clientAddress = "127.0.0.1";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, serviceName, operationName, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      TraceEvent event = new TraceEvent("trace-empty", serviceName, operationName, clientAddress);
      when(mockSpanStorageService.getSpansForTrace("trace-empty")).thenReturn(Collections.emptyList());

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace("trace-empty");
      // Should not send any trace events since spans are empty
      verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should process matching trace events")
   void shouldProcessMatchingTraceEvents() throws IOException {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      String clientAddress = "127.0.0.1";
      String traceId = "trace-123";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, serviceName, operationName, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, serviceName, operationName, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should send trace event since it matches
      verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should not process non-matching trace events")
   void shouldNotProcessNonMatchingTraceEvents() throws IOException {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      String clientAddress = "127.0.0.1";
      String differentService = "order-service";
      String differentOperation = "create";
      String traceId = "trace-456";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, serviceName, operationName, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, differentService, differentOperation, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should not send trace event since service and operation don't match
      verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should send heartbeat without errors")
   void shouldSendHeartbeatWithoutErrors() throws IOException {
      // Given
      SseEmitter mockEmitter = mock(SseEmitter.class);
      traceSubscriptionManager.subscribe(mockEmitter, "user-service", "login", "127.0.0.1");

      // Reset mock to clear the initial heartbeat call and focus on periodic heartbeats
      reset(mockEmitter);

      // When & Then
      assertDoesNotThrow(() -> traceSubscriptionManager.sendHeartbeats());

      // Verify heartbeat was sent
      verify(mockEmitter, atLeastOnce()).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should process matching trace events with regex service name pattern")
   void shouldProcessMatchingTraceEventsWithWildcardServiceName() throws IOException {
      // Given
      String serviceNamePattern = ".*-service";
      String operationName = "login";
      String clientAddress = "127.0.0.1";
      String traceId = "trace-123";
      String actualServiceName = "user-service";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, serviceNamePattern, operationName, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, actualServiceName, operationName, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should send trace event since regex matches
      verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should process matching trace events with regex operation name pattern")
   void shouldProcessMatchingTraceEventsWithWildcardOperationName() throws IOException {
      // Given
      String serviceName = "user-service";
      String operationPattern = "log.*";
      String clientAddress = "127.0.0.1";
      String traceId = "trace-456";
      String actualOperation = "login";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, serviceName, operationPattern, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, serviceName, actualOperation, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should send trace event since regex matches
      verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should process matching trace events with full regex wildcard")
   void shouldProcessMatchingTraceEventsWithFullWildcard() throws IOException {
      // Given
      String servicePattern = "*";
      String operationPattern = "*";
      String clientAddress = "127.0.0.1";
      String traceId = "trace-789";
      String actualServiceName = "any-service";
      String actualOperation = "any-operation";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, servicePattern, operationPattern, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, actualServiceName, actualOperation, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should send trace event since full regex matches everything
      verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should not process non-matching trace events with regex patterns")
   void shouldNotProcessNonMatchingTraceEventsWithWildcard() throws IOException {
      // Given
      String servicePattern = "user-.*";
      String operationPattern = "log.*";
      String clientAddress = "127.0.0.1";
      String traceId = "trace-999";
      String actualServiceName = "order-service"; // doesn't match user-.*
      String actualOperation = "login"; // matches log.*
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, servicePattern, operationPattern, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, actualServiceName, actualOperation, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should not send trace event since service doesn't match regex pattern
      verify(mockEmitter, never()).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should send initial heartbeat when subscribing with custom emitter")
   void shouldSendInitialHeartbeatWhenSubscribingWithCustomEmitter() throws IOException {
      // Given
      SseEmitter mockEmitter = mock(SseEmitter.class);

      // When
      traceSubscriptionManager.subscribe(mockEmitter, "service", "operation", "127.0.0.1");

      // Then
      verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should handle emitter IOException during event sending")
   void shouldHandleEmitterIOExceptionDuringEventSending() throws IOException {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      String clientAddress = "127.0.0.1";
      String traceId = "trace-123";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, serviceName, operationName, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      // Mock emitter to throw IOException when sending trace data
      doThrow(new IOException("Connection lost")).when(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));

      TraceEvent event = new TraceEvent(traceId, serviceName, operationName, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
      verify(mockEmitter).complete(); // Should complete the emitter on error
   }

   @Test
   @DisplayName("Should match client address with regex pattern")
   void shouldMatchClientAddressWithWildcard() throws IOException {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      String clientAddressPattern = "192\\.168\\..*";
      String actualClientAddress = "192.168.1.100";
      String traceId = "trace-123";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, serviceName, operationName, clientAddressPattern);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, serviceName, operationName, actualClientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should send trace event since client address matches regex pattern
      verify(mockEmitter, atLeast(1)).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should handle exact string patterns when regex fails")
   void shouldHandleExactStringPatternsWhenRegexFails() throws IOException {
      // Given
      String serviceName = "user-service";
      String operationName = "login";
      String clientAddress = "127.0.0.1";
      String traceId = "trace-123";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, serviceName, operationName, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, serviceName, operationName, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should send trace event since exact string matches
      verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
   }

   @Test
   @DisplayName("Should support complex regex patterns")
   void shouldSupportComplexRegexPatterns() throws IOException {
      // Given
      String servicePattern = "(user|admin)-service";
      String operationPattern = "log(in|out)";
      String clientAddress = "127.0.0.1";
      String traceId = "trace-123";
      String actualServiceName = "admin-service";
      String actualOperation = "logout";
      SseEmitter mockEmitter = mock(SseEmitter.class);

      traceSubscriptionManager.subscribe(mockEmitter, servicePattern, operationPattern, clientAddress);

      // Reset mock to clear the initial heartbeat call
      reset(mockEmitter);

      List<SpanData> spans = List.of(Mockito.mock(SpanData.class));
      when(mockSpanStorageService.getSpansForTrace(traceId)).thenReturn(spans);

      TraceEvent event = new TraceEvent(traceId, actualServiceName, actualOperation, clientAddress);

      // When
      traceSubscriptionManager.onTraceUpdated(event);

      // Then
      verify(mockSpanStorageService).getSpansForTrace(traceId);
      // Should send trace event since complex regex matches
      verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
   }
}
