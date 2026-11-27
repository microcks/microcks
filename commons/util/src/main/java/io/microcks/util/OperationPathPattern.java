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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A path pattern built from an operation path template, used to match a request resource path. Only {@code {param}} and
 * {@code /:param} are path parameters matching a single path segment; every other character of the template, including
 * regex metacharacters and unresolved {@code {{variable}}} segments, is matched literally.
 */
public final class OperationPathPattern {

   private static final Pattern TEMPLATE_TOKEN = Pattern.compile("\\{\\{[^}]*}}|\\{[\\w-]+}|/:[^:^/]+");
   private static final String UNRESOLVED_VARIABLE_PREFIX = "{{";
   private static final String COLON_PARAMETER_PREFIX = "/:";
   private static final String SEGMENT_VALUE = "[^/]+";

   private final Pattern pattern;

   private OperationPathPattern(Pattern pattern) {
      this.pattern = pattern;
   }

   /**
    * Compile an operation path template into a pattern.
    * @param pathTemplate The operation path template (eg. {@code /pastry/{name}} or {@code /pastry/:name})
    * @return The pattern matching resource paths of this template
    */
   public static OperationPathPattern compile(String pathTemplate) {
      StringBuilder regex = new StringBuilder();
      Matcher tokens = TEMPLATE_TOKEN.matcher(pathTemplate);
      int literalStart = 0;
      while (tokens.find()) {
         regex.append(Pattern.quote(pathTemplate.substring(literalStart, tokens.start())));
         regex.append(toRegex(tokens.group()));
         literalStart = tokens.end();
      }
      regex.append(Pattern.quote(pathTemplate.substring(literalStart)));
      return new OperationPathPattern(Pattern.compile(regex.toString()));
   }

   /**
    * Tell if a resource path matches this pattern.
    * @param resourcePath The resource path of a request
    * @return true if the whole resource path matches
    */
   public boolean matches(String resourcePath) {
      return pattern.matcher(resourcePath).matches();
   }

   private static String toRegex(String token) {
      if (token.startsWith(UNRESOLVED_VARIABLE_PREFIX)) {
         return Pattern.quote(token);
      }
      if (token.startsWith(COLON_PARAMETER_PREFIX)) {
         return "/" + SEGMENT_VALUE;
      }
      return SEGMENT_VALUE;
   }
}
