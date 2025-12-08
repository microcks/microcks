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
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpanFilterUtilTest {

   @Test
   void testMatchesWildcard_exactMatch() {
      assertTrue(SpanFilterUtil.matchesWildcard("service-name", "service-name"));
      assertFalse(SpanFilterUtil.matchesWildcard("service-name", "other-name"));
   }

   @Test
   void testMatchesWildcard_wildcardPattern() {
      assertTrue(SpanFilterUtil.matchesWildcard("*", "any-value"));
      assertTrue(SpanFilterUtil.matchesWildcard("*", ""));
   }

   @Test
   void testMatchesWildcard_nullValues() {
      assertTrue(SpanFilterUtil.matchesWildcard(null, null));
      assertFalse(SpanFilterUtil.matchesWildcard("pattern", null));
      assertFalse(SpanFilterUtil.matchesWildcard(null, "value"));
   }

   @Test
   void testMatchesWildcard_regexPattern() {
      // Test regex patterns
      assertTrue(SpanFilterUtil.matchesWildcard(".*-service", "user-service"));
      assertTrue(SpanFilterUtil.matchesWildcard(".*-service", "order-service"));
      assertFalse(SpanFilterUtil.matchesWildcard(".*-service", "standalone"));

      assertTrue(SpanFilterUtil.matchesWildcard("log.*", "logback"));
      assertTrue(SpanFilterUtil.matchesWildcard("log.*", "logging"));
      assertFalse(SpanFilterUtil.matchesWildcard("log.*", "catalog"));

      assertTrue(SpanFilterUtil.matchesWildcard("192\\.168\\..*", "192.168.1.100"));
      assertFalse(SpanFilterUtil.matchesWildcard("192\\.168\\..*", "10.0.0.1"));
   }

   @Test
   void testMatchesWildcard_invalidRegexFallsBackToExactMatch() {
      // Invalid regex should fall back to exact string match
      assertTrue(SpanFilterUtil.matchesWildcard("[invalid", "[invalid"));
      assertFalse(SpanFilterUtil.matchesWildcard("[invalid", "other"));
   }

   @Test
   void testMatchesTraceEvent() {
      // Mock a SpanData with specific attributes
      SpanData mockSpanData = mock(SpanData.class);
      EventData mockEventData = mock(EventData.class);

      when(mockSpanData.getAttributes())
            .thenReturn(Attributes.builder().put(AttributeKey.stringKey("service.name"), "order-service")
                  .put(AttributeKey.stringKey("operation.name"), "createOrder").build());

      when(mockEventData.getName()).thenReturn("invocation_received");
      when(mockEventData.getAttributes())
            .thenReturn(Attributes.builder().put(AttributeKey.stringKey("client.address"), "128.0.0.1").build());
      when(mockSpanData.getEvents()).thenReturn(List.of(mockEventData));

      List<SpanData> spans = List.of(mockSpanData);
      TraceEvent event = SpanFilterUtil.extractTraceEvent("trace-123", spans);

      assertNotNull(event);
      assertEquals("trace-123", event.traceId());
      assertEquals("order-service", event.service());
      assertEquals("createOrder", event.operation());
      assertEquals("128.0.0.1", event.clientAddress());

      // Test matching with exact values
      assertTrue(SpanFilterUtil.matchesTraceEvent(event, "order-service", "createOrder", "128.0.0.1"));
      // Test matching with wildcards
      assertTrue(SpanFilterUtil.matchesTraceEvent(event, "*", "createOrder", "*"));
      assertFalse(SpanFilterUtil.matchesTraceEvent(event, "other-service", "createOrder", "*"));
      // Test matching with regex
      assertTrue(SpanFilterUtil.matchesTraceEvent(event, ".*-service", "create.*", "128\\.0\\.0\\.1"));
      assertFalse(SpanFilterUtil.matchesTraceEvent(event, ".*-service", "update.*", "128\\.0\\.0\\.1"));
   }
}
