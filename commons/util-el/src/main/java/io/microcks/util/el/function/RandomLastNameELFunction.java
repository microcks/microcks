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

import net.datafaker.Faker;
import io.github.microcks.util.el.EvaluationContext;

/**
 * Implementation of ELFunction that generates a random lastname.
 * @author laurent
 */
public class RandomLastNameELFunction extends FakerELFunction {

   @Override
   public String evaluate(EvaluationContext evaluationContext, String... args) {
      Faker faker = retrieveFaker(evaluationContext);
      return faker.name().lastName();
   }
}
