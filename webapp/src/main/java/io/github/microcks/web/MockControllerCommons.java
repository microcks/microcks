/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.web;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.event.MockInvocationEvent;

import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.dispatcher.FallbackSpecification;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;


import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * This class holds commons, utility handlers for different mock controller implements
 * (whether it be Soap, Rest, Async or whatever ...)
 * @author laurent
 */
public class MockControllerCommons {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(MockControllerCommons.class);

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
    * Render the response content using the Expression Language compatible {@code TemplateEngine} if required.
    * If rendering template fails, we just produce a log error message and stick to templatized response.
    * @param requestBody The body payload of incoming request.
    * @param requestResourcePath The resource path of mock request (if any, may be null)
    * @param request The incoming servlet request
    * @param response The response that was found by dispatcher
    * @return The rendered response body payload.
    */
   public static String renderResponseContent(String requestBody, String requestResourcePath, HttpServletRequest request, Response response) {
      // handle empty responses (e.g. 204 - No Content)
      if (response.getContent() == null) {
         return "";
      }

      if (response.getContent().contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         log.debug("Response contains dynamic EL expression, rendering it...");
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

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
         List<String> headerNames = Collections.list(request.getHeaderNames());
         for (String header : headerNames) {
            evaluableHeaders.put(header, request.getHeader(header));
         }
         evaluableRequest.setHeaders(evaluableHeaders);

         // Register the request variable and evaluate the response.
         engine.getContext().setVariable("request", evaluableRequest);
         try {
            return engine.getValue(response.getContent());
         } catch (Throwable t) {
            log.error("Failing at evaluating template " + response.getContent(), t);
            return response.getContent();
         }
      }
      return response.getContent();
   }

   /**
    * Block current thread for specified delay (if not null) from @{code startTime}.
    * @param startTime The starting time of mock request invocation
    * @param delay The delay to wait for
    */
   public static void waitForDelay(Long startTime, Long delay) {
      if (delay != null && delay > -1) {
         log.debug("Mock delay is turned on, waiting if necessary...");
         long duration = System.currentTimeMillis() - startTime;
         if (duration < delay) {
            Object semaphore = new Object();
            synchronized (semaphore) {
               try {
                  semaphore.wait(delay - duration);
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
    * @param eventSource The source of this event
    * @param service The mocked Service that was invoked
    * @param response The response is has been dispatched to
    * @param startTime The start time of the invocation
    */
   public static void publishMockInvocation(ApplicationContext applicationContext, Object eventSource, Service service, Response response, Long startTime) {
      // Publish an invocation event before returning.
      MockInvocationEvent event = new MockInvocationEvent(eventSource, service.getName(), service.getVersion(),
            response.getName(), new Date(startTime), startTime - System.currentTimeMillis());
      applicationContext.publishEvent(event);
      log.debug("Mock invocation event has been published");
   }
}
