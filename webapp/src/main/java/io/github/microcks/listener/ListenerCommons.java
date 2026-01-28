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
package io.github.microcks.listener;

import io.github.microcks.event.HttpServletRequestSnapshot;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * A utility class providing helper methods for processing event-driven content rendering using templates. This class is
 * designed to work with HTTP request snapshots and content templates for dynamic evaluation and rendering.
 * @author laurent
 */
public class ListenerCommons {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ListenerCommons.class);

   // Utility class, hide default constructor.
   private ListenerCommons() {
   }

   /**
    * Render a content from an event HttpServiceRequestSnapshot and a content template.
    * @param requestSnapshot The Http request snapshot to use as context for evaluation.
    * @param contentTemplate The template to evaluate.
    * @return The rendered content or the original template if no evaluation was possible.
    */
   public static String renderContent(HttpServletRequestSnapshot requestSnapshot, String contentTemplate) {
      if (contentTemplate != null && contentTemplate.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {

         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

         // Building a EvaluableRequest from snapshot.
         EvaluableRequest evaluableRequest = new EvaluableRequest(requestSnapshot.body(),
               requestSnapshot.path().split("/"));
         evaluableRequest.setParams(requestSnapshot.queryParameters().entrySet().stream()
               .filter(e -> e.getValue() != null && e.getValue().length > 0)
               .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0])));
         evaluableRequest.setHeaders(requestSnapshot.headers().entrySet().stream()
               .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
               .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getFirst())));

         // Register the request variable and evaluate the response.
         engine.getContext().setVariable("request", evaluableRequest);
         try {
            return engine.getValue(contentTemplate);
         } catch (Throwable t) {
            log.error("Failing at evaluating template {}", contentTemplate, t);
         }
      }
      return contentTemplate;
   }
}
