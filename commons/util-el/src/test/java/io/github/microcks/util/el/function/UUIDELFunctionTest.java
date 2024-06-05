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
package io.github.microcks.util.el.function;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for UUIDELFunction class.
 * @author laurent
 */
class UUIDELFunctionTest {

   @Test
   void testSimpleEvaluation() {
      // Compute uuid.
      UUIDELFunction function = new UUIDELFunction();
      String uuid = function.evaluate(null);

      // Check its RFC 4122 compliant.
      assertEquals(36, uuid.length());
      assertEquals(8, uuid.indexOf('-'));
      assertEquals(23, uuid.lastIndexOf('-'));
      assertEquals("-", uuid.substring(13, 14));
      assertEquals("4", uuid.substring(14, 15));
      assertEquals("-", uuid.substring(18, 19));
   }
}
