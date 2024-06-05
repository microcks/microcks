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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for JsonEvaluationSpecification class.
 * @author laurent
 */
class JsonEvaluationSpecificationTest {

   private final static String JSON_PAYLOAD = "{\"exp\": \"/type\", \"operator\": \"range\", \"cases\": {"
         + "\".*[Aa][Ll][Ee].*\": \"OK\", " + "\"default\": \"Bad\"" + "}}";

   @Test
   void testJsonSerialization() {
      DispatchCases cases = new DispatchCases();
      Map<String, String> dispatchCases = new HashMap<>();
      dispatchCases.put(".*[Aa][Ll][Ee].*", "OK");
      dispatchCases.put("default", "Bad");
      cases.putAll(dispatchCases);

      JsonEvaluationSpecification specifications = new JsonEvaluationSpecification();
      specifications.setExp("/type");
      specifications.setOperator(EvaluationOperator.regexp);
      specifications.setCases(cases);

      String jsonPayload = null;
      try {
         ObjectMapper mapper = new ObjectMapper();
         jsonPayload = mapper.writeValueAsString(specifications);
      } catch (Exception e) {
         fail("No exception should be thrown when writing Json payload...");
      }

      assertNotEquals(-1, jsonPayload.indexOf("exp"));
      assertNotEquals(-1, jsonPayload.indexOf("operator"));
      assertNotEquals(-1, jsonPayload.indexOf("cases"));
      assertNotEquals(-1, jsonPayload.indexOf("default"));
      assertNotEquals(-1, jsonPayload.indexOf(".*[Aa][Ll][Ee].*"));
   }

   @Test
   void testJsonDeserialization() {
      JsonEvaluationSpecification specification = null;
      try {
         specification = JsonEvaluationSpecification.buildFromJsonString(JSON_PAYLOAD);
      } catch (Exception e) {
         fail("No exception should be thrown when parsing Json payload...");
      }

      assertEquals("/type", specification.getExp());
      assertEquals(EvaluationOperator.range, specification.getOperator());
      assertEquals(2, specification.getCases().size());
      assertEquals("OK", specification.getCases().get(".*[Aa][Ll][Ee].*"));
      assertEquals("Bad", specification.getCases().getDefault());
   }
}
