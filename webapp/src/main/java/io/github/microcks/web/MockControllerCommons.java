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
package io.github.microcks.web;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.event.MockInvocationEvent;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.dispatcher.ProxyFallbackSpecification;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import io.github.microcks.util.delay.DelaySpec;
import io.github.microcks.util.delay.DelayApplier;
import io.github.microcks.util.delay.DelayApplierFactory;

/**
 * This class holds commons, utility handlers for different mock controller implements (whether it be Soap, Rest, Async
 * or whatever ...)
 * @author laurent
 */
public class MockControllerCommons {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(MockControllerCommons.class);

   /** The header name used to specify a wait delay in the request. */
   public static final String X_MICROCKS_DELAY_HEADER = "x-microcks-delay";

   /** The header name used to specify a delay strategy in the request. */
   public static final String X_MICROCKS_DELAY_STRATEGY_HEADER = "x-microcks-delay-strategy";

   private static final String RENDERING_MESSAGE = "Response contains dynamic EL expression, rendering it...";


   /** Private constructor to avoid instantiation. */
   private MockControllerCommons() {
   }

   /**
    * Retrieve a fallback specification for this operation if one is defined.
    * @param rOperation The operation to get fallback specification
    * @return A fallback specification or null if none defined
    */
   public static FallbackSpecification getFallbackIfAny(Operation rOperation) {
      FallbackSpecification fallback = null;
      if (DispatchStyles.FALLBACK.equals(rOperation.getDispatcher())) {
         try {
            fallback = FallbackSpecification.buildFromJsonString(rOperation.getDispatcherRules());
         } catch (JsonMappingException jme) {
            log.error("Dispatching rules of operation cannot be interpreted as FallbackSpecification", jme);
         }
      }
      return fallback;
   }

   /**
    * Retrieve a proxyFallback specification for this operation if one is defined.
    * @param rOperation The operation to get proxyFallback specification
    * @return A proxy specification or null if none defined
    */
   public static ProxyFallbackSpecification getProxyFallbackIfAny(Operation rOperation) {
      ProxyFallbackSpecification proxyFallback = null;
      if (DispatchStyles.PROXY_FALLBACK.equals(rOperation.getDispatcher())) {
         try {
            proxyFallback = ProxyFallbackSpecification.buildFromJsonString(rOperation.getDispatcherRules());
         } catch (JsonMappingException jme) {
            log.error("Dispatching rules of operation cannot be interpreted as ProxyFallbackSpecification", jme);
         }
      }
      return proxyFallback;
   }

   /**
    * Check if proxy behavior is requested and extract proxyUrl.
    * @param dispatcher      The original dispatcher for the Proxy dispatcher checking.
    * @param dispatcherRules The original dispatcherRules for URL extracting.
    * @param resourcePath    The original resourcePath for URL compilation.
    * @param proxyFallback   The proxyFallbackSpec for the Proxy-Fallback dispatcher checking and URL extracting.
    * @param request         The original request for URL comparing on cycling.
    * @param response        The response that was found(or not) by dispatcher.
    * @return The optional container with URI for the proxy service if the request needs to be proxied.
    */
   public static Optional<URI> getProxyUrlIfProxyIsNeeded(String dispatcher, String dispatcherRules,
         String resourcePath, ProxyFallbackSpecification proxyFallback, HttpServletRequest request, Response response) {
      String externalUrl = null;
      if (DispatchStyles.PROXY.equals(dispatcher)) {
         externalUrl = dispatcherRules;
      }
      if (response == null && proxyFallback != null) {
         externalUrl = proxyFallback.getProxyUrl();
      }
      if (externalUrl != null) {
         externalUrl = externalUrl.replaceFirst("/$", "") + resourcePath;
         if (!externalUrl.contentEquals(request.getRequestURL())) {
            try {
               return Optional
                     .of(UriComponentsBuilder.fromHttpUrl(externalUrl).query(request.getQueryString()).build().toUri());
            } catch (IllegalArgumentException ex) {
               log.warn("Invalid external URL in the dispatcher - {}", externalUrl);
            }
         }
      }
      return Optional.empty();
   }

   /**
    * Render the response headers using the Expression Language compatible {@code TemplateEngine} if required. If
    * rendering template fails, we just produce a log error message and stick to templatized values.
    * @param evaluableRequest The request that can be evaluated in templating.
    * @param requestContext   The invocation context of the request
    * @param response         The response that was found by dispatcher
    * @return The rendered response headers values.
    */
   public static Set<Header> renderResponseHeaders(EvaluableRequest evaluableRequest,
         Map<String, Object> requestContext, Response response) {
      TemplateEngine engine = null;
      Set<Header> headers = new HashSet<>();

      if (response.getHeaders() != null) {
         for (Header header : response.getHeaders()) {
            Set<String> renderedValues = new HashSet<>();
            for (String value : header.getValues()) {
               // Only render and build an engine if we have an expression.
               if (value.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
                  // Evaluate the header value.
                  if (engine == null) {
                     engine = TemplateEngineFactory.getTemplateEngine();
                  }
                  renderedValues.add(unguardedRenderResponseContent(evaluableRequest, requestContext, engine, value));
               } else {
                  renderedValues.add(value);
               }
            }
            headers.add(new Header(header.getName(), renderedValues));
         }
      }
      return headers;
   }

   /**
    * Render the response content using the Expression Language compatible {@code TemplateEngine} if required. If
    * rendering template fails, we just produce a log error message and stick to templatized response.
    * @param requestBody    The body payload of incoming request.
    * @param requestContext The invocation context of the request
    * @param response       The response that was found by dispatcher
    * @return The rendered response body payload.
    */
   public static String renderResponseContent(String requestBody, Map<String, Object> requestContext,
         Response response) {
      if (response.getContent() != null && response.getContent().contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         log.debug(RENDERING_MESSAGE);

         // Create and fill an evaluable request object.
         EvaluableRequest evaluableRequest = new EvaluableRequest(requestBody, null);

         // Evaluate the response.
         return unguardedRenderResponseContent(evaluableRequest, requestContext,
               TemplateEngineFactory.getTemplateEngine(), response.getContent());
      }
      return response.getContent();
   }

   /**
    * Render the response content using the Expression Language compatible {@code TemplateEngine} if required. If
    * rendering template fails, we just produce a log error message and stick to templatized response.
    * @param requestBody      The body payload of incoming request.
    * @param evaluableParams  A map of params for evaluation.
    * @param evaluableHeaders A map of headers for evaluation.
    * @param requestContext   The invocation context of the request
    * @param response         The response that was found by dispatcher
    * @return The rendered response body payload.
    */
   public static String renderResponseContent(String requestBody, Map<String, String> evaluableParams,
         Map<String, String> evaluableHeaders, Map<String, Object> requestContext, Response response) {
      if (response.getContent() != null && response.getContent().contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         log.debug(RENDERING_MESSAGE);

         // Create and fill an evaluable request object.
         EvaluableRequest evaluableRequest = new EvaluableRequest(requestBody, null);
         // Adding query and header parameters...
         evaluableRequest.setParams(evaluableParams);
         evaluableRequest.setHeaders(evaluableHeaders);

         // Evaluate the response.
         return unguardedRenderResponseContent(evaluableRequest, requestContext,
               TemplateEngineFactory.getTemplateEngine(), response.getContent());
      }
      return response.getContent();
   }

   /**
    * Render the response content using the Expression Language compatible {@code TemplateEngine} if required. If
    * rendering template fails, we just produce a log error message and stick to templatized response.
    * @param requestBody         The body payload of incoming request.
    * @param requestResourcePath The resource path of mock request (if any, may be null)
    * @param request             The incoming servlet request
    * @param requestContext      The invocation context of the request
    * @param response            The response that was found by dispatcher
    * @return The rendered response body payload.
    */
   public static String renderResponseContent(String requestBody, String requestResourcePath,
         HttpServletRequest request, Map<String, Object> requestContext, Response response) {
      if (response.getContent() != null && response.getContent().contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         log.debug(RENDERING_MESSAGE);

         // Create and fill an evaluable request object.
         EvaluableRequest evaluableRequest = buildEvaluableRequest(requestBody, requestResourcePath, request);
         return unguardedRenderResponseContent(evaluableRequest, requestContext,
               TemplateEngineFactory.getTemplateEngine(), response.getContent());
      }
      return response.getContent();
   }

   /**
    * Render the response content using the Expression Language compatible {@code TemplateEngine} if required. If
    * rendering template fails, we just produce a log error message and stick to templatized response.
    * @param evaluableRequest The request that can be evaluated in templating.
    * @param engine           The template engine to use for this rendering
    * @param responseContent  The response content found by dispatcher
    * @return The rendered response body payload.
    */
   public static String renderResponseContent(EvaluableRequest evaluableRequest, TemplateEngine engine,
         String responseContent) {
      if (responseContent != null && responseContent.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         return unguardedRenderResponseContent(evaluableRequest, null, engine, responseContent);
      }
      return responseContent;
   }

   private static String unguardedRenderResponseContent(EvaluableRequest evaluableRequest,
         Map<String, Object> requestContext, TemplateEngine engine, String responseContent) {
      // Register the request variable and evaluate the response.
      engine.getContext().setVariable("request", evaluableRequest);
      if (requestContext != null) {
         engine.getContext().setVariables(requestContext);
      }
      try {
         return engine.getValue(responseContent);
      } catch (Throwable t) {
         log.error("Failing at evaluating template {}", responseContent, t);
      }
      return responseContent;
   }

   /**
    * Build an Evaluable request from various request elements.
    * @param requestBody         The body of request to evaluate
    * @param requestResourcePath The resource path this request is bound to
    * @param request             The underlying Http request
    * @return The Evaluable request for later templating rendering
    */
   public static EvaluableRequest buildEvaluableRequest(String requestBody, String requestResourcePath,
         HttpServletRequest request) {
      // Create and fill an evaluable request object.
      EvaluableRequest evaluableRequest = new EvaluableRequest(requestBody,
            requestResourcePath != null ? requestResourcePath.split("/") : null);
      // Adding query parameters...
      Map<String, String> evaluableParams = new HashMap<>();
      List<String> parameterNames = Collections.list(request.getParameterNames());
      for (String parameter : parameterNames) {
         evaluableParams.put(parameter, request.getParameter(parameter));
      }
      evaluableRequest.setParams(evaluableParams);
      // Adding headers...
      Map<String, String> evaluableHeaders = new HashMap<>();
      if (request.getHeaderNames() != null) {
         List<String> headerNames = Collections.list(request.getHeaderNames());
         for (String header : headerNames) {
            evaluableHeaders.put(header, request.getHeader(header));
         }
      }
      evaluableRequest.setHeaders(evaluableHeaders);

      return evaluableRequest;
   }

   /** Retrieve delay header or default to the one provided as parameter. */
   public static DelaySpec getDelay(HttpHeaders headers, Long delayParameter, String strategyName) {
      if (headers.containsKey(MockControllerCommons.X_MICROCKS_DELAY_HEADER)) {
         String delayHeader = headers.getFirst(MockControllerCommons.X_MICROCKS_DELAY_HEADER);
         try {
            String delayStrategyHeader = headers.getFirst(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER);

            return new DelaySpec(Long.parseLong(delayHeader), delayStrategyHeader);
         } catch (NumberFormatException nfe) {
            log.debug("Invalid delay header value: {}", delayHeader);
         }
      }
      if (delayParameter == null) {
         return null;
      }

      return new DelaySpec(delayParameter, strategyName);
   }

   /**
    * Block current thread for specified delay (if not null) from @{code startTime}.
    * @param startTime The starting time of mock request invocation
    * @param delay     The delay to wait for
    */
   public static void waitForDelay(Long startTime, DelaySpec delay) {
      if (delay != null && delay.baseValue() > -1) {
         DelayApplier delayStrategy = DelayApplierFactory.fromString(delay.strategyName());
         Long waitDelay = delayStrategy.compute(delay.baseValue());
         log.debug("Mock delay is turned on, waiting if necessary...");
         long duration = System.currentTimeMillis() - startTime;
         if (duration < waitDelay) {
            Object semaphore = new Object();
            synchronized (semaphore) {
               try {
                  semaphore.wait(waitDelay - duration);
               } catch (Exception e) {
                  log.debug("Delay semaphore was interrupted");
               }
            }
         }
         log.debug("Delay now expired, releasing response !");
      }
   }

   /**
    * Publish a mock invocation event on Spring ApplicationContext internal bus.
    * @param applicationContext The context to use for publication
    * @param eventSource        The source of this event
    * @param service            The mocked Service that was invoked
    * @param response           The response it has been dispatched to
    * @param startTime          The start time of the invocation
    */
   public static void publishMockInvocation(ApplicationContext applicationContext, Object eventSource, Service service,
         Response response, Long startTime) {
      // Publish an invocation event before returning.
      MockInvocationEvent event = new MockInvocationEvent(eventSource, service.getName(), service.getVersion(),
            response.getName(), new Date(startTime), startTime - System.currentTimeMillis());
      applicationContext.publishEvent(event);
      log.debug("Mock invocation event has been published");
   }

   public static String composeServiceAndVersion(String serviceName, String version) {
      return "/" + UriUtils.encodeFragment(serviceName, StandardCharsets.UTF_8) + "/" + version;
   }

   public static String extractResourcePath(HttpServletRequest request, String serviceAndVersion) {
      String requestURI = request.getRequestURI();
      String resourcePath = requestURI.substring(requestURI.indexOf(serviceAndVersion) + serviceAndVersion.length());
      log.debug("Found resourcePath: {}", resourcePath);

      // If resourcePath was encoded with '+' instead of '%20', replace them .
      if (resourcePath.contains("+")) {
         resourcePath = resourcePath.replace("+", "%20");
      }
      return resourcePath;
   }
}
