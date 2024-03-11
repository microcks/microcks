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

import java.util.Random;

/**
 * This is an implementation of ELFunction that generates random Alphanumeric string. You may specify string length as
 * first argument. Default length is 32.
 * @author laurent
 */
public class RandomStringELFunction extends AbstractRandomELFunction {

   public static final int DEFAULT_LENGTH = 32;

   private static final int LEFT_LIMIT = 48; // numeral '0'
   private static final int RIGHT_LIMIT = 122; // letter 'z'

   @Override
   public String evaluate(EvaluationContext evaluationContext, String... args) {
      if (args != null && args.length == 1) {
         int maxLength = DEFAULT_LENGTH;
         try {
            maxLength = Integer.parseInt(args[0]);
         } catch (NumberFormatException nfe) {
            // Ignore, we'll stick to the default.
         }
         return generateString(getRandom(), maxLength);
      }
      return generateString(getRandom(), DEFAULT_LENGTH);
   }

   private String generateString(Random random, int length) {
      // See https://www.baeldung.com/java-random-string for reference.
      return random.ints(LEFT_LIMIT, RIGHT_LIMIT + 1).filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
            .limit(length).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
            .toString();
   }
}
