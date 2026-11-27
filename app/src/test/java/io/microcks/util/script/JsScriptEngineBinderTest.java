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
package io.github.microcks.util.script;

import io.github.microcks.service.StateStore;
import io.roastedroot.quickjs4j.core.Engine;
import jakarta.servlet.http.Cookie;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for class JsScriptEngineBinder class.
 */
class JsScriptEngineBinderTest {

   private static HttpServer testServer;
   private static int testServerPort;

   @BeforeAll
   static void startTestServer() throws IOException {
      testServer = HttpServer.create(new InetSocketAddress(0), 0);
      testServer.createContext("/api/test-fetch", JsScriptEngineBinderTest::handleTestFetch);
      testServer.createContext("/api/test-fetch-headers", JsScriptEngineBinderTest::handleTestFetchHeaders);
      testServer.createContext("/api/test-fetch-json", JsScriptEngineBinderTest::handleTestFetchJson);
      testServer.start();
      testServerPort = testServer.getAddress().getPort();
   }

   @AfterAll
   static void stopTestServer() {
      if (testServer != null) {
         testServer.stop(0);
      }
   }

   private String getServerUrl() {
      return "http://localhost:" + testServerPort;
   }

   private static void handleTestFetch(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String response = switch (exchange.getRequestMethod()) {
         case "GET" -> "Hello from test-fetch endpoint!";
         case "POST" -> "POST received with body: " + (body.isEmpty() ? "empty" : body);
         case "PUT" -> "PUT received with body: " + (body.isEmpty() ? "empty" : body);
         case "DELETE" -> "DELETE received";
         case "PATCH" -> "PATCH received with body: " + (body.isEmpty() ? "empty" : body);
         default -> null;
      };

      if (response == null) {
         sendResponse(exchange, 405, "Method not allowed");
         return;
      }
      sendResponse(exchange, 200, response);
   }

   private static void handleTestFetchHeaders(HttpExchange exchange) throws IOException {
      String customHeader = exchange.getRequestHeaders().getFirst("X-Custom-Header");
      String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
      sendResponse(exchange, 200, "Headers received - Custom: " + customHeader + ", Auth: " + authHeader);
   }

   private static void handleTestFetchJson(HttpExchange exchange) throws IOException {
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      sendResponse(exchange, 200, "{\"message\":\"Hello from test-fetch-json endpoint!\",\"status\":200}");
   }

   private static void sendResponse(HttpExchange exchange, int status, String response) throws IOException {
      byte[] payload = response.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, payload.length);
      try (OutputStream responseBody = exchange.getResponseBody()) {
         responseBody.write(payload);
      }
   }

   @Test
   void testRequestContentIsBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            return mockRequest.requestContent();
            """);

      String body = "content";
      Engine engine = JsScriptEngineBinder.buildEvaluationContext("content", null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals(body, result);
   }

   @Test
   void testRequestHeaderIsBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const fooHeader = mockRequest.getRequestHeader("foo");
            log.info("header: " + fooHeader[0]);
            return fooHeader[0];
            """);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("foo", "bar");

      Engine engine = JsScriptEngineBinder.buildEvaluationContext("content", null, null, request);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals("bar", result);
   }

   @Test
   void testNoExceptionOnMissingRequestHeader() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const fooHeader = mockRequest.getRequestHeader("foo");
            if (fooHeader && fooHeader.length > 0) {
               log.info("header: " + fooHeader[0]);
               return fooHeader[0];
            }
            return "no-foo";
            """);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("baz", "bar");

      Engine engine = JsScriptEngineBinder.buildEvaluationContext("content", null, null, request);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals("no-foo", result);
   }

   @Test
   void testRequestHeadersAreBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const headers = mockRequest.getRequestHeaders();
            log.info("header: " + JSON.stringify(headers));
            if (headers["foo"]) {
               return headers.foo[0];
            }
            return "no-foo";
            """);

      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("foo", "bar");

      Engine engine = JsScriptEngineBinder.buildEvaluationContext("content", null, null, request);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals("bar", result);
   }

   @Test
   void testRequestQueryString() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const request = mockRequest.getRequest();
            log.info("queryString: " + request.queryString);
            return request.queryString;
            """);

      String body = "content";
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setQueryString("foobar");

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, null, null, request);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals("foobar", result);
   }

   @Test
   void testRequestCookie() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const request = mockRequest.getRequest();
            log.info("cookies: " + JSON.stringify(request.cookies));
            return request.cookies[0].name + request.cookies[0].value;
            """);

      String body = "content";
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setCookies(new Cookie("bar", "baz"));

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, null, null, request);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals("barbaz", result);
   }

   @Test
   void testUriParameterIsBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const fooParam = mockRequest.getURIParameter("foo");
            log.info("uri parameter: " + fooParam);
            return fooParam ?? "null";
            """);

      MockHttpServletRequest request = new MockHttpServletRequest();
      Map<String, String> uriParameters = new HashMap<>();
      uriParameters.put("foo", "bar");

      Engine engine = JsScriptEngineBinder.buildEvaluationContext("content", null, null, request, uriParameters);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals("bar", result);
   }

   @Test
   void testUriParametersAreBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const params = mockRequest.getURIParameters();
            log.info("uri parameters: " + JSON.stringify(params));
            if (params && params.foo === "bar" && params.baz === "xyz") {
               return "params-ok";
            }
            return "params-ko";
            """);

      MockHttpServletRequest request = new MockHttpServletRequest();
      Map<String, String> uriParameters = new HashMap<>();
      uriParameters.put("foo", "bar");
      uriParameters.put("baz", "xyz");

      Engine engine = JsScriptEngineBinder.buildEvaluationContext("content", null, null, request, uriParameters);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals("params-ok", result);
   }

   @Test
   void testRequestContextIsModified() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            requestContext.set("foo", "bar");
            return mockRequest.requestContent();
            """);

      Map<String, Object> context = new HashMap<>();
      String body = "content";

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, context, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals(body, result);
      assertTrue(context.containsKey("foo"));
      assertEquals("bar", context.get("foo"));
   }

   @Test
   void testStateStoreIsBoundAndAccessed() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const foo = store.get("foo");
            store.put("bar", "barValue");
            store.delete("baz");
            return foo;
            """);

      StateStore store = new StateStore() {
         private final Map<String, String> map = new HashMap<>();

         @Override
         public void put(String key, String value) {
            map.put(key, value);
         }

         @Override
         public void put(String key, String value, int secondsTTL) {
            map.put(key, value);
         }

         @Nullable
         @Override
         public String get(String key) {
            return map.get(key);
         }

         @Override
         public void delete(String key) {
            map.remove(key);
         }
      };

      Map<String, Object> context = new HashMap<>();
      store.put("foo", "fooValue");
      store.put("baz", "bazValue");

      Engine engine = JsScriptEngineBinder.buildEvaluationContext("body", context, store, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertEquals("fooValue", result);
      assertEquals("barValue", store.get("bar"));
      assertNull(store.get("baz"));
   }

   @Test
   void testBasicFetchApiWorks() {
      String url = getServerUrl() + "/api/test-fetch";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const response = fetch('""" + url + """
            ');
            log.info('fetch status: ' + response.status);
            const body = response.body;
            return response.status + ':' + (body.includes('test-fetch') ? 'ok' : 'fail');
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertTrue(result.startsWith("200:"));
      assertTrue(result.endsWith(":ok"));
   }

   @Test
   void testBasicFetchApiWorksWithJsonPayloads() {
      String url = getServerUrl() + "/api/test-fetch-json";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const response = fetch('""" + url + """
            ');
            log.info('fetch status: ' + response.status);
            const body = JSON.parse(response.body);
            return body.status + ':' + (body.message.includes('test-fetch-json') ? 'ok' : 'fail');
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertTrue(result.startsWith("200:"));
      assertTrue(result.endsWith(":ok"));
   }

   @Test
   void testBasicFetchApiWithErrors() {
      String url = "http://inexistent:6543/api/test-fetch-inexistent";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            log.info("the following fetch will fail");
            const response = fetch('""" + url + """
            ');
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertNull(result);
   }

   @Test
   void testFetchApiWithPostMethod() {
      String url = getServerUrl() + "/api/test-fetch";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const response = fetch('""" + url + """
            ', 'POST', 'test body content');
            log.info('POST fetch status: ' + response.status);
            const body = response.body;
            return response.status + ':' + (body.includes('POST received') ? 'ok' : 'fail');
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertTrue(result.startsWith("200:"));
      assertTrue(result.endsWith(":ok"));
   }

   @Test
   void testFetchApiWithPutMethod() {
      String url = getServerUrl() + "/api/test-fetch";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const response = fetch('""" + url + """
            ', 'PUT', 'updated content');
            log.info('PUT fetch status: ' + response.status);
            const body = response.body;
            return response.status + ':' + (body.includes('PUT received') ? 'ok' : 'fail');
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertTrue(result.startsWith("200:"));
      assertTrue(result.endsWith(":ok"));
   }

   @Test
   void testFetchApiWithDeleteMethod() {
      String url = getServerUrl() + "/api/test-fetch";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const response = fetch('""" + url + """
            ', 'DELETE', null, null);
            log.info('DELETE fetch status: ' + response.status);
            const body = response.body;
            return response.status + ':' + (body.includes('DELETE received') ? 'ok' : 'fail');
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertTrue(result.startsWith("200:"));
      assertTrue(result.endsWith(":ok"));
   }

   @Test
   void testFetchApiWithPatchMethod() {
      String url = getServerUrl() + "/api/test-fetch";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const response = fetch('""" + url + """
            ', 'PATCH', 'patch data');
            log.info('PATCH fetch status: ' + response.status);
            const body = response.body;
            return response.status + ':' + (body.includes('PATCH received') ? 'ok' : 'fail');
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertTrue(result.startsWith("200:"));
      assertTrue(result.endsWith(":ok"));
   }

   @Test
   void testFetchApiWithHeaders() {
      String url = getServerUrl() + "/api/test-fetch-headers";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const headers = {
               'X-Custom-Header': 'custom-value',
               'Authorization': 'Bearer token123'
            };
            const response = fetch('""" + url
            + """
                  ', 'GET', null, headers);
                  log.info('Headers fetch status: ' + response.status);
                  const body = response.body;
                  return response.status + ':' + (body.includes('custom-value') && body.includes('Bearer token123') ? 'ok' : 'fail');
                  """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertTrue(result.startsWith("200:"));
      assertTrue(result.endsWith(":ok"));
   }

   @Test
   void testFetchApiWithPostAndHeaders() {
      String url = getServerUrl() + "/api/test-fetch";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const headers = {
               'X-Example': 'my-header',
               'X-Test-Header': 'test-value'
            };
            const body = '{"message": "hello world"}';
            const response = fetch('""" + url + """
            ', 'POST', body, headers);
            log.info('POST with headers fetch status: ' + response.status);
            const responseBody = response.body;
            return response.status + ':' + (responseBody.includes('POST received') ? 'ok' : 'fail');
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertTrue(result.startsWith("200:"));
      assertTrue(result.endsWith(":ok"));
   }

   @Test
   void testFetchApiWithInvalidMethod() {
      String url = getServerUrl() + "/api/test-fetch";
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            log.info("going to fail");
            const response = fetch('""" + url + """
            ', 'INVALID');
            return "unreachable";
            """);

      Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
      String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

      assertNull(result);
   }
}
