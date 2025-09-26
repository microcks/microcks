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

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test case for SpanStorageService class.
 */
class SpanStorageServiceTest {

   private SpanStorageService spanStorageService;

   @BeforeEach
   void setUp() {
      spanStorageService = new SpanStorageService();
   }

   @Test
   @DisplayName("Should store and retrieve spans by trace ID")
   void shouldStoreAndRetrieveSpansByTraceId() {
      // Given
      String traceId = "trace-123";
      ReadableSpan span1 = createMockSpan(traceId, "span-1");
      ReadableSpan span2 = createMockSpan(traceId, "span-2");

      // When
      spanStorageService.storeSpan(span1);
      spanStorageService.storeSpan(span2);

      // Then
      List<ReadableSpan> retrievedSpans = spanStorageService.getSpansForTrace(traceId);
      assertEquals(2, retrievedSpans.size());
      assertTrue(retrievedSpans.contains(span1));
      assertTrue(retrievedSpans.contains(span2));
   }

   @Test
   @DisplayName("Should return empty list for non-existent trace ID")
   void shouldReturnEmptyListForNonExistentTraceId() {
      // When
      List<ReadableSpan> spans = spanStorageService.getSpansForTrace("non-existent-trace");

      // Then
      assertTrue(spans.isEmpty());
   }

   @Test
   @DisplayName("Should limit spans per trace")
   void shouldLimitSpansPerTrace() {
      // Given
      String traceId = "trace-with-many-spans";
      int maxSpansPerTrace = 100; // From SpanStorageService.MAX_SPANS_PER_TRACE

      // When - Add more spans than the limit
      for (int i = 0; i < maxSpansPerTrace + 10; i++) {
         ReadableSpan span = createMockSpan(traceId, "span-" + i);
         spanStorageService.storeSpan(span);
      }

      // Then
      List<ReadableSpan> spans = spanStorageService.getSpansForTrace(traceId);
      assertEquals(maxSpansPerTrace, spans.size());
   }

   @Test
   @DisplayName("Should remove oldest traces when memory limit exceeded")
   void shouldRemoveOldestTracesWhenMemoryLimitExceeded() {
      // Given
      int maxTraces = 1000; // From SpanStorageService.MAX_TRACES

      // When - Add more traces than the limit
      for (int i = 0; i < maxTraces + 5; i++) {
         String traceId = "trace-" + i;
         ReadableSpan span = createMockSpan(traceId, "span-" + i);
         spanStorageService.storeSpan(span);
      }

      // Then
      Set<String> allTraceIds = spanStorageService.getAllTraceIds();
      assertEquals(maxTraces, allTraceIds.size());

      // The oldest traces (trace-0, trace-1, etc.) should be removed
      assertFalse(allTraceIds.contains("trace-0"));
      assertFalse(allTraceIds.contains("trace-1"));
      assertFalse(allTraceIds.contains("trace-2"));
      assertFalse(allTraceIds.contains("trace-3"));
      assertFalse(allTraceIds.contains("trace-4"));

      // The newest traces should still be present
      assertTrue(allTraceIds.contains("trace-" + (maxTraces + 4)));
      assertTrue(allTraceIds.contains("trace-" + (maxTraces + 3)));
   }

   @Test
   @DisplayName("Should clear all spans and traces")
   void shouldClearAllSpansAndTraces() {
      // Given
      spanStorageService.storeSpan(createMockSpan("trace-1", "span-1"));
      spanStorageService.storeSpan(createMockSpan("trace-2", "span-2"));

      // When
      spanStorageService.clearAll();

      // Then
      assertTrue(spanStorageService.getAllTraceIds().isEmpty());
      assertTrue(spanStorageService.getSpansForTrace("trace-1").isEmpty());
      assertTrue(spanStorageService.getSpansForTrace("trace-2").isEmpty());
   }

   @Test
   @DisplayName("Should query trace IDs by span attributes")
   void shouldQueryTraceIdsBySpanAttributes() {
      // Given
      AttributeKey<String> serviceNameKey = AttributeKey.stringKey("service.name");
      AttributeKey<String> operationKey = AttributeKey.stringKey("operation");

      ReadableSpan span1 = createMockSpanWithAttributes("trace-1", "span-1",
            Attributes.builder().put(serviceNameKey, "user-service").put(operationKey, "login").build());

      ReadableSpan span2 = createMockSpanWithAttributes("trace-2", "span-2",
            Attributes.builder().put(serviceNameKey, "user-service").put(operationKey, "logout").build());

      ReadableSpan span3 = createMockSpanWithAttributes("trace-3", "span-3",
            Attributes.builder().put(serviceNameKey, "order-service").put(operationKey, "create").build());

      spanStorageService.storeSpan(span1);
      spanStorageService.storeSpan(span2);
      spanStorageService.storeSpan(span3);

      // When
      Map<AttributeKey<?>, Object> requiredAttributes = new HashMap<>();
      requiredAttributes.put(serviceNameKey, "user-service");

      List<String> matchingTraceIds = spanStorageService.queryTraceIdsBySpanAttributes(requiredAttributes);

      // Then
      assertEquals(2, matchingTraceIds.size());
      assertTrue(matchingTraceIds.contains("trace-1"));
      assertTrue(matchingTraceIds.contains("trace-2"));
      assertFalse(matchingTraceIds.contains("trace-3"));
   }

   @Test
   @DisplayName("Should return all trace IDs when no attributes specified")
   void shouldReturnAllTraceIdsWhenNoAttributesSpecified() {
      // Given
      spanStorageService.storeSpan(createMockSpan("trace-1", "span-1"));
      spanStorageService.storeSpan(createMockSpan("trace-2", "span-2"));
      spanStorageService.storeSpan(createMockSpan("trace-3", "span-3"));

      // When
      List<String> allTraceIds = spanStorageService.queryTraceIdsBySpanAttributes(null);

      // Then
      assertEquals(3, allTraceIds.size());
      assertTrue(allTraceIds.contains("trace-1"));
      assertTrue(allTraceIds.contains("trace-2"));
      assertTrue(allTraceIds.contains("trace-3"));
   }

   @Test
   @DisplayName("Should handle concurrent access safely")
   void shouldHandleConcurrentAccessSafely() throws InterruptedException {
      // Given
      int numberOfThreads = 10;
      int spansPerThread = 50;
      try (ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads)) {
         CountDownLatch latch = new CountDownLatch(numberOfThreads);

         // When - Multiple threads storing spans concurrently
         for (int i = 0; i < numberOfThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
               try {
                  for (int j = 0; j < spansPerThread; j++) {
                     String traceId = "trace-" + threadId + "-" + j;
                     ReadableSpan span = createMockSpan(traceId, "span-" + j);
                     spanStorageService.storeSpan(span);
                  }
               } finally {
                  latch.countDown();
               }
            });
         }

         // Wait for all threads to complete
         assertTrue(latch.await(10, TimeUnit.SECONDS));
      }

      // Then - Verify data integrity
      Set<String> allTraceIds = spanStorageService.getAllTraceIds();
      assertEquals(numberOfThreads * spansPerThread, allTraceIds.size());

      // Verify we can read all stored spans without exceptions
      for (String traceId : allTraceIds) {
         List<ReadableSpan> spans = spanStorageService.getSpansForTrace(traceId);
         assertEquals(1, spans.size());
      }
   }

   @Test
   @DisplayName("Should test valuesEqualAttr utility method")
   void shouldTestValuesEqualAttrUtilityMethod() {
      // Test null values
      assertTrue(SpanStorageService.valuesEqualAttr(null, null));
      assertFalse(SpanStorageService.valuesEqualAttr("value", null));
      assertFalse(SpanStorageService.valuesEqualAttr(null, "value"));

      // Test equal values
      assertTrue(SpanStorageService.valuesEqualAttr("test", "test"));
      assertTrue(SpanStorageService.valuesEqualAttr(123, 123));

      // Test different types with same string representation
      assertTrue(SpanStorageService.valuesEqualAttr(123, "123"));
      assertTrue(SpanStorageService.valuesEqualAttr("123", 123));

      // Test different values
      assertFalse(SpanStorageService.valuesEqualAttr("test1", "test2"));
      assertFalse(SpanStorageService.valuesEqualAttr(123, 456));
   }

   @Test
   @DisplayName("Should maintain trace order for eviction")
   void shouldMaintainTraceOrderForLRUEviction() {
      // Given - Add a few traces
      spanStorageService.storeSpan(createMockSpan("trace-1", "span-1"));
      spanStorageService.storeSpan(createMockSpan("trace-2", "span-2"));
      spanStorageService.storeSpan(createMockSpan("trace-3", "span-3"));

      // When - Add more spans to an existing trace (should not change its position)
      spanStorageService.storeSpan(createMockSpan("trace-1", "span-1-2"));

      // Then - Verify the trace is still there and has multiple spans
      List<ReadableSpan> trace1Spans = spanStorageService.getSpansForTrace("trace-1");
      assertEquals(2, trace1Spans.size());
   }

   // Helper methods for creating mock spans

   private ReadableSpan createMockSpan(String traceId, String spanId) {
      return createMockSpanWithAttributes(traceId, spanId, Attributes.empty());
   }

   private ReadableSpan createMockSpanWithAttributes(String traceId, String spanId, Attributes attributes) {
      ReadableSpan span = Mockito.mock(ReadableSpan.class);
      SpanContext spanContext = Mockito.mock(SpanContext.class);
      SpanData spanData = createMockSpanData(attributes);

      // Mock the SpanContext to return the correct trace ID
      when(spanContext.getTraceId()).thenReturn(traceId);
      when(spanContext.getSpanId()).thenReturn(spanId);
      when(spanContext.getTraceFlags()).thenReturn(TraceFlags.getDefault());
      when(spanContext.getTraceState()).thenReturn(TraceState.getDefault());

      when(span.getSpanContext()).thenReturn(spanContext);
      when(span.toSpanData()).thenReturn(spanData);

      return span;
   }

   private SpanData createMockSpanData(Attributes attributes) {
      SpanData spanData = Mockito.mock(SpanData.class);
      when(spanData.getAttributes()).thenReturn(attributes);
      when(spanData.getEndEpochNanos()).thenReturn(System.nanoTime());
      when(spanData.getName()).thenReturn("test-span");
      when(spanData.getKind()).thenReturn(io.opentelemetry.api.trace.SpanKind.INTERNAL);
      when(spanData.getResource()).thenReturn(Resource.getDefault());
      when(spanData.getInstrumentationScopeInfo()).thenReturn(InstrumentationScopeInfo.empty());

      return spanData;
   }
}
