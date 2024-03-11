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
package io.github.microcks.util;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for sanitizing strings to be used in a URL.
 */
public class Sanitizer {
   private static final String ALLOWED_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ$-_.+!*'(),";
   private static final Set<Character> ALLOWED_CHARS_SET = ALLOWED_CHARS.chars().mapToObj(c -> (char) c)
         .collect(Collectors.toSet());
   private static final Character REPLACE_CHAR = '-';

   private Sanitizer() {
      // Hide the implicit constructor as it's a utility class.
   }

   /**
    * Sanitize a string to be used in a URL. It replaces all characters that are not in the set of allowed characters by
    * a dash.
    * @param string the string to sanitize
    * @return the sanitized string
    */
   public static String urlSanitize(String string) {
      StringBuilder sanitized = new StringBuilder();

      for (char c : string.toCharArray()) {
         sanitized.append(ALLOWED_CHARS_SET.contains(c) ? c : REPLACE_CHAR);
      }

      return sanitized.toString();
   }
}
