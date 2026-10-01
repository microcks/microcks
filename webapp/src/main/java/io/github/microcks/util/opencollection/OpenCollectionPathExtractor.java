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

   private OpenCollectionPathExtractor() {
      // Private constructor to hide the implicit one as it's a utility class.
   }

   /**
    * Extract the path of an URL, without its host prefix, its query and its fragment.
    * @param url The <code>http.url</code> of an OpenCollection item
    * @return The path, kept verbatim
    */
   public static String extractPath(String url) {
      String path = url.strip();
      int queryOrFragment = indexOfQueryOrFragment(path);
      if (queryOrFragment >= 0) {
         path = path.substring(0, queryOrFragment);
      }
      Matcher variables = LEADING_VARIABLES.matcher(path);
      return variables.find() ? path.substring(variables.end()) : path;
   }

   private static int indexOfQueryOrFragment(String url) {
      int query = url.indexOf('?');
      int fragment = url.indexOf('#');
      if (query < 0 || fragment < 0) {
         return Math.max(query, fragment);
      }
      return Math.min(query, fragment);
   }
}
