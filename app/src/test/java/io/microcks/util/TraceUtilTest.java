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
package io.github.microcks.util;

import io.github.microcks.util.tracing.TraceUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Test class for TraceUtil functionality.
 */
@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class TraceUtilTest {
   @Test
   @DisplayName("Should return false when enabling explain tracing with no current span")
   void testEnableExplainTracingWithNoCurrentSpan() {
      // Try to enable explain tracing when no span is current
      boolean result = TraceUtil.enableExplainTracing();

      // Should return false when no span is current
      assertThat(result).isFalse();
   }

   @Test
   @DisplayName("Should return true when enabling explain tracing with a current span")
   void testEnableExplainTracingWithCurrentSpan() throws Exception {
      var span = mock(io.opentelemetry.api.trace.Span.class);
      when(span.getSpanContext()).thenReturn(mock(io.opentelemetry.api.trace.SpanContext.class));
      when(span.getSpanContext().isValid()).thenReturn(true);
      try (var currentSpan = mockStatic(io.opentelemetry.api.trace.Span.class)) {
         currentSpan.when(io.opentelemetry.api.trace.Span::current).thenReturn(span);
         boolean result = TraceUtil.enableExplainTracing();
         assertThat(result).isTrue();
         verify(span).setAttribute(TraceUtil.EXPLAIN_TRACE_ATTRIBUTE, true);
      }
   }
}
