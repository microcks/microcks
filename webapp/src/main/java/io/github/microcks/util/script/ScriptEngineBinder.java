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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.util.Collections;
import java.util.Map;

/**
 * Utility class that holds methods for creating binding environments for a JSR 233 ScriptEngine.
 * @author laurent
 */
public class ScriptEngineBinder {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ScriptEngineBinder.class);


   /**
    * Create and bind a SoapUI environment for a ScriptEngine.
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    */
   public static void bindEnvironment(ScriptEngine engine, String requestContent, Map<String, Object> requestContext) {
      // Build a map of header values.
      bindEnvironment(engine, requestContent, requestContext, null);
   }

   /**
    * Create and bind an environment from Http request for a ScriptEngine.
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param request        The wrapped incoming servlet request.
    */
   public static void bindEnvironment(ScriptEngine engine, String requestContent, Map<String, Object> requestContext,
         HttpServletRequest request) {
      // Build a map of header values.
      StringToStringsMap headers = new HttpHeadersStringToStringsMap();
      if (request != null) {
         for (String headerName : Collections.list(request.getHeaderNames())) {
            headers.put(headerName, Collections.list(request.getHeaders(headerName)));
         }
      }

      // Build a fake request container.
      FakeScriptMockRequest mockRequest = new FakeScriptMockRequest(requestContent, headers);
      mockRequest.setRequest(request);

      // Create bindings and put content according to SoapUI binding environment.
      Bindings bindings = engine.createBindings();
      bindings.put("mockRequest", mockRequest);
      bindings.put("log", log);
      bindings.put("requestContext", requestContext);
      engine.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
   }

   /**
    * Review and adapt a script so that we ensure its compatibility with legacy SoapUI helper.
    * @param script The script to review and adapt
    * @return The script that may have been changed
    */
   public static String ensureSoapUICompatibility(String script) {
      if (script.contains("com.eviware.soapui.support.XmlHolder")) {
         return script.replaceAll("com.eviware.soapui.support.XmlHolder", "io.github.microcks.util.soapui.XmlHolder");
      }
      return script;
   }
}
