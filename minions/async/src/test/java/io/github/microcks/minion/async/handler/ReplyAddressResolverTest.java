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
package io.github.microcks.minion.async.handler;

import io.github.microcks.util.el.EvaluableRequest;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ReplyAddressResolver}.
 *
 * @author adamhicks
 */
class ReplyAddressResolverTest {

   @Test
   void resolveFromHeaderExpression() {
      Map<String, String> headers = new HashMap<>();
      headers.put("replyTo", "my-reply-topic");
      EvaluableRequest request = new EvaluableRequest("{}", null);
      request.setHeaders(headers);

      String result = ReplyAddressResolver.resolve("$message.header#/replyTo", request);

      assertEquals("my-reply-topic", result);
   }

   @Test
   void resolveFromHeaderExpressionWithDifferentHeaderName() {
      Map<String, String> headers = new HashMap<>();
      headers.put("kafka_replyTopic", "order-reply-topic");
      EvaluableRequest request = new EvaluableRequest("{}", null);
      request.setHeaders(headers);

      String result = ReplyAddressResolver.resolve("$message.header#/kafka_replyTopic", request);

      assertEquals("order-reply-topic", result);
   }

   @Test
   void resolveFromHeaderExpressionMissingHeader() {
      Map<String, String> headers = new HashMap<>();
      headers.put("otherHeader", "value");
      EvaluableRequest request = new EvaluableRequest("{}", null);
      request.setHeaders(headers);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve("$message.header#/replyTo", request));

      assertTrue(ex.getMessage().contains("header not found"));
   }

   @Test
   void resolveFromHeaderExpressionNoHeaders() {
      EvaluableRequest request = new EvaluableRequest("{}", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve("$message.header#/replyTo", request));

      assertTrue(ex.getMessage().contains("no headers"));
   }

   @Test
   void resolveFromPayloadExpressionTopLevel() {
      String body = """
            {"replyTopic": "user-signup-reply", "email": "test@example.com"}
            """;
      EvaluableRequest request = new EvaluableRequest(body, null);

      String result = ReplyAddressResolver.resolve("$message.payload#/replyTopic", request);

      assertEquals("user-signup-reply", result);
   }

   @Test
   void resolveFromPayloadExpressionNested() {
      String body = """
            {"user": {"preferredReplyChannel": "notifications-reply"}, "email": "test@example.com"}
            """;
      EvaluableRequest request = new EvaluableRequest(body, null);

      String result = ReplyAddressResolver.resolve("$message.payload#/user/preferredReplyChannel", request);

      assertEquals("notifications-reply", result);
   }

   @Test
   void resolveFromPayloadExpressionDeeplyNested() {
      String body = """
            {"config": {"reply": {"destination": "deep-reply-topic"}}, "action": "signup"}
            """;
      EvaluableRequest request = new EvaluableRequest(body, null);

      String result = ReplyAddressResolver.resolve("$message.payload#/config/reply/destination", request);

      assertEquals("deep-reply-topic", result);
   }

   @Test
   void resolveFromPayloadExpressionNonStringValue() {
      String body = """
            {"replyChannel": 42, "email": "test@example.com"}
            """;
      EvaluableRequest request = new EvaluableRequest(body, null);

      String result = ReplyAddressResolver.resolve("$message.payload#/replyChannel", request);

      assertEquals("42", result);
   }

   @Test
   void resolveFromPayloadExpressionObjectValue() {
      String body = """
            {"reply": {"topic": "my-topic"}, "action": "signup"}
            """;
      EvaluableRequest request = new EvaluableRequest(body, null);

      String result = ReplyAddressResolver.resolve("$message.payload#/reply", request);

      assertEquals("{\"topic\":\"my-topic\"}", result);
   }

   @Test
   void resolveFromPayloadExpressionMissingPath() {
      String body = """
            {"email": "test@example.com"}
            """;
      EvaluableRequest request = new EvaluableRequest(body, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve("$message.payload#/replyTopic", request));

      assertTrue(ex.getMessage().contains("path not found"));
   }

   @Test
   void resolveFromPayloadExpressionNullValue() {
      String body = """
            {"replyTopic": null}
            """;
      EvaluableRequest request = new EvaluableRequest(body, null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve("$message.payload#/replyTopic", request));

      assertTrue(ex.getMessage().contains("path not found"));
   }

   @Test
   void resolveFromPayloadExpressionEmptyBody() {
      EvaluableRequest request = new EvaluableRequest("", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve("$message.payload#/replyTopic", request));

      assertTrue(ex.getMessage().contains("body is empty"));
   }

   @Test
   void resolveFromPayloadExpressionInvalidJson() {
      EvaluableRequest request = new EvaluableRequest("not-json", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve("$message.payload#/replyTopic", request));

      assertTrue(ex.getMessage().contains("failed to parse request body as JSON"));
   }

   @Test
   void resolveWithUnsupportedExpressionFormat() {
      EvaluableRequest request = new EvaluableRequest("{}", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve("$message.unknown#/something", request));

      assertTrue(ex.getMessage().contains("Unsupported addressLocation expression format"));
   }

   @Test
   void resolveWithNullExpression() {
      EvaluableRequest request = new EvaluableRequest("{}", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve(null, request));

      assertTrue(ex.getMessage().contains("must not be null or blank"));
   }

   @Test
   void resolveWithBlankExpression() {
      EvaluableRequest request = new EvaluableRequest("{}", null);

      IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> ReplyAddressResolver.resolve("  ", request));

      assertTrue(ex.getMessage().contains("must not be null or blank"));
   }
}
