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

import io.github.microcks.util.el.EvaluationContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link DynamicFakerELFunction}.
 */
class DynamicFakerELFunctionTest {

   @Test
   void testSimpleProviderMethod() {
      EvaluationContext ctx = new EvaluationContext();
      DynamicFakerELFunction f = new DynamicFakerELFunction();
      String city = f.evaluate(ctx, "address.city");
      assertNotNull(city);
      assertFalse(city.isBlank());
   }

   @Test
   void testInternetDomain() {
      EvaluationContext ctx = new EvaluationContext();
      DynamicFakerELFunction f = new DynamicFakerELFunction();
      String domain = f.evaluate(ctx, "internet.domainName");
      assertNotNull(domain);
      assertTrue(domain.contains("."));
   }

   @Test
   void testNumberBetween() {
      EvaluationContext ctx = new EvaluationContext();
      DynamicFakerELFunction f = new DynamicFakerELFunction();
      String number = f.evaluate(ctx, "number.numberBetween", "10", "20");
      assertNotNull(number);
      int value = Integer.parseInt(number);
      assertTrue(value >= 10 && value < 20);
   }
}
