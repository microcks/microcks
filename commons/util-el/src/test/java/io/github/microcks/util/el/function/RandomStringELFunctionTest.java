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
 * This is a test case for RandomStringELFunction class.
 * @author laurent
 */
class RandomStringELFunctionTest {

   @Test
   void testSimpleEvaluation() {
      // Compute evaluation.
      RandomStringELFunction function = new RandomStringELFunction();
      String result = function.evaluate(null);

      assertEquals(RandomStringELFunction.DEFAULT_LENGTH, result.length());
   }

   @Test
   void testCustomSizeEvaluation() {
      // Compute evaluation.
      RandomStringELFunction function = new RandomStringELFunction();
      String result = function.evaluate(null, "64");

      assertEquals(64, result.length());
   }
}
