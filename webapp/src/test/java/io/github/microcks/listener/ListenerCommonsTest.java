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

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for ListenerCommons class.
 * @author laurent
 */
class ListenerCommonsTest {

   @Test
   void testRenderContentWithValidTemplateAndParameters() {
      // Configuring the request snapshot with body, headers, and query parameters
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/test-path",
            Map.of("Authorization", List.of("Bearer mockToken")), Map.of("userId", new String[] { "12345" }),
            "{\"name\":\"test\"}");

      // Simulating the template evaluation process
      String template = "Hello, {{request.params[userId]}}!";

      String renderedContent = ListenerCommons.renderContent(snapshot, template);

      // Assertions
      assertEquals("Hello, 12345!", renderedContent);
   }

   @Test
   void testRenderContentWithMissingTemplateData() {
      // Configuring the request snapshot with a parameter and no relevant data for the template
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/test-path", Map.of(),
            Map.of("missingKey", new String[] { "dummyValue" }), null);

      // Validating that the template return remains untouched when not evaluated
      String template = "Missing {{request.params[userId]}} data";

      String renderedContent = ListenerCommons.renderContent(snapshot, template);

      assertEquals("Missing null data", renderedContent);
   }

   @Test
   void testRenderContentWithoutTemplateExpression() {
      // Configuring a request snapshot
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/test-path", Map.of(), Map.of(), null);

      // Using a template without an expression
      String plainContent = "Plain Content";
      String renderedContent = ListenerCommons.renderContent(snapshot, plainContent);

      // Assertions
      assertEquals(plainContent, renderedContent);
   }

   @Test
   void testRenderContentWithTemplateEvaluationError() {
      // Configuring the request snapshot
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/test-path", Collections.emptyMap(),
            Collections.emptyMap(), null);

      // Simulating a runtime exception during template evaluation
      String template = "Error {{request.body}}";

      // Attempting the evaluation which should handle the exception gracefully
      String renderedContent = ListenerCommons.renderContent(snapshot, template);

      // Assertions: fallback to the original template
      assertEquals("Error null", renderedContent);
   }
}
