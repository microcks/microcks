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

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for class ScriptEngineBinder class.
 * @author laurent
 */
class ScriptEngineBinderTest {

   @Test
   void testRequestContentIsBound() {
      String script = """
            return mockRequest.requestContent;
            """;

      ScriptEngineManager sem = new ScriptEngineManager();
      String body = "content";

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         ScriptContext sc = ScriptEngineBinder.buildEvaluationContext(se, body, null, null, null);
         String result = (String) se.eval(script, sc);

         assertEquals(body, result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testRequestContentHeadersAreBound() {
      String script = """
            def headers = mockRequest.getRequestHeaders()
            log.info("headers: " + headers)
            return headers.get("foo", "null");
            """;

      ScriptEngineManager sem = new ScriptEngineManager();
      String body = "content";
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.addHeader("foo", "bar");

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         ScriptContext sc = ScriptEngineBinder.buildEvaluationContext(se, body, null, null, request, null);
         String result = (String) se.eval(script, sc);

         assertEquals("bar", result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testUriParametersAreBound() {
      String script = """
            def uriParameters = mockRequest.getURIParameters()
            log.info("uri parameters: " + uriParameters)
            return uriParameters.get("foo", "null");
            """;

      ScriptEngineManager sem = new ScriptEngineManager();
      String body = "content";
      MockHttpServletRequest request = new MockHttpServletRequest();
      Map<String, String> uriParameters = new HashMap<>();
      uriParameters.put("foo", "bar");

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         ScriptContext sc = ScriptEngineBinder.buildEvaluationContext(se, body, null, null, request, uriParameters);
         String result = (String) se.eval(script, sc);

         assertEquals("bar", result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testRequestContextIsModified() {
      String script = """
            requestContext.foo = "bar";
            return mockRequest.requestContent;
            """;

      ScriptEngineManager sem = new ScriptEngineManager();
      Map<String, Object> context = new HashMap<>();
      String body = "content";

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         ScriptContext sc = ScriptEngineBinder.buildEvaluationContext(se, body, context, null, null);
         String result = (String) se.eval(script, sc);

         assertEquals(body, result);
         assertTrue(context.containsKey("foo"));
         assertEquals("bar", context.get("foo"));
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testStateStoreIsBoundAndAccessed() {
      String script = """
            def foo = store.get("foo");
            def bar = store.put("bar", "barValue");
            store.delete("baz");
            return foo;
            """;

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

      ScriptEngineManager sem = new ScriptEngineManager();
      Map<String, Object> context = new HashMap<>();
      store.put("foo", "fooValue");
      store.put("baz", "bazValue");

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         ScriptContext sc = ScriptEngineBinder.buildEvaluationContext(se, "body", context, store, null);
         String result = (String) se.eval(script, sc);

         assertEquals("fooValue", result);
         assertEquals("barValue", store.get("bar"));
         assertNull(store.get("baz"));
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testMicrocksXmlHolder() {
      String body = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                <soapenv:Header/>
                <soapenv:Body>
                   <hel:sayHello xmlns:hel="http://www.example.com/hello">
                      <name>Andrew</name>
                   </hel:sayHello>
                </soapenv:Body>
             </soapenv:Envelope>
            """;

      String script = """
            import io.github.microcks.util.soapui.XmlHolder
            def holder = new XmlHolder( mockRequest.requestContent )
            def name = holder["//name"]

            if (name == "Andrew"){
                return "Andrew Response"
            } else if (name == "Karla"){
                return "Karla Response"
            } else {
                return "World Response"
            }
            """;

      ScriptEngineManager sem = new ScriptEngineManager();
      Map<String, Object> context = new HashMap<>();

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         ScriptContext sc = ScriptEngineBinder.buildEvaluationContext(se, body, context, null, null);
         String result = (String) se.eval(script, sc);

         assertEquals("Andrew Response", result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }

   @Test
   void testEviwareXmlHolder() {
      String body = """
            <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                <soapenv:Header/>
                <soapenv:Body>
                   <hel:sayHello xmlns:hel="http://www.example.com/hello">
                      <name>Andrew</name>
                   </hel:sayHello>
                </soapenv:Body>
             </soapenv:Envelope>
            """;

      String script = """
            import com.eviware.soapui.support.XmlHolder
            def holder = new XmlHolder( mockRequest.requestContent )
            def name = holder["//name"]

            if (name == "Andrew"){
                return "Andrew Response"
            } else if (name == "Karla"){
                return "Karla Response"
            } else {
                return "World Response"
            }
            """;

      ScriptEngineManager sem = new ScriptEngineManager();
      Map<String, Object> context = new HashMap<>();

      try {
         // Evaluating request with script coming from operation dispatcher rules.
         ScriptEngine se = sem.getEngineByExtension("groovy");
         ScriptContext sc = ScriptEngineBinder.buildEvaluationContext(se, body, context, null, null);
         script = ScriptEngineBinder.ensureSoapUICompatibility(script);
         String result = (String) se.eval(script, sc);

         assertEquals("Andrew Response", result);
      } catch (Exception e) {
         fail("Exception should no be thrown");
      }
   }
}
