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
package io.github.microcks.util.openapi;

import io.github.microcks.domain.ResponseExampleComparisonMode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonExampleComparatorTest {

   private static final ObjectMapper MAPPER = new ObjectMapper();

   @Test
   void shouldStrictlyMatchObjectsAndUnorderedArrays() throws JsonProcessingException {
      JsonNode expected = json("""
            {"items":[{"id":1,"name":"one"},{"id":2,"name":"two"}],"active":true}
            """);
      JsonNode actual = json("""
            {"active":true,"items":[{"name":"two","id":2.0},{"name":"one","id":1.0}]}
            """);

      assertTrue(JsonExampleComparator.matches(expected, actual, ResponseExampleComparisonMode.STRICT));
   }

   @Test
   void shouldRejectAdditionalContentInStrictMode() throws JsonProcessingException {
      JsonNode expected = json("""
            {"items":[{"id":1}]}
            """);

      assertFalse(JsonExampleComparator.matches(expected, json("""
            {"items":[{"id":1}],"traceId":"123"}
            """), ResponseExampleComparisonMode.STRICT));
      assertFalse(JsonExampleComparator.matches(expected, json("""
            {"items":[{"id":1},{"id":2}]}
            """), ResponseExampleComparisonMode.STRICT));
   }

   @Test
   void shouldAllowAdditionalNestedContentInLenientMode() throws JsonProcessingException {
      JsonNode expected = json("""
            {"items":[{"id":1},{"id":2}]}
            """);
      JsonNode actual = json("""
            {
              "traceId":"123",
              "items":[
                {"id":2,"name":"two"},
                {"id":3,"name":"three"},
                {"id":1,"name":"one"}
              ]
            }
            """);

      assertTrue(JsonExampleComparator.matches(expected, actual, ResponseExampleComparisonMode.LENIENT));
   }

   @Test
   void shouldRequireEveryExpectedArrayElementInLenientMode() throws JsonProcessingException {
      JsonNode expected = json("""
            [{"id":1},{"id":1}]
            """);
      JsonNode actual = json("""
            [{"id":1,"name":"one"},{"id":2,"name":"two"}]
            """);

      assertFalse(JsonExampleComparator.matches(expected, actual, ResponseExampleComparisonMode.LENIENT));
   }

   @Test
   void shouldFindDistinctMatchesForOverlappingLenientCandidates() throws JsonProcessingException {
      JsonNode expected = json("""
            [{"id":1},{"id":1,"kind":"primary"}]
            """);
      JsonNode actual = json("""
            [
              {"id":1,"kind":"primary"},
              {"id":1,"kind":"secondary"},
              {"id":2,"kind":"other"}
            ]
            """);

      assertTrue(JsonExampleComparator.matches(expected, actual, ResponseExampleComparisonMode.LENIENT));
   }

   private static JsonNode json(String content) throws JsonProcessingException {
      return MAPPER.readTree(content);
   }
}
