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
package io.github.microcks.util.otel;

import io.github.microcks.service.SpanStorageService;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * Test case for CustomExplainTraceProcessor class.
 */
@ExtendWith(MockitoExtension.class)
class CustomExplainTraceProcessorTest {

   @Mock
   private SpanStorageService mockSpanStorageService;

   private CustomExplainTraceProcessor processor;

   @BeforeEach
   void setUp() {
      processor = new CustomExplainTraceProcessor(mockSpanStorageService);
   }

   @Test
   @DisplayName("Should store span when it has explain-trace attribute")
   void shouldStoreSpanWithExplainTraceAttribute() {
      // Given
      ReadableSpan mockSpan = createMockSpanWithExplainTrace(true);

      // When
      processor.onEnd(mockSpan);

      // Then
      verify(mockSpanStorageService).storeSpan(mockSpan);
   }

   @Test
   @DisplayName("Should not store span when it doesn't have explain-trace attribute")
   void shouldNotStoreSpanWithoutExplainTraceAttribute() {
      // Given
      ReadableSpan mockSpan = createMockSpanWithExplainTrace(false);

      // When
      processor.onEnd(mockSpan);

      // Then
      verify(mockSpanStorageService, never()).storeSpan(any());
   }

   @Test
   @DisplayName("Should not store span when it has other attributes but not explain-trace")
   void shouldNotStoreSpanWithOtherAttributes() {
      // Given
      ReadableSpan mockSpan = createMockSpanWithOtherAttributes();

      // When
      processor.onEnd(mockSpan);

      // Then
      verify(mockSpanStorageService, never()).storeSpan(any());
   }

   @Test
   @DisplayName("Should clear all spans on close")
   void shouldClearAllSpansOnClose() {
      // When
      processor.close();

      // Then
      verify(mockSpanStorageService).clearAll();
   }

   @Test
   @DisplayName("Should handle span with empty attributes")
   void shouldHandleSpanWithEmptyAttributes() {
      // Given
      ReadableSpan mockSpan = createMockSpanWithEmptyAttributes();

      // When
      processor.onEnd(mockSpan);

      // Then
      verify(mockSpanStorageService, never()).storeSpan(any());
   }

   @Test
   @DisplayName("Should handle span with null span data attributes")
   void shouldHandleSpanWithNullAttributes() {
      // Given
      ReadableSpan mockSpan = mock(ReadableSpan.class);
      SpanData mockSpanData = mock(SpanData.class);
      when(mockSpan.toSpanData()).thenReturn(mockSpanData);
      when(mockSpanData.getAttributes()).thenReturn(Attributes.empty());

      // When
      processor.onEnd(mockSpan);

      // Then
      verify(mockSpanStorageService, never()).storeSpan(any());
   }

   @Test
   @DisplayName("Should handle multiple spans with mixed attributes")
   void shouldHandleMultipleSpansWithMixedAttributes() {
      // Given
      ReadableSpan spanWithExplainTrace = createMockSpanWithExplainTrace(true);
      ReadableSpan spanWithoutExplainTrace = createMockSpanWithExplainTrace(false);
      ReadableSpan spanWithOtherAttributes = createMockSpanWithOtherAttributes();

      // When
      processor.onEnd(spanWithExplainTrace);
      processor.onEnd(spanWithoutExplainTrace);
      processor.onEnd(spanWithOtherAttributes);

      // Then
      verify(mockSpanStorageService, times(1)).storeSpan(spanWithExplainTrace);
      verify(mockSpanStorageService, never()).storeSpan(spanWithoutExplainTrace);
      verify(mockSpanStorageService, never()).storeSpan(spanWithOtherAttributes);
   }

   // Helper methods for creating mock spans

   private ReadableSpan createMockSpanWithExplainTrace(boolean hasAttribute) {
      ReadableSpan mockSpan = mock(ReadableSpan.class);
      SpanData mockSpanData = mock(SpanData.class);

      Attributes attributes;
      if (hasAttribute) {
         attributes = Attributes.of(AttributeKey.booleanKey("explain-trace"), true);
      } else {
         attributes = Attributes.of(AttributeKey.stringKey("other-attribute"), "value");
      }

      when(mockSpan.toSpanData()).thenReturn(mockSpanData);
      when(mockSpanData.getAttributes()).thenReturn(attributes);

      return mockSpan;
   }


   private ReadableSpan createMockSpanWithOtherAttributes() {
      ReadableSpan mockSpan = mock(ReadableSpan.class);
      SpanData mockSpanData = mock(SpanData.class);

      Attributes attributes = Attributes.builder().put(AttributeKey.stringKey("service.name"), "test-service")
            .put(AttributeKey.stringKey("operation.name"), "test-operation")
            .put(AttributeKey.longKey("duration"), 1000L).build();

      when(mockSpan.toSpanData()).thenReturn(mockSpanData);
      when(mockSpanData.getAttributes()).thenReturn(attributes);

      return mockSpan;
   }

   private ReadableSpan createMockSpanWithEmptyAttributes() {
      ReadableSpan mockSpan = mock(ReadableSpan.class);
      SpanData mockSpanData = mock(SpanData.class);

      when(mockSpan.toSpanData()).thenReturn(mockSpanData);
      when(mockSpanData.getAttributes()).thenReturn(Attributes.empty());

      return mockSpan;
   }
}
