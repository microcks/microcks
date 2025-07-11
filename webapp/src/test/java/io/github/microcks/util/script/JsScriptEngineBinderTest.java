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
import io.roastedroot.quickjs4j.core.Runner;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is a test case for class JsScriptEngineBinder class.
 */
class JsScriptEngineBinderTest {

   @Test
   void testRequestContentIsBound() {
      String script = JsScriptEngineBinder.wrapIntoFunction("""
            return mockRequest.requestContent();
            """);

      String body = "content";

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         Engine engine = JsScriptEngineBinder.buildEvaluationContext(body, null, null, null);
         Runner runner = Runner.builder().withEngine(engine).build();
         JsScriptEngineBinder.JsApi jsApi = JsApi_Invokables.create(script, runner);

         String result = jsApi.process();

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
         Runner runner = Runner.builder().withEngine(engine).build();
         JsScriptEngineBinder.JsApi jsApi = JsApi_Invokables.create(script, runner);

         String result = jsApi.process();

         assertEquals("bar", result);
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
         Runner runner = Runner.builder().withEngine(engine).build();
         JsScriptEngineBinder.JsApi jsApi = JsApi_Invokables.create(script, runner);

         String result = jsApi.process();

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
         Runner runner = Runner.builder().withEngine(engine).build();
         JsScriptEngineBinder.JsApi jsApi = JsApi_Invokables.create(script, runner);

         String result = jsApi.process();

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
         Runner runner = Runner.builder().withEngine(engine).build();
         JsScriptEngineBinder.JsApi jsApi = JsApi_Invokables.create(script, runner);

         String result = jsApi.process();

         assertEquals("fooValue", result);
         assertEquals("barValue", store.get("bar"));
         assertNull(store.get("baz"));
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }
}
