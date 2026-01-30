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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * These are test cases for OpenCollectionUtil class.
 * @author krisrr3
 */
class OpenCollectionUtilTest {

   @Test
   void testAreOperationsEquivalent() {
      // Test exact match (case insensitive)
      assertTrue(OpenCollectionUtil.areOperationsEquivalent("GET /pastries", "get /pastries"));
      assertTrue(OpenCollectionUtil.areOperationsEquivalent("POST /pastries", "post /pastries"));

      // Test path parameter equivalence (OpenAPI style vs OpenCollection style)
      assertTrue(OpenCollectionUtil.areOperationsEquivalent("GET /pastries/{name}", "get /pastries/:name"));
      assertTrue(OpenCollectionUtil.areOperationsEquivalent("GET /PaStRiEs/{name}", "get /pastries/:name"));
      assertTrue(OpenCollectionUtil.areOperationsEquivalent("DELETE /pastries/{country}/{name}",
            "delete /pastries/:country/:name"));

      // Test with verb prefix variations
      assertTrue(OpenCollectionUtil.areOperationsEquivalent("recommendation", "POST recommendation"));
      assertTrue(OpenCollectionUtil.areOperationsEquivalent("/users", "GET /users"));

      // Test non-equivalent operations
      assertFalse(OpenCollectionUtil.areOperationsEquivalent("GET /pastries", "POST /pastries"));
      assertFalse(OpenCollectionUtil.areOperationsEquivalent("GET /pastries/{name}", "GET /products/:id"));
   }

   @Test
   void testIsValidOpenCollectionVersion() {
      assertTrue(OpenCollectionUtil.isValidOpenCollectionVersion("1.0.0"));
      assertTrue(OpenCollectionUtil.isValidOpenCollectionVersion("1.0"));
      assertTrue(OpenCollectionUtil.isValidOpenCollectionVersion("1"));

      assertFalse(OpenCollectionUtil.isValidOpenCollectionVersion("0.9.0"));
      assertFalse(OpenCollectionUtil.isValidOpenCollectionVersion("2.0.0"));
      assertFalse(OpenCollectionUtil.isValidOpenCollectionVersion(null));
      assertFalse(OpenCollectionUtil.isValidOpenCollectionVersion(""));
   }
}
