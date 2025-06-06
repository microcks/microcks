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

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Utf8ContentTypeChecker {
   private static final Pattern UTF8_ENCODABLE_PATTERN = Pattern.compile(
         "^(text/[a-z0-9.+-]+|application/([a-z0-9.+-]*\\+(json|xml)|json|xml|javascript|x-www-form-urlencoded))$",
         Pattern.CASE_INSENSITIVE);

   public static boolean isUtf8Encodable(String contentType) {
      if (contentType == null)
         return false;
      Matcher matcher = UTF8_ENCODABLE_PATTERN.matcher(contentType.trim());
      return matcher.matches();
   }

   public static void main(String[] args) {
      String[] testTypes = { "text/plain", "application/json", "application/vnd.api+json", "application/soap+xml",
            "application/pdf", "image/png" };

      for (String type : testTypes) {
         System.out.printf("%-35s -> %s%n", type, isUtf8Encodable(type));
      }
   }
}
