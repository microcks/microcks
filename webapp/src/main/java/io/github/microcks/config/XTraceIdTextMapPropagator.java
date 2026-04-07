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
package io.github.microcks.config;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A custom {@link TextMapPropagator} that extracts and injects trace context using {@code X-Trace-Id} and optionally
 * {@code X-Span-Id} headers. This allows Microcks to join distributed traces started by external systems that use these
 * custom headers rather than the standard W3C {@code traceparent} format.
 *
 * On <b>extraction</b>, if the context already carries a valid remote span (e.g. set by the W3C or B3 propagator that
 * ran earlier in the composite chain), this propagator is a no-op so it never overrides a standard context.
 *
 * On <b>injection</b>, the current trace-id and span-id are written into the two custom headers so that downstream
 * services that understand only {@code X-Trace-Id} can still correlate.
 *
 * @author laurent
 */
public class XTraceIdTextMapPropagator implements TextMapPropagator {

   /** A commons logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(XTraceIdTextMapPropagator.class);

   /** Header carrying the 128-bit (32 hex chars) or shorter trace identifier. */
   static final String X_TRACE_ID = "X-Trace-Id";
   /** Optional header carrying the 64-bit (16 hex chars) parent span identifier. */
   static final String X_SPAN_ID = "X-Span-Id";

   private static final int TRACE_ID_HEX_LENGTH = 32;
   private static final int SPAN_ID_HEX_LENGTH = 16;
   private static final String HEX_PATTERN = "[0-9a-f]+";

   @Override
   public Collection<String> fields() {
      return List.of(X_TRACE_ID, X_SPAN_ID);
   }

   @Override
   public <C> void inject(@Nonnull Context context, C carrier, @Nonnull TextMapSetter<C> setter) {
      SpanContext spanContext = Span.fromContext(context).getSpanContext();
      if (spanContext.isValid()) {
         setter.set(carrier, X_TRACE_ID, spanContext.getTraceId());
         setter.set(carrier, X_SPAN_ID, spanContext.getSpanId());
      }
   }

   @Override
   public <C> Context extract(@Nonnull Context context, C carrier, @Nonnull TextMapGetter<C> getter) {
      // If a previous propagator (W3C / B3) already set a valid remote parent, do not override it.
      SpanContext existing = Span.fromContext(context).getSpanContext();
      if (existing.isValid() && existing.isRemote()) {
         return context;
      }

      String rawTraceId = getter.get(carrier, X_TRACE_ID);
      if (rawTraceId == null || rawTraceId.isBlank()) {
         return context;
      }

      String traceId = normalizeTraceId(rawTraceId);
      if (traceId == null) {
         log.warn("Ignoring malformed X-Trace-Id header value: {}", rawTraceId);
         return context;
      }

      String rawSpanId = getter.get(carrier, X_SPAN_ID);
      String spanId = normalizeSpanId(rawSpanId);

      SpanContext remoteParent = SpanContext.createFromRemoteParent(traceId, spanId, TraceFlags.getSampled(),
            TraceState.getDefault());
      log.debug("Extracted remote parent from X-Trace-Id: traceId={}, spanId={}", traceId, spanId);
      return context.with(Span.wrap(remoteParent));
   }

   // ---- helpers ----

   /**
    * Normalize a raw trace ID value to a 32 hex-char string. Strips dashes (UUID format) and zero-pads on the left if
    * needed. Returns {@code null} when the value cannot be interpreted as a hex trace ID.
    */
   static String normalizeTraceId(String raw) {
      String cleaned = raw.replace("-", "").trim().toLowerCase();
      if (!cleaned.matches(HEX_PATTERN) || cleaned.isEmpty()) {
         return null;
      }
      if (cleaned.length() < TRACE_ID_HEX_LENGTH) {
         cleaned = "0".repeat(TRACE_ID_HEX_LENGTH - cleaned.length()) + cleaned;
      } else if (cleaned.length() > TRACE_ID_HEX_LENGTH) {
         cleaned = cleaned.substring(0, TRACE_ID_HEX_LENGTH);
      }
      // An all-zero trace id is invalid per the spec.
      if (cleaned.chars().allMatch(c -> c == '0')) {
         return null;
      }
      return cleaned;
   }

   /**
    * Normalize a raw span ID value to a 16 hex-char string. When the incoming value is absent or malformed a random
    * span ID is generated so that the extracted context is always complete.
    */
   static String normalizeSpanId(String raw) {
      if (raw == null || raw.isBlank()) {
         return generateRandomSpanId();
      }
      String cleaned = raw.replace("-", "").trim().toLowerCase();
      if (!cleaned.matches(HEX_PATTERN) || cleaned.isEmpty()) {
         return generateRandomSpanId();
      }
      if (cleaned.length() < SPAN_ID_HEX_LENGTH) {
         cleaned = "0".repeat(SPAN_ID_HEX_LENGTH - cleaned.length()) + cleaned;
      } else if (cleaned.length() > SPAN_ID_HEX_LENGTH) {
         cleaned = cleaned.substring(0, SPAN_ID_HEX_LENGTH);
      }
      if (cleaned.chars().allMatch(c -> c == '0')) {
         return generateRandomSpanId();
      }
      return cleaned;
   }

   private static String generateRandomSpanId() {
      return String.format("%016x", ThreadLocalRandom.current().nextLong());
   }
}

