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
package io.github.microcks.util.el;

import java.util.Calendar;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for TemplateEngine class.
 * @author laurent
 */
class TemplateEngineTest {

   @Test
   void testSimpleTemplate() {
      // Prepare a string representing now().
      Calendar currentDate = Calendar.getInstance();// Assert formatting.
      int day = currentDate.get(Calendar.DAY_OF_MONTH);
      int month = currentDate.get(Calendar.MONTH);
      int year = currentDate.get(Calendar.YEAR);
      String dateString = (day < 10 ? "0" + day : day) + "/" + (++month < 10 ? "0" + month : month) + "/" + year;

      // Execute simple template calling now() and request.body function.
      EvaluableRequest request = new EvaluableRequest("hello world!", new String[] { "name", "Laurent" });

      TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
      engine.getContext().setVariable("request", request);

      String result = engine.getValue("Today is {{ now(dd/MM/yyyy) }} and {{ request.body }}");

      assertEquals("Today is " + dateString + " and hello world!", result);
   }

   @Test
   void testContextlessTemplate() {
      String template = "{\"signedAt\": \"{{ now() }}\", \"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": {{ randomInt(20, 99) }}} \n";

      TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

      String content = null;
      try {
         content = engine.getValue(template);
      } catch (Throwable t) {
         fail("Contextless template should not fail.");
      }
      assertTrue(content.startsWith("{\"signedAt\": \"1"));
   }

   @Test
   void testPostmanNotationCompatibility() {
      String template = "{\"signedAt\": \"{{ now() }}\", \"fullName\": \"{{ randomFullName() }}\", \"email\": \"{{ randomEmail() }}\", \"age\": {{ randomInt(20, 99) }} } \n";
      String postmanTemplate = "{\"signedAt\": \"{{ $timestamp }}\", \"fullName\": \"{{ $randomFullName }}\", \"email\": \"{{ $randomEmail }}\", \"age\": {{ $randomInt }} } \n";

      TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

      String content = null;
      String postmanContent = null;
      try {
         content = engine.getValue(template);
         postmanContent = engine.getValue(postmanTemplate);
      } catch (Throwable t) {
         fail("Contextless template should not fail.");
      }
      assertTrue(content.startsWith("{\"signedAt\": \"1"));
      assertTrue(postmanContent.startsWith("{\"signedAt\": \"1"));
   }

   @Test
   void testXMLWithAttributeTemplate() {
      // Execute simple template calling now() and request.body function.
      EvaluableRequest request = new EvaluableRequest("<request><name firstname=\"Laurent\"/></request>", null);

      TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
      engine.getContext().setVariable("request", request);

      String result = engine.getValue("<greeting>Hello {{request.body/request/name/@firstname}}</greeting>");

      assertEquals("<greeting>Hello Laurent</greeting>", result);
   }

   @Test
   void testXMLWithNSAndAttributeTemplate() {
      // Execute simple template calling now() and request.body function.
      EvaluableRequest request = new EvaluableRequest(
            "<ns:request xmlns:ns=\"http://example.com/ns\"><ns:name><firstname value=\"Laurent\"/></ns:name></ns:request>",
            null);

      TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
      engine.getContext().setVariable("request", request);

      String result = engine
            .getValue("<greeting>Hello {{request.body//*[local-name() = 'name']/firstname/@value}}</greeting>");

      assertEquals("<greeting>Hello Laurent</greeting>", result);
   }

   @Test
   void testRequestParams() {
      EvaluableRequest request = new EvaluableRequest("", null);
      Map<String, String> params = Map.of("id", "8", "account-name", "test");
      request.setParams(params);

      TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
      engine.getContext().setVariable("request", request);

      String result = engine
            .getValue("{ \"id\": \"{{request.params[id]}}\", \"accountName\": \"{{request.params[account-name]}}\" }");

      assertEquals("{ \"id\": \"8\", \"accountName\": \"test\" }", result);
   }

   @Test
   void testFallbackExpressionTemplate() {
      // Prepare request with body and params.
      EvaluableRequest request = new EvaluableRequest("{ \"foo\": \"value\" }", null);
      request.setParams(Map.of("foo", "paramValue"));

      // Prepare an engine with request variable.
      TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
      engine.getContext().setVariable("request", request);

      // Matching case with body.
      String result = engine.getValue("{ \"result\": \"{{ request.body/foo || 'default' }}\" }");
      assertEquals("{ \"result\": \"value\" }", result);

      // Fallback case with body.
      result = engine.getValue("{ \"result\": \"{{ request.body/bar || 'default' }}\" }");
      assertEquals("{ \"result\": \"default\" }", result);

      // Matching case with params.
      result = engine.getValue("{ \"result\": \"{{ request.params[foo] || 'default' }}\" }");
      assertEquals("{ \"result\": \"paramValue\" }", result);

      // Matching case with params.
      result = engine.getValue("{ \"result\": \"{{ request.params[bar] || 'default' }}\" }");
      assertEquals("{ \"result\": \"default\" }", result);
   }
}
