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
package io.microcks.minion.arazzo.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the JSON pointers of response bodies, inputs, outputs and request body replacements.
 * @author SebastienDegodez
 */
class JsonPointerTest {

   private static final Map<String, Object> DOCUMENT = Map.of("a/b", 1, "m~n", 2, "list", List.of("x", "y"), "nested",
         Map.of("deep", "value"));

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "/a~1b | 1", "/m~0n | 2", "/list/0 | x", "/list/1 | y",
         "/nested/deep | value" })
   void aValueIsReadThroughObjectsAndArrays(String pointer, String value) {
      assertEquals(value, String.valueOf(JsonPointer.get(DOCUMENT, pointer, "the body")));
   }

   @Test
   void theEmptyPointerIsTheWholeValue() {
      assertSame(DOCUMENT, JsonPointer.get(DOCUMENT, "", "the body"));
   }

   @ParameterizedTest
   @CsvSource({ "/missing", "/list/2", "/list/-1", "/list/first", "/nested/deep/deeper" })
   void aMissingValueIsReported(String pointer) {
      assertEquals("No value at JSON pointer " + pointer + " in the body",
            assertThrows(ArazzoRunException.class, () -> JsonPointer.get(DOCUMENT, pointer, "the body")).getMessage());
   }

   @Test
   void aValueIsWrittenIntoObjectsAndArrays() {
      Map<String, Object> item = new LinkedHashMap<>(Map.of("quantity", 1));
      Map<String, Object> payload = new LinkedHashMap<>(Map.of("items", new ArrayList<>(List.of(item, "b"))));

      assertSame(payload, JsonPointer.set(payload, "/items/0/quantity", 3));
      assertSame(payload, JsonPointer.set(payload, "/items/1", "c"));
      assertSame(payload, JsonPointer.set(payload, "/tier", "GOLD"));

      assertEquals(Map.of("items", List.of(Map.of("quantity", 3), "c"), "tier", "GOLD"), payload);
      assertEquals("whole", JsonPointer.set(payload, "", "whole"));
   }

   @ParameterizedTest
   @CsvSource({ "/name/first", "/missing/first", "/list/0" })
   void aValueIsNotWrittenWhereThereIsNoMemberOrItem(String pointer) {
      Map<String, Object> payload = new LinkedHashMap<>(Map.of("name", "Eclair", "list", new ArrayList<>()));

      assertEquals("No value at JSON pointer " + pointer + " in the request body",
            assertThrows(ArazzoRunException.class, () -> JsonPointer.set(payload, pointer, "x")).getMessage());
   }
}
