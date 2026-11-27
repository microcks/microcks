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

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for VariableReferenceExpression class.
 * @author laurent
 */
class VariableReferenceExpressionTest {

   @Test
   void testStringValue() {
      EvaluableRequest request = new EvaluableRequest("hello world", null);

      // Create new expression evaluating simple string value.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("hello world", result);
   }

   @Test
   void testJSONPointerValue() {
      String jsonString = """
            {
              "library": "My Personal Library",
              "books": [
                {"title": "Title 1", "author": "Jane Doe" },
                {"title": "Title 2", "author": "John Doe" }
              ]
            }
            """;
      EvaluableRequest request = new EvaluableRequest(jsonString, null);

      // Create new expression evaluating JSON Pointer path.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body/books/1/author");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("John Doe", result);

      // Test extraction of Array by JSON Pointer path
      VariableReferenceExpression expArray = new VariableReferenceExpression(request, "body/books");
      String resultArray = expArray.getValue(new EvaluationContext());
      assertEquals("[{\"title\":\"Title 1\",\"author\":\"Jane Doe\"},{\"title\":\"Title 2\",\"author\":\"John Doe\"}]",
            resultArray);

      // Test extraction of Object by JSON Pointer path
      VariableReferenceExpression expObj = new VariableReferenceExpression(request, "body/books/1");
      String resultObj = expObj.getValue(new EvaluationContext());
      assertEquals("{\"title\":\"Title 2\",\"author\":\"John Doe\"}", resultObj);
   }

   @Test
   void testJSONPointerValueInArray() {
      String jsonString = "[{\"foo\":{\"bar\":111222},\"quantity\":1}]";
      EvaluableRequest request = new EvaluableRequest(jsonString, null);

      // Create new expression evaluating JSON Pointer path.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body/0/quantity");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("1", result);

      // Test with a nested expression
      exp = new VariableReferenceExpression(request, "body/0/foo/bar");
      result = exp.getValue(new EvaluationContext());
      assertEquals("111222", result);
   }

   @Test
   void testXPathValue() {
      String xmlString = """
            <library>
               <name>My Personal Library</name>
               <books>
                  <book><title>Title 1</title><author>Jane Doe</author></book>
                  <book><title>Title 2</title><author>John Doe</author></book>
               </books>
            </library>
            """;
      EvaluableRequest request = new EvaluableRequest(xmlString, null);

      // Create new expression evaluating XML XPath.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body/library/books/book[1]/author");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("Jane Doe", result);
   }

   @Test
   void testXPathWithNamespaceValue() {
      String xmlString = """
            <ns:library xmlns:ns="https://microcks.io">
               <ns:name>My Personal Library</ns:name>
               <ns:books>
                  <ns:book><ns:title>Title 1</ns:title><ns:author>Jane Doe</ns:author></ns:book>
                  <ns:book><ns:title>Title 2</ns:title><ns:author>John Doe</ns:author></ns:book>
               </ns:books>
            </ns:library>
            """;
      EvaluableRequest request = new EvaluableRequest(xmlString, null);

      // Create new expression evaluating XML XPath.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body//*[local-name() = 'name']");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("My Personal Library", result);
   }

   @Test
   void testArrayValues() {
      EvaluableRequest request = new EvaluableRequest(null, new String[] { "one", "two" });

      // Create new expression evaluating array value.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "path[0]");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("one", result);

      // Change array index.
      exp.setPathExpression("path[1]");
      result = exp.getValue(new EvaluationContext());
      assertEquals("two", result);

      // Test with incorrect index.
      exp.setPathExpression("path[2]");
      result = exp.getValue(new EvaluationContext());
      assertEquals("null", result);
   }

   @Test
   void testMapValues() {
      EvaluableRequest request = new EvaluableRequest(null, null);
      Map<String, String> headers = new HashMap<>();
      headers.put("key", "value");
      headers.put("hello", "world");
      headers.put("account_-name", "test");
      headers.put("account.name", "test");
      request.setHeaders(headers);

      // Create new expression evaluating map value.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "headers[hello]");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("world", result);

      // Test with key having special characters.
      exp.setPathExpression("headers[account_-name]");
      result = exp.getValue(new EvaluationContext());
      assertEquals("test", result);

      // Test with incorrect key.
      exp.setPathExpression("headers[microcks]");
      result = exp.getValue(new EvaluationContext());
      assertEquals("null", result);

      // Test with key having dot character.
      exp.setPathExpression("headers[account.name]");
      result = exp.getValue(new EvaluationContext());
      assertEquals("test", result);
   }
}
