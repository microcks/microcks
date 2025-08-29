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
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.github.microcks.web.AbstractBaseIT;

/**
 * This is a test case for class JsScriptEngineBinder class.
 */
class JsScriptEngineBinderTest extends AbstractBaseIT {

   @Test
   void testRequestContentIsBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            return mockRequest.requestContent();
            """);

      String body = "content";

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertEquals(body, result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testRequestContentHeadersAreBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const fooHeader = mockRequest.getRequestHeader("foo");
            log.info("header: " + fooHeader[0]);
            return fooHeader[0];
            """);

      String body = "content";
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("foo", "bar");

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, null, null, request);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertEquals("bar", result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
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

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, null, null, request);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertEquals("foobar", result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
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

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, null, null, request);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertEquals("barbaz", result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testUriParametersAreBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            const fooParam = mockRequest.getURIParameter("foo");
            log.info("uri parameter: " + fooParam);
            return fooParam ?? "null";
            """);

      String body = "content";
      MockHttpServletRequest request = new MockHttpServletRequest();
      Map<String, String> uriParameters = new HashMap<>();
      uriParameters.put("foo", "bar");

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, null, null, request, uriParameters);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertEquals("bar", result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testRequestContextIsModified() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            requestContext.set("foo", "bar");
            return mockRequest.requestContent();
            """);

      Map<String, Object> context = new HashMap<>();
      String body = "content";

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, context, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertEquals(body, result);
         assertTrue(context.containsKey("foo"));
         assertEquals("bar", context.get("foo"));
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext("body", context, store, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertEquals("fooValue", result);
         assertEquals("barValue", store.get("bar"));
         assertNull(store.get("baz"));
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertTrue(result.startsWith("200:"));
         assertTrue(result.endsWith(":ok"));
      } catch (Exception e) {
         fail("fetch threw exception: " + e.getMessage());
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertTrue(result.startsWith("200:"));
         assertTrue(result.endsWith(":ok"));
      } catch (Exception e) {
         fail("fetch threw exception: " + e.getMessage());
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertTrue(result.startsWith("200:"));
         assertTrue(result.endsWith(":ok"));
      } catch (Exception e) {
         fail("POST fetch threw exception: " + e.getMessage());
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertTrue(result.startsWith("200:"));
         assertTrue(result.endsWith(":ok"));
      } catch (Exception e) {
         fail("PUT fetch threw exception: " + e.getMessage());
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertTrue(result.startsWith("200:"));
         assertTrue(result.endsWith(":ok"));
      } catch (Exception e) {
         fail("DELETE fetch threw exception: " + e.getMessage());
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertTrue(result.startsWith("200:"));
         assertTrue(result.endsWith(":ok"));
      } catch (Exception e) {
         fail("PATCH fetch threw exception: " + e.getMessage());
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertTrue(result.startsWith("200:"));
         assertTrue(result.endsWith(":ok"));
      } catch (Exception e) {
         fail("Headers fetch threw exception: " + e.getMessage());
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertTrue(result.startsWith("200:"));
         assertTrue(result.endsWith(":ok"));
      } catch (Exception e) {
         fail("POST with headers fetch threw exception: " + e.getMessage());
      }
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

      try {
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(null, null, null, null);
         String result = JsScriptEngineBinder.invokeProcessFn(script, engine);

         assertNull(result);
      } catch (Exception e) {
         fail("Invalid method fetch threw exception: " + e.getMessage());
      }
   }
}
