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

import io.github.microcks.event.TraceEvent;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.PatternSyntaxException;

/**
 * Utility class for filtering spans based on service attributes.
 * @author Apoorva Srinivas Appadoo
 */
public class SpanFilterUtil {

   private SpanFilterUtil() {
      // Utility class - prevent instantiation
   }

   /**
    * Check if a value matches a pattern with optional regex support.
    * @param pattern The pattern to match against (can be null, "*", or regex pattern).
    * @param value   The value to check (can be null).
    * @return True if the value matches the pattern, false otherwise.
    */
   public static boolean matchesWildcard(String pattern, String value) {
      if (pattern == null || value == null) {
         return pattern == null && value == null;
      }

      // If pattern is "*", match everything
      if ("*".equals(pattern)) {
         return true;
      }

      // Try exact match first
      if (pattern.equals(value)) {
         return true;
      }

      // Try regex match
      try {
         return value.matches(pattern);
      } catch (PatternSyntaxException e) {
         // Invalid regex pattern, fallback to false
         return false;
      }
   }

   /**
    * Check if a TraceEvent matches the provided filters.
    * @param traceEvent    TraceEvent to check
    * @param serviceName   Service name pattern (can be null, "*", or regex)
    * @param operationName Operation name pattern (can be null, "*", or regex)
    * @param clientAddress Client address pattern (can be null, "*", or regex)
    * @return True if the span matches all filters, false otherwise
    */
   public static boolean matchesTraceEvent(TraceEvent traceEvent, String serviceName, String operationName,
         String clientAddress) {
      if (traceEvent == null) {
         return false;
      }
      return matchesWildcard(serviceName, traceEvent.service())
            && matchesWildcard(operationName, traceEvent.operation())
            && (clientAddress == null || matchesWildcard(clientAddress, traceEvent.clientAddress()));
   }

   /**
    * Extract TraceEvent from spans
    * @param traceId the trace ID
    * @param spans   the list of spans
    * @return the TraceEvent
    */
   public static TraceEvent extractTraceEvent(String traceId, List<SpanData> spans) {
      if (spans == null || spans.isEmpty()) {
         return null;
      }

      String service = null;
      String operation = null;
      String clientAddress = null;

      for (SpanData s : spans) {
         if (isValidSpan(s)) {
            Map<AttributeKey<?>, Object> attributes = s.getAttributes().asMap();

            service = extractAttributeIfPresent(attributes, CommonAttributes.SERVICE_NAME, service);
            operation = extractAttributeIfPresent(attributes, CommonAttributes.OPERATION_NAME, operation);
            clientAddress = extractClientAddress(s, clientAddress);

            if (allAttributesFound(service, operation, clientAddress)) {
               break;
            }
         }
      }

      return new TraceEvent(traceId, service, operation, clientAddress);
   }

   private static boolean isValidSpan(SpanData span) {
      return span != null;
   }

   private static String extractAttributeIfPresent(Map<AttributeKey<?>, Object> attributes, AttributeKey<String> key,
         String currentValue) {
      String attributeValue = (String) attributes.get(key);
      return attributeValue != null ? attributeValue : currentValue;
   }

   private static String extractClientAddress(SpanData span, String currentClientAddress) {
      Optional<EventData> invocationReceivedEvent = span.getEvents().stream()
            .filter(e -> CommonEvents.INVOCATION_RECEIVED.getEventName().equals(e.getName())).findFirst();

      if (invocationReceivedEvent.isPresent()) {
         String clientAddressAttribute = invocationReceivedEvent.get().getAttributes()
               .get(CommonAttributes.CLIENT_ADDRESS);
         if (clientAddressAttribute != null) {
            return clientAddressAttribute;
         }
      }

      return currentClientAddress;
   }

   private static boolean allAttributesFound(String service, String operation, String clientAddress) {
      return service != null && operation != null && clientAddress != null;
   }
}
