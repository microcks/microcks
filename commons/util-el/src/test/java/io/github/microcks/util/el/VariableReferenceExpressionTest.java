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

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * This is a test case for VariableReferenceExpression class.
 * @author laurent
 */
public class VariableReferenceExpressionTest {

   @Test
   public void testStringValue() {
      EvaluableRequest request = new EvaluableRequest("hello world", null);

      // Create new expression evaluating simple string value.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("hello world", result);
   }

   @Test
   public void testJSONPointerValue() {
      String jsonString = "{\n" + "    \"library\": \"My Personal Library\",\n" + "    \"books\": [\n"
            + "        { \"title\":\"Title 1\", \"author\":\"Jane Doe\" },\n"
            + "        { \"title\":\"Title 2\", \"author\":\"John Doe\" }\n" + "    ]\n" + "}";
      EvaluableRequest request = new EvaluableRequest(jsonString, null);

      // Create new expression evaluating JSON Pointer path.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body/books/1/author");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("John Doe", result);
   }

   @Test
   public void testJSONPointerValueInArray() {
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
   public void testXPathValue() {
      String xmlString = "<library>\n" + "  <name>My Personal Library</name>\n" + "  <books>\n"
            + "    <book><title>Title 1</title><author>Jane Doe</author></book>\n"
            + "    <book><title>Title 2</title><author>John Doe</author></book>\n" + "  </books>\n" + "</library>";
      EvaluableRequest request = new EvaluableRequest(xmlString, null);

      // Create new expression evaluating XML XPath.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body/library/books/book[1]/author");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("Jane Doe", result);
   }

   @Test
   public void testXPathWithNamespaceValue() {
      String xmlString = "<ns:library xmlns:ns=\"https://microcks.io\">\n"
            + "  <ns:name>My Personal Library</ns:name>\n" + "  <ns:books>\n"
            + "    <ns:book><ns:title>Title 1</ns:title><ns:author>Jane Doe</ns:author></ns:book>\n"
            + "    <ns:book><ns:title>Title 2</ns:title><ns:author>John Doe</ns:author></ns:book>\n" + "  </ns:books>\n"
            + "</ns:library>";
      EvaluableRequest request = new EvaluableRequest(xmlString, null);

      // Create new expression evaluating XML XPath.
      VariableReferenceExpression exp = new VariableReferenceExpression(request, "body//*[local-name() = 'name']");
      String result = exp.getValue(new EvaluationContext());
      assertEquals("My Personal Library", result);
   }

   @Test
   public void testArrayValues() {
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
   public void testMapValues() {
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
