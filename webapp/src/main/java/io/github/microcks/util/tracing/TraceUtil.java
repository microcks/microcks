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
package io.github.microcks.util.tracing;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;

/**
 * Utility class for managing tracing functionality. This class provides constants and methods to enable explain tracing
 * in the current context.
 *
 * @author microcks-team
 */
public final class TraceUtil {

   /**
    * The attribute key used to mark spans for explain tracing. When this attribute is set to true on a span, the span
    * will be captured by the CustomExplainTraceProcessor and stored in the SpanStorageService.
    */
   public static final AttributeKey<Boolean> EXPLAIN_TRACE_ATTRIBUTE = AttributeKey.booleanKey("explain-trace");

   /**
    * Private constructor to prevent instantiation of utility class.
    */
   private TraceUtil() {
      throw new UnsupportedOperationException("Utility class cannot be instantiated");
   }

   /**
    * Enables explain tracing for the current span context. This method adds the explain-trace attribute to the current
    * active span, marking it for capture and storage by the tracing infrastructure. If there is no active span in the
    * current context, this method will have no effect
    *
    * @return true if explain tracing was successfully enabled on the current span, false if there was no active span or
    *         if the operation failed
    */
   public static boolean enableExplainTracing() {
      Span currentSpan = Span.current();

      // Check if there's an active span
      SpanContext spanContext = currentSpan.getSpanContext();
      if (!spanContext.isValid()) {
         return false;
      }

      try {
         // Add the explain-trace attribute to the current span
         currentSpan.setAttribute(EXPLAIN_TRACE_ATTRIBUTE, true);
         return true;
      } catch (Exception e) {
         // Log the error but don't throw to avoid disrupting application flow
         // In a real application, you might want to use a proper logger here
         System.err.println("Failed to enable explain tracing: " + e.getMessage());
         return false;
      }
   }

   /**
    * Builder Wrapper enforcing specific attributes on an event. An event has a message attribute which is used as the
    * event name in tracing UIs.
    */
   public static AttributesBuilder explainSpanEventBuilder(String message) {
      return Attributes.builder().put("message", message);
   }

}
