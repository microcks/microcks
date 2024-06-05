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

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for RandomValueEFFunction class.
 * @author laurent
 */
class RandomValueELFunctionTest {

   @Test
   void testSimpleEvaluation() {
      List<String> values = List.of("one", "two", "three");

      // Compute evaluation.
      RandomValueELFunction function = new RandomValueELFunction();
      String result = function.evaluate(null);
      assertEquals("", result);

      result = function.evaluate(null, "one", "two", "three");
      assertTrue(values.contains(result));
   }
}
