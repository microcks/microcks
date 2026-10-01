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
package io.github.microcks.util.opencollection;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Domain service extracting the path of an OpenCollection <code>http.url</code>. It extracts the path, it never
 * rewrites it: <code>:x</code> segments and <code>{{x}}</code> tokens are kept verbatim.
 * @author SebastienDegodez
 */
public final class OpenCollectionPathExtractor {

   private static final Pattern LEADING_VARIABLES = Pattern.compile("^(\\{\\{[^{}]*}})+");

   private static final Pattern LITERAL_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.\\-]*://");

   private OpenCollectionPathExtractor() {
      // Private constructor to hide the implicit one as it's a utility class.
   }

   /**
    * Extract the path of an URL, without its host prefix, its query and its fragment.
    * @param url The <code>http.url</code> of an OpenCollection item
    * @return The path, kept verbatim
    * @throws IllegalArgumentException with a human-readable reason when no path can be derived
    */
   public static String extractPath(String url) {
      String trimmedUrl = url.strip();
      if (trimmedUrl.isEmpty()) {
         throw new IllegalArgumentException("url is empty");
      }
      String path = withoutHostPrefix(withoutQueryAndFragment(trimmedUrl));
      if (path.isEmpty()) {
         return "/";
      }
      if (!path.startsWith("/")) {
         throw new IllegalArgumentException("no path found after host prefix");
      }
      if (path.chars().anyMatch(Character::isWhitespace)) {
         throw new IllegalArgumentException("path contains whitespace");
      }
      return path;
   }

   private static String withoutQueryAndFragment(String url) {
      int end = url.length();
      for (char separator : new char[] { '?', '#' }) {
         int position = url.indexOf(separator);
         end = position < 0 ? end : Math.min(end, position);
      }
      return url.substring(0, end);
   }

   private static String withoutHostPrefix(String url) {
      Matcher literalScheme = LITERAL_SCHEME.matcher(url);
      if (literalScheme.find()) {
         return withoutAuthority(url.substring(literalScheme.end()));
      }
      Matcher variables = LEADING_VARIABLES.matcher(url);
      if (!variables.find()) {
         return url;
      }
      String remainder = url.substring(variables.end());
      if (remainder.startsWith("://")) {
         return withoutAuthority(remainder.substring("://".length()));
      }
      return remainder.startsWith(":") ? withoutAuthority(remainder) : remainder;
   }

   private static String withoutAuthority(String authorityAndPath) {
      int pathStart = authorityAndPath.indexOf('/');
      return pathStart < 0 ? "" : authorityAndPath.substring(pathStart);
   }
}
