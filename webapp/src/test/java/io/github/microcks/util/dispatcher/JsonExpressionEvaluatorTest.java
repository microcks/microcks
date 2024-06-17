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
package io.github.microcks.util.dispatcher;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for JsonExpressionEvaluator.
 * @author laurent
 */
class JsonExpressionEvaluatorTest {

   private static final String BELGIUM_BEER = "{\"name\": \"Maredsous\","
         + "\"country\": \"Belgium\", \"type\": \"Brown ale\"," + "\"rating\": 4.2, \"status\": \"available\"}";

   private static final String GERMAN_BEER = "{\"name\": \"Weissbier\","
         + "\"country\": \"Germany\", \"type\": \"Wheat\","
         + "\"rating\": 3.5, \"status\": \"out_of_stock\", \"extra\": \"Extra Information\"}";

   private static final String EXTRA_GERMAN_BEER = "{\"name\": \"Weissbier\","
         + "\"country\": \"Germany\", \"type\": \"Wheat\","
         + "\"rating\": 3.5, \"status\": \"out_of_stock\", \"extra\": {\"value\": \"Extra Information\"}}";

   private static final String ENGLISH_BEER = "{\"name\": \"Guinness\"," + "\"country\": \"UK\", \"type\": \"Black\","
         + "\"rating\": 4.1, \"status\": \"available\"}";

   private static final String LAURENT_CARS = "{\"driver\": \"Laurent\", \"cars\": ["
         + "{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}, "
         + "{\"name\": \"jean-pierre\", \"model\": \"Peugeot Traveller\", \"year\": 2017}]}";

   private static final String LOT_OF_CARS = "{\"driver\": \"Laurent\", \"cars\": ["
         + "{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}, "
         + "{\"name\": \"308\", \"model\": \"Peugeot 308\", \"year\": 2014}, "
         + "{\"name\": \"jean-pierre\", \"model\": \"Peugeot Traveller\", \"year\": 2017}]}";

   private static final String HUGE_LIST_OF_CARS = "{\"driver\": \"Laurent\", \"cars\": ["
         + "{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}, "
         + "{\"name\": \"308\", \"model\": \"Peugeot 308\", \"year\": 2014}, "
         + "{\"name\": \"508\", \"model\": \"Peugeot 508\", \"year\": 2015}, "
         + "{\"name\": \"3008\", \"model\": \"Peugeot 3008\", \"year\": 2016}, "
         + "{\"name\": \"5008\", \"model\": \"Peugeot 5008\", \"year\": 2017}, "
         + "{\"name\": \"maurice\", \"model\": \"Peugeot 5008\", \"year\": 2017}, "
         + "{\"name\": \"jean-pierre\", \"model\": \"Peugeot Traveller\", \"year\": 2017}]}";

   @Test
   void testEqualsOperatorDispatcher() throws Exception {

      DispatchCases cases = new DispatchCases();
      Map<String, String> dispatchCases = new HashMap<>();
      dispatchCases.put("Belgium", "OK");
      dispatchCases.put("Germany", "KO");
      dispatchCases.put("default", "Why not?");
      cases.putAll(dispatchCases);

      JsonEvaluationSpecification specifications = new JsonEvaluationSpecification();
      specifications.setExp("/country");
      specifications.setOperator(EvaluationOperator.equals);
      specifications.setCases(cases);

      String result = JsonExpressionEvaluator.evaluate(BELGIUM_BEER, specifications);
      assertEquals("OK", result);

      result = JsonExpressionEvaluator.evaluate(GERMAN_BEER, specifications);
      assertEquals("KO", result);

      result = JsonExpressionEvaluator.evaluate(ENGLISH_BEER, specifications);
      assertEquals("Why not?", result);
   }

   @Test
   void testRangeOperatorDispatcher() throws Exception {
      DispatchCases cases = new DispatchCases();
      Map<String, String> dispatchCases = new HashMap<>();
      dispatchCases.put("[4.2;5.0]", "Acceptable");
      dispatchCases.put("[0;4[", "Too low");
      dispatchCases.put("default", "Why not?");
      cases.putAll(dispatchCases);

      JsonEvaluationSpecification specifications = new JsonEvaluationSpecification();
      specifications.setExp("/rating");
      specifications.setOperator(EvaluationOperator.range);
      specifications.setCases(cases);

      String result = JsonExpressionEvaluator.evaluate(BELGIUM_BEER, specifications);
      assertEquals("Acceptable", result);

      result = JsonExpressionEvaluator.evaluate(GERMAN_BEER, specifications);
      assertEquals("Too low", result);

      result = JsonExpressionEvaluator.evaluate(ENGLISH_BEER, specifications);
      assertEquals("Why not?", result);
   }

   @Test
   void testRegexpOperatorDispatcher() throws Exception {
      DispatchCases cases = new DispatchCases();
      Map<String, String> dispatchCases = new HashMap<>();
      dispatchCases.put(".*[Aa][Ll][Ee].*", "OK");
      dispatchCases.put("default", "Bad");
      cases.putAll(dispatchCases);

      JsonEvaluationSpecification specifications = new JsonEvaluationSpecification();
      specifications.setExp("/type");
      specifications.setOperator(EvaluationOperator.regexp);
      specifications.setCases(cases);

      String result = JsonExpressionEvaluator.evaluate(BELGIUM_BEER, specifications);
      assertEquals("OK", result);

      result = JsonExpressionEvaluator.evaluate(GERMAN_BEER, specifications);
      assertEquals("Bad", result);

      result = JsonExpressionEvaluator.evaluate(ENGLISH_BEER, specifications);
      assertEquals("Bad", result);
   }

   @Test
   void testSizeOperatorDispatcher() throws Exception {
      DispatchCases cases = new DispatchCases();
      Map<String, String> dispatchCases = new HashMap<>();
      dispatchCases.put("[0;2]", "Standard");
      dispatchCases.put("[3;5]", "Lot of");
      dispatchCases.put("default", "Huge");
      cases.putAll(dispatchCases);

      JsonEvaluationSpecification specifications = new JsonEvaluationSpecification();
      specifications.setExp("/cars");
      specifications.setOperator(EvaluationOperator.size);
      specifications.setCases(cases);

      String result = JsonExpressionEvaluator.evaluate(LAURENT_CARS, specifications);
      assertEquals("Standard", result);

      result = JsonExpressionEvaluator.evaluate(LOT_OF_CARS, specifications);
      assertEquals("Lot of", result);

      result = JsonExpressionEvaluator.evaluate(HUGE_LIST_OF_CARS, specifications);
      assertEquals("Huge", result);
   }

   @Test
   void testPresenceOperatorDispatcher() throws Exception {
      DispatchCases cases = new DispatchCases();
      Map<String, String> dispatchCases = new HashMap<>();
      dispatchCases.put("found", "Extra");
      dispatchCases.put("missing", "Normal");
      dispatchCases.put("default", "Basic");
      cases.putAll(dispatchCases);

      JsonEvaluationSpecification specifications = new JsonEvaluationSpecification();
      specifications.setExp("/extra");
      specifications.setOperator(EvaluationOperator.presence);
      specifications.setCases(cases);

      String result = JsonExpressionEvaluator.evaluate(BELGIUM_BEER, specifications);
      assertEquals("Normal", result);

      result = JsonExpressionEvaluator.evaluate(GERMAN_BEER, specifications);
      assertEquals("Extra", result);

      result = JsonExpressionEvaluator.evaluate(ENGLISH_BEER, specifications);
      assertEquals("Normal", result);

      result = JsonExpressionEvaluator.evaluate(EXTRA_GERMAN_BEER, specifications);
      assertEquals("Extra", result);
   }
}
