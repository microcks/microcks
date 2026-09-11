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

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.Map;

/**
 * Compares JSON examples with actual response bodies using unordered array semantics.
 */
final class JsonExampleComparator {

   private JsonExampleComparator() {
      // Hide implicit public constructor.
   }

   /**
    * Check whether an actual JSON body matches an expected example in the requested mode.
    * @param expected expected response example
    * @param actual   actual response body
    * @param mode     comparison mode
    * @return {@code true} when the actual body matches the example
    */
   static boolean matches(JsonNode expected, JsonNode actual, ResponseExampleComparisonMode mode) {
      if (expected == null || actual == null || expected.getNodeType() != actual.getNodeType()) {
         // Jackson uses different node types for integral and decimal values although both are JSON numbers.
         return expected != null && actual != null && expected.isNumber() && actual.isNumber()
               && expected.decimalValue().compareTo(actual.decimalValue()) == 0;
      }

      if (expected.isObject()) {
         return objectsMatch(expected, actual, mode);
      }
      if (expected.isArray()) {
         return arraysMatch(expected, actual, mode);
      }
      if (expected.isNumber()) {
         return expected.decimalValue().compareTo(actual.decimalValue()) == 0;
      }
      return expected.equals(actual);
   }

   private static boolean objectsMatch(JsonNode expected, JsonNode actual, ResponseExampleComparisonMode mode) {
      if (ResponseExampleComparisonMode.STRICT.equals(mode) && expected.size() != actual.size()) {
         return false;
      }
      for (Map.Entry<String, JsonNode> property : expected.properties()) {
         if (!actual.has(property.getKey()) || !matches(property.getValue(), actual.get(property.getKey()), mode)) {
            return false;
         }
      }
      return true;
   }

   private static boolean arraysMatch(JsonNode expected, JsonNode actual, ResponseExampleComparisonMode mode) {
      if (ResponseExampleComparisonMode.STRICT.equals(mode) && expected.size() != actual.size()) {
         return false;
      }
      if (expected.size() > actual.size()) {
         return false;
      }

      // Find a distinct actual element for every expected element. A maximum matching avoids false negatives when
      // lenient object comparisons make more than one actual element a candidate for an expected element.
      int[] actualMatches = new int[actual.size()];
      Arrays.fill(actualMatches, -1);
      for (int expectedIndex = 0; expectedIndex < expected.size(); expectedIndex++) {
         boolean[] visitedActualElements = new boolean[actual.size()];
         if (!findArrayElementMatch(expectedIndex, expected, actual, mode, actualMatches, visitedActualElements)) {
            return false;
         }
      }
      return true;
   }

   private static boolean findArrayElementMatch(int expectedIndex, JsonNode expected, JsonNode actual,
         ResponseExampleComparisonMode mode, int[] actualMatches, boolean[] visitedActualElements) {
      for (int actualIndex = 0; actualIndex < actual.size(); actualIndex++) {
         if (!visitedActualElements[actualIndex]
               && matches(expected.get(expectedIndex), actual.get(actualIndex), mode)) {
            visitedActualElements[actualIndex] = true;
            if (actualMatches[actualIndex] == -1 || findArrayElementMatch(actualMatches[actualIndex], expected, actual,
                  mode, actualMatches, visitedActualElements)) {
               actualMatches[actualIndex] = expectedIndex;
               return true;
            }
         }
      }
      return false;
   }
}
