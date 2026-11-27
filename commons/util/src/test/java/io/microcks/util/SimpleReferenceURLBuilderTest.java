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
package io.github.microcks.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for SimpleReferenceURLBuilder.
 * @author laurent
 */
class SimpleReferenceURLBuilderTest {

   private static final String BASE_URL = "https://raw.githubusercontent.com/microcks/microcks/main/samples/API_Pastry_1.0.0-openapi.yaml";

   @Test
   void testGetFileName() {
      SimpleReferenceURLBuilder builder = new SimpleReferenceURLBuilder();
      assertEquals("API_Pastry_1.0.0-openapi.yaml", builder.getFileName(BASE_URL, null));
   }

   @Test
   void testBuildRemoteURL() {

      SimpleReferenceURLBuilder builder = new SimpleReferenceURLBuilder();
      assertEquals("https://raw.githubusercontent.com/microcks/microcks/main/samples/schema-ref.yml",
            builder.buildRemoteURL(BASE_URL, "schema-ref.yml"));
      assertEquals("https://raw.githubusercontent.com/microcks/microcks/main/samples/schema-ref.yml",
            builder.buildRemoteURL(BASE_URL, "./schema-ref.yml"));
      assertEquals("https://raw.githubusercontent.com/microcks/microcks/main/refs/schema-ref.yml",
            builder.buildRemoteURL(BASE_URL, "../refs/schema-ref.yml"));
      assertEquals("https://raw.githubusercontent.com/microcks/microcks/main/refs/sub/schema-ref.yml",
            builder.buildRemoteURL(BASE_URL, "../refs/sub/schema-ref.yml"));
      assertEquals("https://raw.githubusercontent.com/microcks/microcks/refs/sub/schema-ref.yml",
            builder.buildRemoteURL(BASE_URL, "../../refs/sub/schema-ref.yml"));
   }
}
