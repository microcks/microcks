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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.github.microcks.service.StateStore;
import io.github.microcks.util.http.HttpHeadersUtil;
import io.roastedroot.quickjs4j.annotations.Builtins;
import io.roastedroot.quickjs4j.annotations.GuestFunction;
import io.roastedroot.quickjs4j.annotations.HostFunction;
import io.roastedroot.quickjs4j.annotations.Invokables;
import io.roastedroot.quickjs4j.core.Engine;
import io.roastedroot.quickjs4j.core.Runner;
import io.roastedroot.quickjs4j.core.ScriptCache;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class that holds methods for creating binding environments and evaluation context for a QuickJs4J
 * ScriptEngine.
 * @author Andrea
 */
public class JsScriptEngineBinder {

   private static final Logger log = LoggerFactory.getLogger(JsScriptEngineBinder.class);

   private static final ScriptCache cache = new LRUScriptCache(100);

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
      public void putWithTtl(String key, String value, Integer ttlSeconds) {
         this.delegate.put(key, value, ttlSeconds);
      }

      @HostFunction
      public void delete(String key) {
         this.delegate.delete(key);
      }
   }

   private static final ObjectMapper mapper = new ObjectMapper();

   @Builtins("requestContext")
   public final static class RequestContextApi {
      private final Map<String, Object> delegate;


      public RequestContextApi(Map<String, Object> requestContext) {
         this.delegate = requestContext;
      }

      @HostFunction
      public JsonNode get(String key) {
         return mapper.convertValue(this.delegate.get(key), JsonNode.class);
      }

      @HostFunction
      public void set(String key, Object value) {
         this.delegate.put(key, value);
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

   @Builtins("customBuiltins")
   public static final class CustomBuiltins {

      @JsonDeserialize(using = JsonDeserializer.None.class)
      @JsonInclude(JsonInclude.Include.NON_NULL)
      public static class FetchResponse {
         @JsonProperty("status")
         public final int status;
         @JsonProperty("body")
         public final String body;

         public FetchResponse(int status, String body) {
            this.status = status;
            this.body = body;
         }
      }

      /**
       * Mimics the browser/node fetch API with full HTTP method support.
       *
       * @param url        The URL to fetch
       * @param method     The HTTP method (GET, POST, PUT, DELETE, PATCH)
       * @param body       The request body (for POST, PUT, PATCH requests)
       * @param rawHeaders Map of headers to include in the request
       * @return A JS object with status and body properties
       */
      @HostFunction
      public Object fetch(String url, String method, String body, JsonNode rawHeaders) {
         try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpUriRequestBase request = createHttpRequest(url, method);

            // Add headers if provided
            if (rawHeaders != null) {
               rawHeaders.fields().forEachRemaining(entry -> {
                  String key = entry.getKey();
                  JsonNode valueNode = entry.getValue();

                  List<String> values = new ArrayList<>();
                  if (valueNode.isArray()) {
                     valueNode.forEach(v -> values.add(v.asText()));
                  } else {
                     values.add(valueNode.asText());
                  }

                  request.addHeader(key, values);
               });
            }

            // Add body for methods that support it
            if ((request instanceof HttpPost || request instanceof HttpPut || request instanceof HttpPatch)
                  && body != null) {
               StringEntity entity = new StringEntity(body);
               request.setEntity(entity);
            }

            try (CloseableHttpResponse response = httpClient.execute(request)) {
               int status = response.getCode();
               String responseBody = response.getEntity() != null ? EntityUtils.toString(response.getEntity()) : "";
               return new FetchResponse(status, responseBody);
            }
         } catch (IOException e) {
            throw new UncheckedIOException("Failed to read response from server", e);
         } catch (ParseException e) {
            throw new RuntimeException("Failed to parse response from server", e);
         }
      }

      /**
       * Creates the appropriate HTTP request object based on the method.
       */
      private HttpUriRequestBase createHttpRequest(String url, String method) {
         if (method == null) {
            return new HttpGet(url);
         }

         return switch (method.toUpperCase()) {
            case "GET" -> new HttpGet(url);
            case "POST" -> new HttpPost(url);
            case "PUT" -> new HttpPut(url);
            case "DELETE" -> new HttpDelete(url);
            case "PATCH" -> new HttpPatch(url);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
         };
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
      Engine engine = Engine.builder().withCache(cache).addInvokables(JsApi_Invokables.toInvokables())
            .addBuiltins(LogContext_Builtins.toBuiltins(new LogContext()))
            .addBuiltins(StoreApi_Builtins.toBuiltins(new StoreApi(stateStore)))
            .addBuiltins(RequestContextApi_Builtins.toBuiltins(new RequestContextApi(requestContext)))
            .addBuiltins(MockRequestApi_Builtins.toBuiltins(new MockRequestApi(mockRequest)))
            .addBuiltins(CustomBuiltins_Builtins.toBuiltins(new CustomBuiltins())).build();

      return engine;
   }

   public static String wrapIntoFunction(String script) {
      return "globalThis.fetch = customBuiltins.fetch;\n" + "function process() { " + script + "}";
   }

   public static String invokeProcessFn(String script, Engine scriptContext) {
      Runner runner = null;
      try {
         runner = Runner.builder().withEngine(scriptContext).build();
         JsScriptEngineBinder.JsApi jsApi = JsApi_Invokables.create(script, runner);
         return jsApi.process();
      } catch (Exception e) {
         log.error("Error during JS evaluation", e);
         if (runner != null) {
            log.error("script stdout: " + runner.stdout());
            log.error("script stderr: " + runner.stderr());
         }
      } finally {
         if (runner != null) {
            runner.close();
         }
      }
      return null;
   }
}
