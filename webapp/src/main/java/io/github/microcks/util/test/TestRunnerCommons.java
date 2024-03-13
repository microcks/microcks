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
package io.github.microcks.util.test;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class holds commons, utility handlers for different test runner implements (whether it be Soap, OpenAPI,
 * Async...)
 * @author laurent
 */
public class TestRunnerCommons {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(TestRunnerCommons.class);

   /**
    * Render the request content using the Expression Language compatible {@code TemplateEngine} if required. If
    * rendering template fails, we just produce a log error message and stick to templatized content.
    * @param request The request that will be sent for test.
    * @param headers The set of computed headers to use for request body evaluation
    * @return The rendered response body payload.
    */
   public static String renderRequestContent(Request request, Set<Header> headers) {
      if (request.getContent().contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         log.debug("Response contains dynamic EL expression, rendering it...");
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

         // Create and fill an evaluable request object.
         EvaluableRequest evaluableRequest = new EvaluableRequest(request.getContent(), null);
         // Adding query parameters...
         Map<String, String> evaluableParams = new HashMap<>();
         for (Parameter parameter : request.getQueryParameters()) {
            evaluableParams.put(parameter.getName(), parameter.getValue());
         }
         evaluableRequest.setParams(evaluableParams);
         // Adding headers...
         Map<String, String> evaluableHeaders = new HashMap<>();
         for (Header header : request.getHeaders()) {
            evaluableHeaders.put(header.getName(), String.join(",", header.getValues()));
         }
         evaluableRequest.setHeaders(evaluableHeaders);

         // Register the request variable and evaluate the request body.
         engine.getContext().setVariable("request", evaluableRequest);
         try {
            return engine.getValue(request.getContent());
         } catch (Throwable t) {
            log.error("Failing at evaluating template " + request.getContent(), t);
            return request.getContent();
         }
      }
      return request.getContent();
   }
}
