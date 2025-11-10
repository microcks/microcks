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
import javax.script.SimpleScriptContext;
import java.util.Map;

import static io.github.microcks.util.tracing.TraceUtil.addSpanLogEvent;
import static io.github.microcks.util.tracing.TraceUtil.LogLevel;

/**
 * Utility class that holds methods for creating binding environments and evaluation context for a JSR 233 ScriptEngine.
 * @author laurent
 */
public class ScriptEngineBinder {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ScriptEngineBinder.class);

   private static final String ENGINE_NAME = "groovy";

   /** Private constructor to hide the implicit public one. */
   private ScriptEngineBinder() {
   }

   /**
    * Lightweight wrapper exposing common Logger methods while also exporting messages as span events. Keeps the same
    * method names used by scripts (info/debug/warn/error) for drop-in compatibility.
    */
   public static final class LogWrapper {
      private final Logger delegate;

      LogWrapper(Logger delegate) {
         this.delegate = delegate;
      }

      public void info(String msg) {
         delegate.info(msg);
         addSpanLogEvent(LogLevel.INFO, msg, ENGINE_NAME, null);
      }

      public void debug(String msg) {
         delegate.debug(msg);
         addSpanLogEvent(LogLevel.DEBUG, msg, ENGINE_NAME, null);
      }

      public void warn(String msg) {
         delegate.warn(msg);
         addSpanLogEvent(LogLevel.WARN, msg, ENGINE_NAME, null);
      }

      public void error(String msg) {
         delegate.error(msg);
         addSpanLogEvent(LogLevel.ERROR, msg, ENGINE_NAME, null);
      }

      public void error(String msg, Throwable t) {
         delegate.error(msg, t);
         addSpanLogEvent(LogLevel.ERROR, msg, ENGINE_NAME, t);
      }
   }

   /**
    * Build a ScriptContext from Http request for a ScriptEngine.
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param stateStore     A store to save/get state from script
    * @param request        The wrapped incoming servlet request.
    * @return The evaluation context for the script engine eval() method.
    */
   public static ScriptContext buildEvaluationContext(ScriptEngine engine, String requestContent,
         Map<String, Object> requestContext, StateStore stateStore, HttpServletRequest request) {
      StringToStringsMap headers = HttpHeadersUtil.extractFromHttpServletRequest(request);
      return buildEvaluationContext(engine, requestContent, requestContext, stateStore, headers, request);
   }

   /**
    * Create and bind an environment for a ScriptEngine.
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param stateStore     A store to save/get state from script
    * @param request        The wrapped incoming servlet request.
    * @param uriParameters  The URI parameters of the request
    * @return The evaluation context for the script engine eval() method.
    */
   public static ScriptContext buildEvaluationContext(ScriptEngine engine, String requestContent,
         Map<String, Object> requestContext, StateStore stateStore, HttpServletRequest request,
         Map<String, String> uriParameters) {
      StringToStringsMap headers = HttpHeadersUtil.extractFromHttpServletRequest(request);
      return buildEvaluationContext(engine, requestContent, requestContext, stateStore, headers, request,
            uriParameters);
   }

   /**
    * Build an evaluation ScriptContext for a ScriptEngine.
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param stateStore     A store to save/get state from script
    * @param headers        The header values of the request
    * @param request        The wrapped incoming servlet request.
    * @return The evaluation context for the script engine eval() method.
    */
   public static ScriptContext buildEvaluationContext(ScriptEngine engine, String requestContent,
         Map<String, Object> requestContext, StateStore stateStore, StringToStringsMap headers,
         HttpServletRequest request) {
      return buildEvaluationContext(engine, requestContent, requestContext, stateStore, headers, request, null);
   }

   /**
    * Build an evaluation ScriptContext for a ScriptEngine.
    * @param engine         The engine to enrich with binding environment.
    * @param requestContent The content of request to use as data
    * @param requestContext The execution context of this request
    * @param stateStore     A store to save/get state from script
    * @param headers        The header values of the request
    * @param request        The wrapped incoming servlet request.
    * @param uriParameters  The URI parameters of the request
    * @return The evaluation context for the script engine eval() method.
    */
   public static ScriptContext buildEvaluationContext(ScriptEngine engine, String requestContent,
         Map<String, Object> requestContext, StateStore stateStore, StringToStringsMap headers,
         HttpServletRequest request, Map<String, String> uriParameters) {

      // Build a fake request container.
      FakeScriptMockRequest mockRequest = new FakeScriptMockRequest(requestContent, headers);
      mockRequest.setRequest(request);
      mockRequest.setURIParameters(uriParameters);

      // Create bindings and put content according to SoapUI binding environment.
      Bindings bindings = engine.createBindings();
      bindings.put("mockRequest", mockRequest);
      // Wrap the logger so that script log calls are also exported as span events.
      bindings.put("log", new LogWrapper(log));
      bindings.put("requestContext", requestContext);
      bindings.put("store", stateStore);

      SimpleScriptContext scriptContext = new SimpleScriptContext();
      scriptContext.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
      return scriptContext;
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
