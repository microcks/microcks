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

import io.github.microcks.domain.Parameter;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import org.springframework.web.util.UriUtils;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Helper class for building URIs from various objects.
 * @author laurent
 */
public class URIBuilder {

   private URIBuilder() {
      // Hide default no argument constructor as it's a utility class.
   }

   /**
    * Build a URI from a URI pattern (using {} or /: for marked variable parts) and using other query parameters
    * @param pattern    The URI pattern to use
    * @param parameters The set of parameters (whether template or query based)
    * @return The instanciated URI from template and parameters
    */
   public static String buildURIFromPattern(String pattern, List<Parameter> parameters) {
      if (parameters != null) {
         // Browse parameters and choose between template or query one.
         for (Parameter parameter : parameters) {
            String wadlTemplate = "{" + parameter.getName() + "}";
            String swaggerTemplate = "/:" + parameter.getName();
            if (pattern.contains(wadlTemplate)) {
               // It's a template parameter.
               pattern = pattern.replace(wadlTemplate, encodePath(parameter.getValue()));
            } else if (pattern.contains(swaggerTemplate)) {
               // It's a template parameter.
               pattern = pattern.replace(":" + parameter.getName(), encodePath(parameter.getValue()));
            } else {
               // It's a query parameter, ensure we have started delimiting them.
               if (!pattern.contains("?")) {
                  pattern += "?";
               }
               if (pattern.contains("=")) {
                  pattern += "&";
               }
               pattern += parameter.getName() + "=" + encodeValue(parameter.getValue());
            }
         }
      }
      return pattern;
   }

   /**
    * Build a URI from a URI pattern (using {} or /: for marked variable parts) and using other query parameters
    * @param pattern    The URI pattern to use
    * @param parameters The map of parameters K/V (whether template or query based)
    * @return The instanciated URI from template and parameters
    */
   public static String buildURIFromPattern(String pattern, Map<String, String> parameters) {
      if (parameters != null) {
         Multimap<String, String> multimap = parameters.entrySet().stream().collect(ArrayListMultimap::create,
               (m, e) -> m.put(e.getKey(), e.getValue()), Multimap::putAll);
         return buildURIFromPattern(pattern, multimap);
      }
      return pattern;
   }

   /**
    * Build a URI from a URI pattern (using {} or /: for marked variable parts) and using other query parameters
    * @param pattern    The URI pattern to use
    * @param parameters The Multimap of parameters K/V (whether template or query based)
    * @return The instanciated URI from template and parameters
    */
   public static String buildURIFromPattern(String pattern, Multimap<String, String> parameters) {
      if (parameters != null) {
         // Browse parameters and choose from template of query one.
         for (String parameterName : parameters.keySet()) {
            String wadltemplate = "{" + parameterName + "}";
            String swaggerTemplate = "/:" + parameterName;

            for (String parameterValue : parameters.get(parameterName)) {

               if (pattern.contains(wadltemplate)) {
                  // It's a template parameter.
                  pattern = pattern.replace(wadltemplate, encodePath(parameterValue));
               } else if (pattern.contains(swaggerTemplate)) {
                  // It's a template parameter.
                  pattern = pattern.replace(":" + parameterName, encodePath(parameterValue));
               } else {
                  // It's a query parameter, ensure we have started delimiting them.
                  if (!pattern.contains("?")) {
                     pattern += "?";
                  }
                  if (pattern.contains("=")) {
                     pattern += "&";
                  }
                  pattern += parameterName + "=" + encodeValue(parameterValue);
               }
            }
         }
      }
      return pattern;
   }

   /** Utility method for getting URL encoding of query parameter. */
   private static String encodeValue(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
   }

   /**
    * Utility method for getting URL encoding of path parameter. We cannot use JDK method that only deal with query
    * parameters value. See https://stackoverflow.com/a/2678602 and https://www.baeldung.com/java-url-encoding-decoding.
    */
   private static String encodePath(String path) {
      return UriUtils.encodePath(path, StandardCharsets.UTF_8);
   }
}
