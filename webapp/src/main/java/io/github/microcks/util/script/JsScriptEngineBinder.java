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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.microcks.service.StateStore;
import io.github.microcks.util.http.HttpHeadersUtil;
import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.Invokables;
import io.roastedroot.quickjs4j.core.Engine;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Utility class that holds methods for creating binding environments and evaluation context for a JSR 233 ScriptEngine.
 * @author laurent
 */
public class JsScriptEngineBinder {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(JsScriptEngineBinder.class);

   /** Private constructor to hide the implicit public one. */
   private JsScriptEngineBinder() {
   }

   public static Engine buildEvaluationContext(String requestContent, Map<String, Object> requestContext,
         StateStore stateStore, HttpServletRequest request) {
      StringToStringsMap headers = HttpHeadersUtil.extractFromHttpServletRequest(request);
      return buildEvaluationContext(requestContent, requestContext, stateStore, headers, request);
   }

   public static Engine buildEvaluationContext(String requestContent, Map<String, Object> requestContext,
         StateStore stateStore, HttpServletRequest request, Map<String, String> uriParameters) {
      StringToStringsMap headers = HttpHeadersUtil.extractFromHttpServletRequest(request);
      return buildEvaluationContext(requestContent, requestContext, stateStore, headers, request, uriParameters);
   }

   public static Engine buildEvaluationContext(String requestContent, Map<String, Object> requestContext,
         StateStore stateStore, StringToStringsMap headers, HttpServletRequest request) {
      return buildEvaluationContext(requestContent, requestContext, stateStore, headers, request, null);
   }

   private static final ObjectMapper mapper = new ObjectMapper();

   @Builtins("log")
   public static final class LogContext {
      @HostFunction
      public void info(String str) {
         log.info(str);
      }

      @HostFunction
      public void debug(String str) {
         log.debug(str);
      }

      @HostFunction
      public void warn(String str) {
         log.warn(str);
      }

      @HostFunction
      public void error(String str) {
         log.error(str);
      }
   }

   @Builtins("store")
   public final static class StoreApi {
      private final StateStore delegate;

      public StoreApi(StateStore store) {
         this.delegate = store;
      }

      @HostFunction
      public String get(String key) {
         return this.delegate.get(key);
      }

      @HostFunction
      public void put(String key, String value) {
         this.delegate.put(key, value);
      }

      @HostFunction
      public void delete(String key) {
         this.delegate.delete(key);
      }
   }

   @Builtins("requestContext")
   public final static class RequestContextApi {
      private final Map<String, Object> delegate;


      public RequestContextApi(Map<String, Object> requestContext) {
         this.delegate = requestContext;
      }

      @HostFunction
      public JsonNode get(String key) {
         return mapper.convertValue(delegate.get(key), JsonNode.class);
      }

      @HostFunction
      public void set(String key, Object value) {
         delegate.put(key, value);
      }
   }

   @Builtins("mockRequest")
   public final static class MockRequestApi {
      private final FakeScriptMockRequest delegate;
      private final ObjectMapper mapper = new ObjectMapper();

      public MockRequestApi(FakeScriptMockRequest mockRequest) {
         this.delegate = mockRequest;
      }

      @HostFunction
      public String requestContent() {
         return this.delegate.getRequestContent();
      }

      @HostFunction
      public JsonNode getRequestHeader(String key) {
         ArrayNode arr = mapper.createArrayNode();
         StringToStringsMap reqHeaders = this.delegate.getRequestHeaders();
         for (String str : reqHeaders.get(key)) {
            arr.add(str);
         }
         return arr;
      }

      @HostFunction
      public String getURIParameter(String key) {
         if (this.delegate.getURIParameters() == null) {
            return null;
         }
         return this.delegate.getURIParameters().get(key);
      }
   }

   @Invokables("js")
   public interface JsApi {
      @GuestFunction
      String process();
   }

   public static Engine buildEvaluationContext(String requestContent, Map<String, Object> requestContext,
         StateStore stateStore, StringToStringsMap headers, HttpServletRequest request,
         Map<String, String> uriParameters) {

      // Build a fake request container.
      FakeScriptMockRequest mockRequest = new FakeScriptMockRequest(requestContent, headers);
      mockRequest.setRequest(request);
      mockRequest.setURIParameters(uriParameters);

      // Create bindings and put content according to SoapUI binding environment.
      Engine engine = Engine.builder().addInvokables(JsApi_Invokables.toInvokables())
            .addBuiltins(LogContext_Builtins.toBuiltins(new LogContext()))
            .addBuiltins(StoreApi_Builtins.toBuiltins(new StoreApi(stateStore)))
            .addBuiltins(RequestContextApi_Builtins.toBuiltins(new RequestContextApi(requestContext)))
            .addBuiltins(MockRequestApi_Builtins.toBuiltins(new MockRequestApi(mockRequest))).build();

      return engine;
   }

   public static String wrapIntoFunction(String script) {
      return "function process() { " + script + "}";
   }
}
