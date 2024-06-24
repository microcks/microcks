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

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This is a Test case for Random functions based on Faker.
 * @author laurent
 */
class RandomFakerELFunctionTest {

   @Test
   void testNameEvaluations() {
      EvaluationContext context = new EvaluationContext();
      // Test simple evaluations.
      RandomFirstNameELFunction function = new RandomFirstNameELFunction();
      assertNotNull(function.evaluate(context));

      RandomLastNameELFunction lFunction = new RandomLastNameELFunction();
      assertNotNull(lFunction.evaluate(context));

      RandomFullNameELFunction fnFunction = new RandomFullNameELFunction();
      assertNotNull(fnFunction.evaluate(context));

      RandomNamePrefixELFunction npFunction = new RandomNamePrefixELFunction();
      assertNotNull(npFunction.evaluate(context));

      RandomNameSuffixELFunction nsFunction = new RandomNameSuffixELFunction();
      assertNotNull(nsFunction.evaluate(context));
   }

   @Test
   void testAddressEvaluations() {
      EvaluationContext context = new EvaluationContext();
      // Test simple evaluations.
      RandomCityELFunction cFunction = new RandomCityELFunction();
      assertNotNull(cFunction.evaluate(context));

      RandomCountryELFunction coFunction = new RandomCountryELFunction();
      assertNotNull(coFunction.evaluate(context));

      RandomStreetNameELFunction snFunction = new RandomStreetNameELFunction();
      assertNotNull(snFunction.evaluate(context));

      RandomStreetAddressELFunction saFunction = new RandomStreetAddressELFunction();
      assertNotNull(saFunction.evaluate(context));

      RandomPhoneNumberELFunction pFunction = new RandomPhoneNumberELFunction();
      assertNotNull(pFunction.evaluate(context));

      RandomLatitudeELFunction laFunction = new RandomLatitudeELFunction();
      assertNotNull(laFunction.evaluate(context));

      RandomLongitudeELFunction loFunction = new RandomLongitudeELFunction();
      assertNotNull(loFunction.evaluate(context));
   }

   @Test
   void testInternetEvaluations() {
      EvaluationContext context = new EvaluationContext();
      // Test simple evaluations.
      RandomEmailELFunction eFunction = new RandomEmailELFunction();
      assertNotNull(eFunction.evaluate(context));
   }
}
