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
import io.github.microcks.util.http.HttpHeadersUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import java.util.Map;

/**
 * Utility class that holds methods for creating binding environments for a JSR 233 ScriptEngine.
 * @author laurent
 */
public class ScriptEngineBinder {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ScriptEngineBinder.class);

   /** Private constructor to hide the implicit public one. */
   private ScriptEngineBinder() {
   }

   /**
    * Create and bind an environment from Http request for a ScriptEngine.
    *
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param stateStore     A store to save/get state from script
    * @param request        The wrapped incoming servlet request.
    */
   public static void bindEnvironment(ScriptEngine engine, String requestContent, Map<String, Object> requestContext,
         StateStore stateStore, HttpServletRequest request) {
      bindEnvironment(engine, requestContent, requestContext, stateStore, request, null);
   }

   /**
    * Create and bind an environment from Http request for a ScriptEngine.
    *
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param stateStore     A store to save/get state from script
    * @param request        The wrapped incoming servlet request.
    * @param uriParameters  The URI parameters of the request
    */
   public static void bindEnvironment(ScriptEngine engine, String requestContent, Map<String, Object> requestContext,
         StateStore stateStore, HttpServletRequest request, Map<String, String> uriParameters) {
      StringToStringsMap headers = HttpHeadersUtil.extractFromHttpServletRequest(request);
      bindEnvironment(engine, requestContent, requestContext, stateStore, headers, request, uriParameters);
   }

   /**
    * Create and bind an environment for a ScriptEngine.
    *
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param stateStore     A store to save/get state from script
    * @param headers        The header values of the request
    * @param request        The wrapped incoming servlet request.
    */
   public static void bindEnvironment(ScriptEngine engine, String requestContent, Map<String, Object> requestContext,
         StateStore stateStore, StringToStringsMap headers, HttpServletRequest request) {
      bindEnvironment(engine, requestContent, requestContext, stateStore, headers, request, null);
   }

   /**
    * Create and bind an environment for a ScriptEngine.
    *
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param stateStore     A store to save/get state from script
    * @param headers        The header values of the request
    * @param request        The wrapped incoming servlet request.
    * @param uriParameters  The URI parameters of the request
    */
   public static void bindEnvironment(ScriptEngine engine, String requestContent, Map<String, Object> requestContext,
         StateStore stateStore, StringToStringsMap headers, HttpServletRequest request,
         Map<String, String> uriParameters) {
      // Build a fake request container.
      FakeScriptMockRequest mockRequest = new FakeScriptMockRequest(requestContent, headers);
      mockRequest.setRequest(request);
      mockRequest.setURIParameters(uriParameters);

      // Create bindings and put content according to SoapUI binding environment.
      Bindings bindings = engine.createBindings();
      bindings.put("mockRequest", mockRequest);
      bindings.put("log", log);
      bindings.put("requestContext", requestContext);
      bindings.put("store", stateStore);
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
