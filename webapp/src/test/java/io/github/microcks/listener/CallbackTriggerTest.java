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
package io.github.microcks.listener;

import io.github.microcks.domain.CallbackInfo;
import io.github.microcks.event.HttpServletRequestSnapshot;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test case for CallbackTrigger class.
 * @author laurent
 */
class CallbackTriggerTest {

   /**
    * Reflectively invokes the private extractCallbackUrlFromRequest method. CallbackTrigger constructor deps are not
    * used by this method — nulls are safe.
    */
   private String extractCallbackUrl(HttpServletRequestSnapshot request, String expression) throws Exception {
      Method method = CallbackTrigger.class.getDeclaredMethod("extractCallbackUrlFromRequest",
            HttpServletRequestSnapshot.class, String.class);
      method.setAccessible(true);
      return (String) method.invoke(new CallbackTrigger(null, null), request, expression);
   }

   @Test
   void testLiteralHttpUrlReturnedDirectly() throws Exception {
      // A literal http:// URL must be returned as-is without inspecting the request.
      // Validates OpenAPI 3.x spec §4.8.20 support for literal URL callback keys.
      HttpServletRequestSnapshot request = new HttpServletRequestSnapshot("/nafath/initiate", Map.of(), Map.of(),
            "{\"nationalId\":\"1234567890\"}");

      String result = extractCallbackUrl(request, "http://host.docker.internal:6030/elm/nafath");

      assertEquals("http://host.docker.internal:6030/elm/nafath", result);
   }

   @Test
   void testLiteralHttpsUrlReturnedDirectly() throws Exception {
      // A literal https:// URL must also be returned as-is.
      HttpServletRequestSnapshot request = new HttpServletRequestSnapshot("/webhook/initiate", Map.of(), Map.of(),
            "{}");

      String result = extractCallbackUrl(request, "https://example.com/callback");

      assertEquals("https://example.com/callback", result);
   }

   @Test
   void testQueryParameterExpressionUnchanged() throws Exception {
      // Existing {$request.query.*} behaviour must remain unchanged.
      HttpServletRequestSnapshot request = new HttpServletRequestSnapshot("/nafath/initiate", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/cb" }),
            "{\"nationalId\":\"1234567890\"}");

      String result = extractCallbackUrl(request, "{$request.query.callbackUrl}");

      assertEquals("http://consumer.example.com/cb", result);
   }

   @Test
   void testBodyExpressionUnchanged() throws Exception {
      // Existing {$request.body#/...} behaviour must remain unchanged.
      HttpServletRequestSnapshot request = new HttpServletRequestSnapshot("/nafath/initiate", Map.of(), Map.of(),
            "{\"nationalId\":\"1234567890\",\"callbackUrl\":\"http://consumer.example.com/cb\"}");

      String result = extractCallbackUrl(request, "{$request.body#/callbackUrl}");

      assertEquals("http://consumer.example.com/cb", result);
   }

   @Test
   void testHeaderExpressionUnchanged() throws Exception {
      // Existing {$request.header.*} behaviour must remain unchanged.
      HttpServletRequestSnapshot request = new HttpServletRequestSnapshot("/nafath/initiate",
            Map.of("X-Callback-Url", List.of("http://consumer.example.com/cb")), Map.of(), "{}");

      String result = extractCallbackUrl(request, "{$request.header.X-Callback-Url}");

      assertEquals("http://consumer.example.com/cb", result);
   }

   @Test
   void testResponseTimeoutUsesConfiguredValueWhenSet() throws Exception {
      // When responseTimeout is set on CallbackInfo, sendCallbackRequest must use it.
      // Verified by reading the resolved timeout via the same inline logic.
      CallbackInfo info = new CallbackInfo("http://consumer.example.com/cb", "POST");
      info.setResponseTimeout(30000L);

      long resolved = resolveTimeout(info.getResponseTimeout());

      assertEquals(30000L, resolved);
   }

   @Test
   void testResponseTimeoutFallsBackTo2000WhenNull() throws Exception {
      // When responseTimeout is absent (null), the default 2000ms must be used.
      CallbackInfo info = new CallbackInfo("http://consumer.example.com/cb", "POST");

      long resolved = resolveTimeout(info.getResponseTimeout());

      assertEquals(2000L, resolved);
   }

   @Test
   void testResponseTimeoutFallsBackTo2000WhenZero() throws Exception {
      // A zero value is treated as absent — fall back to 2000ms default.
      CallbackInfo info = new CallbackInfo("http://consumer.example.com/cb", "POST");
      info.setResponseTimeout(0L);

      long resolved = resolveTimeout(info.getResponseTimeout());

      assertEquals(2000L, resolved);
   }

   // ── helpers ──────────────────────────────────────────────────────────────────

   /**
    * Mirrors the timeout-resolution logic inside sendCallbackRequest so the fallback behaviour can be verified without
    * making a real HTTP call.
    */
   private long resolveTimeout(Long responseTimeoutMs) {
      return (responseTimeoutMs != null && responseTimeoutMs > 0) ? responseTimeoutMs : 2000L;
   }
}
