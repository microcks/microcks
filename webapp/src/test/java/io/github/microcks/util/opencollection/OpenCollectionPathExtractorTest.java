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
package io.github.microcks.util.opencollection;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for class OpenCollectionPathExtractor: the rules of ADR-001 on the example table of the
 * contracts.
 * @author SebastienDegodez
 */
class OpenCollectionPathExtractorTest {

   static Stream<Arguments> urlsAndTheirPath() {
      return Stream.of(Arguments.of("{{baseUrl}}/api/users", "/api/users"),
            Arguments.of("{{baseUrl}}/pet/findByStatus", "/pet/findByStatus"),
            Arguments.of("{{baseUrl}}/pet/findByStatus?status=available", "/pet/findByStatus"),
            Arguments.of("{{baseUrl}}/pet/:petId", "/pet/:petId"),
            Arguments.of("{{ baseUrl }}/pet/:petId", "/pet/:petId"),
            Arguments.of("{{baseUrl}}/pet/:petId/uploadImage", "/pet/:petId/uploadImage"),
            Arguments.of("{{baseUrl}}/pet/{{petId}}", "/pet/{{petId}}"),
            Arguments.of("{{baseUrl}}/store/order/{{orderId}}.json", "/store/order/{{orderId}}.json"),
            Arguments.of("/{{tenant}}/pet", "/{{tenant}}/pet"), Arguments.of("{{baseUrl}}/pet/{{}}", "/pet/{{}}"),
            Arguments.of("{{host}}{{basePath}}/pet", "/pet"), Arguments.of("{{scheme}}://{{host}}/pet", "/pet"),
            Arguments.of("{{host}}:8080/pet", "/pet"), Arguments.of("{{host}}:{{port}}/pet", "/pet"),
            Arguments.of("https://petstore.example.com/v2/pet/:petId", "/v2/pet/:petId"),
            Arguments.of("http://{{host}}/pet", "/pet"), Arguments.of("http://localhost:8080", "/"),
            Arguments.of("/pet/:petId", "/pet/:petId"), Arguments.of("{{baseUrl}}/pet/", "/pet/"),
            Arguments.of("{{baseUrl}}/", "/"), Arguments.of("{{baseUrl}}", "/"),
            Arguments.of("{{baseUrl}}#section", "/"), Arguments.of("{{baseUrl}}/pet/:id.json", "/pet/:id.json"),
            Arguments.of("{{baseUrl}}/pet/%7Bid%7D", "/pet/%7Bid%7D"),
            Arguments.of("{{baseUrl}}/Pet//Find", "/Pet//Find"), Arguments.of("  {{baseUrl}}/pet  ", "/pet"),
            Arguments.of("https:///pet", "/pet"), Arguments.of("?status=available", "/"));
   }

   @ParameterizedTest
   @MethodSource("urlsAndTheirPath")
   void testExtractPath(String url, String expectedPath) {
      assertEquals(expectedPath, OpenCollectionPathExtractor.extractPath(url));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "{{baseUrl}}/pet/{{ petId }} | path contains whitespace",
         "{{baseUrl}}pet | no path found after host prefix",
         "petstore.example.com/pet | no path found after host prefix", "pet/:petId | no path found after host prefix" })
   void testExtractPathRejected(String url, String reason) {
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> OpenCollectionPathExtractor.extractPath(url));

      assertTrue(exception.getMessage().contains(reason));
   }

   @ParameterizedTest
   @ValueSource(strings = { "", "   " })
   void testExtractPathRejectsEmptyUrl(String url) {
      IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
            () -> OpenCollectionPathExtractor.extractPath(url));

      assertTrue(exception.getMessage().contains("url is empty"));
   }
}
