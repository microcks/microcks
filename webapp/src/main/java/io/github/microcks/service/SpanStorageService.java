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
import io.github.microcks.util.tracing.SpanFilterUtil;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.ReadableSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for storing and retrieving OpenTelemetry spans organized by trace ID.
 */
@Service
public class SpanStorageService {

   private static final Logger log = LoggerFactory.getLogger(SpanStorageService.class);

   /**
    * Thread-safe LinkedHashMap storing lists of spans grouped by trace ID. LinkedHashMap maintains insertion order to
    * allow eviction. Key: trace ID (String), Value: List of spans for that trace
    */
   private final Map<String, List<ReadableSpan>> spansByTraceId = Collections.synchronizedMap(new LinkedHashMap<>());

   /**
    * Maximum number of traces to keep in memory to prevent memory leaks. When this limit is exceeded, oldest traces are
    * removed.
    */
   private static final int MAX_TRACES = 1000;

   /**
    * Maximum number of spans to keep per trace to prevent memory issues.
    */
   private static final int MAX_SPANS_PER_TRACE = 100;

   /**
    * Publisher for notifying listeners about new root spans (new traces).
    */
   private final ApplicationEventPublisher publisher;

   public SpanStorageService(ApplicationEventPublisher publisher) {
      this.publisher = publisher;
   }


   /**
    * Stores a span in the service, grouped by trace ID.
    *
    * @param span The span to store
    */
   public void storeSpan(ReadableSpan span) {
      String traceId = span.getSpanContext().getTraceId();

      // Add the span to our collection
      spansByTraceId.computeIfAbsent(traceId, k -> new ArrayList<>()).add(span);

      // Limit spans per trace to prevent memory issues
      List<ReadableSpan> spans = spansByTraceId.get(traceId);
      if (spans.size() > MAX_SPANS_PER_TRACE) {
         // Remove oldest span to cap memory
         spans.removeFirst();
      }

      // Limit total number of traces to prevent memory leaks
      if (spansByTraceId.size() > MAX_TRACES) {
         // Remove oldest trace this works because LinkedHashMap maintains insertion order
         String oldestTraceId = spansByTraceId.keySet().iterator().next();
         spansByTraceId.remove(oldestTraceId);
      }

      // Publish a notification if this is a root span (no parent)
      if (!span.getParentSpanContext().isValid()) {
         publisher.publishEvent(SpanFilterUtil.extractTraceEvent(traceId, spans));
      }
   }

   /**
    * Retrieves all spans for a given trace ID.
    *
    * @param traceId The trace ID to look up
    * @return A list of spans for the given trace ID, or an empty list if no spans found
    */
   public List<ReadableSpan> getSpansForTrace(String traceId) {
      return spansByTraceId.getOrDefault(traceId, new ArrayList<>());
   }

   /**
    * Clears all stored spans and traces from memory. Useful for testing or when you want to reset the service state.
    */
   public void clearAll() {
      log.info("Clearing all stored spans and traces from SpanStorageService");
      spansByTraceId.clear();
   }

   /**
    * Retrieves trace IDs that have spans matching all specified attributes.
    *
    * @param requiredAttributes Map of attribute key -> expected value
    * @return A List of trace IDs that have spans matching all the provided attributes sorted by recency (most recent
    *         first)
    */
   public List<String> queryTraceIdsBySpanAttributes(Map<AttributeKey<?>, Object> requiredAttributes) {
      if (requiredAttributes == null || requiredAttributes.isEmpty()) {
         return new ArrayList<>(spansByTraceId.keySet());
      }
      return spansByTraceId.entrySet().stream()
            .filter(entry -> entry.getValue().stream()
                  .anyMatch(span -> requiredAttributes.entrySet().stream()
                        .allMatch(reqAttr -> valuesEqualAttr(span.toSpanData().getAttributes().get(reqAttr.getKey()),
                              reqAttr.getValue()))))
            .map(Map.Entry::getKey)
            // Sort by recency - most recent first
            .sorted(this::compareSpansByEndTime).toList();
   }

   /**
    * Query trace IDs by span attributes with support for regex patterns.
    *
    * @param serviceName   Service name pattern (can be null, "*", or regex)
    * @param operationName Operation name pattern (can be null, "*", or regex)
    * @param clientAddress Client address pattern (can be null, "*", or regex)
    * @return A List of trace IDs that have spans matching all the provided patterns sorted by recency (most recent
    *         first)
    */
   public List<String> queryTraceIdsByPatterns(String serviceName, String operationName, String clientAddress) {
      return spansByTraceId.entrySet().stream()
            .map(entry -> SpanFilterUtil.extractTraceEvent(entry.getKey(), entry.getValue()))
            .filter(event -> SpanFilterUtil.matchesTraceEvent(event, serviceName, operationName, clientAddress))
            .map(TraceEvent::traceId)
            // Sort by recency - most recent first
            .sorted(this::compareSpansByEndTime).toList();
   }

   public static boolean valuesEqualAttr(Object actual, Object expected) {
      if (actual == null && expected == null)
         return true;
      if (actual == null || expected == null)
         return false;
      if (actual.getClass().isAssignableFrom(expected.getClass())
            || expected.getClass().isAssignableFrom(actual.getClass())) {
         return actual.equals(expected);
      }
      // Fallback: compare string representations to allow comparing numbers to strings, etc.
      return String.valueOf(actual).equals(String.valueOf(expected));
   }

   /**
    * Get all trace IDs currently stored.
    *
    * @return Set of trace IDs
    */
   public Set<String> getAllTraceIds() {
      return spansByTraceId.keySet();
   }

   private int compareSpansByEndTime(String id1, String id2) {
      List<ReadableSpan> spans1 = spansByTraceId.get(id1);
      List<ReadableSpan> spans2 = spansByTraceId.get(id2);
      if (spans1.isEmpty() || spans2.isEmpty())
         return 0;
      long endTime1 = spans1.stream().mapToLong(s -> s.toSpanData().getEndEpochNanos()).max().orElse(0);
      long endTime2 = spans2.stream().mapToLong(s -> s.toSpanData().getEndEpochNanos()).max().orElse(0);
      return Long.compare(endTime2, endTime1); // Descending order
   }
}
