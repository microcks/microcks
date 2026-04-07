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
import io.opentelemetry.context.propagation.TextMapSetter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link XTraceIdTextMapPropagator}.
 * @author laurent
 */
@DisplayName("XTraceIdTextMapPropagator")
class XTraceIdTextMapPropagatorTest {

   private XTraceIdTextMapPropagator propagator;

   private static final TextMapGetter<Map<String, String>> MAP_GETTER = new TextMapGetter<>() {
      @Override
      public Iterable<String> keys(Map<String, String> carrier) {
         return carrier.keySet();
      }

      @Nullable
      @Override
      public String get(Map<String, String> carrier, String key) {
         return carrier.get(key);
      }
   };

   private static final TextMapSetter<Map<String, String>> MAP_SETTER = Map::put;

   @BeforeEach
   void setUp() {
      propagator = new XTraceIdTextMapPropagator();
   }

   @Test
   @DisplayName("fields() returns X-Trace-Id and X-Span-Id")
   void fieldsShouldReturnExpectedHeaders() {
      assertTrue(propagator.fields().contains(XTraceIdTextMapPropagator.X_TRACE_ID));
      assertTrue(propagator.fields().contains(XTraceIdTextMapPropagator.X_SPAN_ID));
   }

   @Nested
   @DisplayName("extract()")
   class ExtractTests {

      @Test
      @DisplayName("should extract 32-char hex trace id")
      void shouldExtract32CharTraceId() {
         Map<String, String> carrier = new HashMap<>();
         carrier.put("X-Trace-Id", "69b17bb78c12965f2549e38450271363");
         carrier.put("X-Span-Id", "8cbf86cc28908805");

         Context result = propagator.extract(Context.root(), carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         assertTrue(sc.isValid());
         assertTrue(sc.isRemote());
         assertEquals("69b17bb78c12965f2549e38450271363", sc.getTraceId());
         assertEquals("8cbf86cc28908805", sc.getSpanId());
         assertTrue(sc.isSampled());
      }

      @Test
      @DisplayName("should extract UUID-formatted trace id (with dashes)")
      void shouldExtractUuidTraceId() {
         Map<String, String> carrier = new HashMap<>();
         carrier.put("X-Trace-Id", "550e8400-e29b-41d4-a716-446655440000");

         Context result = propagator.extract(Context.root(), carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         assertTrue(sc.isValid());
         assertEquals("550e8400e29b41d4a716446655440000", sc.getTraceId());
      }

      @Test
      @DisplayName("should zero-pad short trace ids to 32 chars")
      void shouldPadShortTraceId() {
         Map<String, String> carrier = new HashMap<>();
         carrier.put("X-Trace-Id", "abcdef1234567890");

         Context result = propagator.extract(Context.root(), carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         assertTrue(sc.isValid());
         assertEquals("0000000000000000abcdef1234567890", sc.getTraceId());
      }

      @Test
      @DisplayName("should generate random span id when X-Span-Id is absent")
      void shouldGenerateSpanIdWhenMissing() {
         Map<String, String> carrier = new HashMap<>();
         carrier.put("X-Trace-Id", "69b17bb78c12965f2549e38450271363");

         Context result = propagator.extract(Context.root(), carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         assertTrue(sc.isValid());
         assertEquals(16, sc.getSpanId().length());
         assertNotEquals("0000000000000000", sc.getSpanId());
      }

      @Test
      @DisplayName("should not override existing valid remote context")
      void shouldNotOverrideExistingRemoteContext() {
         SpanContext remoteParent = SpanContext.createFromRemoteParent("aaaabbbbccccddddeeee111122223333",
               "1234567890abcdef", TraceFlags.getSampled(), TraceState.getDefault());
         Context parentCtx = Context.root().with(Span.wrap(remoteParent));

         Map<String, String> carrier = new HashMap<>();
         carrier.put("X-Trace-Id", "69b17bb78c12965f2549e38450271363");

         Context result = propagator.extract(parentCtx, carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         // The original remote parent must be preserved.
         assertEquals("aaaabbbbccccddddeeee111122223333", sc.getTraceId());
         assertEquals("1234567890abcdef", sc.getSpanId());
      }

      @Test
      @DisplayName("should return unchanged context when X-Trace-Id is missing")
      void shouldReturnUnchangedWhenMissing() {
         Map<String, String> carrier = new HashMap<>();

         Context result = propagator.extract(Context.root(), carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         assertFalse(sc.isValid());
      }

      @Test
      @DisplayName("should return unchanged context when X-Trace-Id is blank")
      void shouldReturnUnchangedWhenBlank() {
         Map<String, String> carrier = new HashMap<>();
         carrier.put("X-Trace-Id", "  ");

         Context result = propagator.extract(Context.root(), carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         assertFalse(sc.isValid());
      }

      @Test
      @DisplayName("should return unchanged context for non-hex trace id")
      void shouldReturnUnchangedForNonHexTraceId() {
         Map<String, String> carrier = new HashMap<>();
         carrier.put("X-Trace-Id", "not-a-valid-hex-string!!!");

         Context result = propagator.extract(Context.root(), carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         assertFalse(sc.isValid());
      }

      @Test
      @DisplayName("should reject all-zero trace id")
      void shouldRejectAllZeroTraceId() {
         Map<String, String> carrier = new HashMap<>();
         carrier.put("X-Trace-Id", "00000000000000000000000000000000");

         Context result = propagator.extract(Context.root(), carrier, MAP_GETTER);
         SpanContext sc = Span.fromContext(result).getSpanContext();

         assertFalse(sc.isValid());
      }
   }

   @Nested
   @DisplayName("inject()")
   class InjectTests {

      @Test
      @DisplayName("should inject trace and span ids into carrier")
      void shouldInjectTraceAndSpanIds() {
         SpanContext spanContext = SpanContext.create("69b17bb78c12965f2549e38450271363", "8cbf86cc28908805",
               TraceFlags.getSampled(), TraceState.getDefault());
         Context ctx = Context.root().with(Span.wrap(spanContext));

         Map<String, String> carrier = new HashMap<>();
         propagator.inject(ctx, carrier, MAP_SETTER);

         assertEquals("69b17bb78c12965f2549e38450271363", carrier.get("X-Trace-Id"));
         assertEquals("8cbf86cc28908805", carrier.get("X-Span-Id"));
      }

      @Test
      @DisplayName("should not inject when span context is invalid")
      void shouldNotInjectWhenInvalid() {
         Map<String, String> carrier = new HashMap<>();
         propagator.inject(Context.root(), carrier, MAP_SETTER);

         assertNull(carrier.get("X-Trace-Id"));
         assertNull(carrier.get("X-Span-Id"));
      }
   }

   @Nested
   @DisplayName("normalizeTraceId()")
   class NormalizeTraceIdTests {

      @Test
      void shouldHandleExact32Chars() {
         assertEquals("69b17bb78c12965f2549e38450271363",
               XTraceIdTextMapPropagator.normalizeTraceId("69b17bb78c12965f2549e38450271363"));
      }

      @Test
      void shouldPadShortIds() {
         assertEquals("0000000000000000abcdef1234567890",
               XTraceIdTextMapPropagator.normalizeTraceId("abcdef1234567890"));
      }

      @Test
      void shouldTruncateLongIds() {
         String longId = "69b17bb78c12965f2549e384502713630000";
         String result = XTraceIdTextMapPropagator.normalizeTraceId(longId);
         assertEquals(32, result.length());
         assertEquals("69b17bb78c12965f2549e38450271363", result);
      }

      @Test
      void shouldStripDashes() {
         assertEquals("550e8400e29b41d4a716446655440000",
               XTraceIdTextMapPropagator.normalizeTraceId("550e8400-e29b-41d4-a716-446655440000"));
      }

      @Test
      void shouldReturnNullForNonHex() {
         assertNull(XTraceIdTextMapPropagator.normalizeTraceId("xyz-not-hex!"));
      }

      @Test
      void shouldReturnNullForAllZeros() {
         assertNull(XTraceIdTextMapPropagator.normalizeTraceId("00000000000000000000000000000000"));
      }
   }

   @Nested
   @DisplayName("normalizeSpanId()")
   class NormalizeSpanIdTests {

      @Test
      void shouldHandleExact16Chars() {
         assertEquals("8cbf86cc28908805", XTraceIdTextMapPropagator.normalizeSpanId("8cbf86cc28908805"));
      }

      @Test
      void shouldPadShortIds() {
         assertEquals("00000000abcdef12", XTraceIdTextMapPropagator.normalizeSpanId("abcdef12"));
      }

      @Test
      void shouldGenerateRandomForNull() {
         String result = XTraceIdTextMapPropagator.normalizeSpanId(null);
         assertNotNull(result);
         assertEquals(16, result.length());
      }

      @Test
      void shouldGenerateRandomForBlank() {
         String result = XTraceIdTextMapPropagator.normalizeSpanId("  ");
         assertNotNull(result);
         assertEquals(16, result.length());
      }

      @Test
      void shouldGenerateRandomForNonHex() {
         String result = XTraceIdTextMapPropagator.normalizeSpanId("not-hex!!!");
         assertNotNull(result);
         assertEquals(16, result.length());
      }

      @Test
      void shouldGenerateRandomForAllZeros() {
         String result = XTraceIdTextMapPropagator.normalizeSpanId("0000000000000000");
         assertNotNull(result);
         assertNotEquals("0000000000000000", result);
      }
   }
}

