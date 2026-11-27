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
package io.github.microcks.util.postman;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for PostmanUtilTest class.
 * @author laurent
 */
class PostmanUtilTest {

   @Test
   void testAreOperationsEquivalent() {
      assertTrue(PostmanUtil.areOperationsEquivalent("GET /PaStRiEs", "get /pastries"));
      assertTrue(PostmanUtil.areOperationsEquivalent("GET /PaStRiEs/{name}", "get /pastries/:name"));
      assertTrue(PostmanUtil.areOperationsEquivalent("recommendation", "POST recommendation"));
   }
}
