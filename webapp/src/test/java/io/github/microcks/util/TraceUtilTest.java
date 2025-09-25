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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for TraceUtil functionality.
 */
class TraceUtilTest {

   @Test
   @DisplayName("Should have correct explain-trace attribute constant")
   void testExplainTraceAttributeConstant() {
      // Verify the constant is correctly defined
      assertThat(TraceUtil.EXPLAIN_TRACE_ATTRIBUTE).isNotNull();
      assertThat(TraceUtil.EXPLAIN_TRACE_ATTRIBUTE.getKey()).isEqualTo("explain-trace");
   }

   @Test
   @DisplayName("Should return false when enabling explain tracing with no current span")
   void testEnableExplainTracingWithNoCurrentSpan() {
      // Try to enable explain tracing when no span is current
      boolean result = TraceUtil.enableExplainTracing();

      // Should return false when no span is current
      assertThat(result).isFalse();
   }
}
