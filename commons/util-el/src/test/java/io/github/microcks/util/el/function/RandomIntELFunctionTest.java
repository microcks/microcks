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

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for RandomIntELFunction class.
 * @author laurent
 */
class RandomIntELFunctionTest {

   @Test
   void testSimpleEvaluation() {
      // Compute evaluation.
      RandomIntELFunction function = new RandomIntELFunction();
      String randomIntString = function.evaluate(null);

      int randomInt = Integer.parseInt(randomIntString);
      assertTrue(randomInt >= Integer.MIN_VALUE);
      assertTrue(randomInt <= Integer.MAX_VALUE);
   }

   @Test
   void testBoundedEvaluation() {
      // Compute evaluation.
      RandomIntELFunction function = new RandomIntELFunction();
      String randomIntString = function.evaluate(null, "50");

      int randomInt = Integer.parseInt(randomIntString);
      assertTrue(randomInt >= 0);
      assertTrue(randomInt <= 50);
   }

   @Test
   void testIntervalEvaluation() {
      // Compute evaluation.
      RandomIntELFunction function = new RandomIntELFunction();
      String randomIntString = function.evaluate(null, "25", "50");

      int randomInt = Integer.parseInt(randomIntString);
      assertTrue(randomInt >= 25);
      assertTrue(randomInt <= 50);
   }
}
