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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.TreeMultimap;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * This is a helper for extracting and building dispatch criteria from many sources.
 * @author laurent
 */
public class DispatchCriteriaHelper {

   private static final String CURLY_PART_PATTERN = "(\\{[^\\}]+\\})";
   private static final String CURLY_PART_EXTRACTION_PATTERN = "\\\\{(.+)\\\\}";

   private DispatchCriteriaHelper() {
      // Hide default no argument constructor as it's a utility class.
   }

   /**
    * Extract a dispatch rule string from URI parameters (specified using example values)
    * @param uri The URI containing parameters
    * @return A string representing dispatch rules for the corresponding incoming request.
    */
   public static String extractParamsFromURI(String uri) {
      if (uri.contains("?") && uri.contains("=")) {
         String parameters = uri.substring(uri.indexOf("?") + 1);
         StringBuilder params = new StringBuilder();

         for (String parameter : parameters.split("&")) {
            String[] pair = parameter.split("=");
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (params.length() > 0) {
               params.append(" && ");
            }
            params.append(key);
         }
         return params.toString();
      }
      return "";
   }

   /**
    * Extract the common prefix between a set of URIs
    * @param uris A set of URIs that are expected to share a common prefix
    * @return A string representing the common prefix of given URIs
    */
   public static String extractCommonPrefix(List<String> uris) {
      String commonURIPath = uris.get(0);

      // 1st pass on collection: find a common prefix.
      for (int prefixLen = 0; prefixLen < uris.get(0).length(); prefixLen++) {
         char c = uris.get(0).charAt(prefixLen);
         for (int i = 1; i < uris.size(); i++) {
            if (prefixLen >= uris.get(i).length() || uris.get(i).charAt(prefixLen) != c) {
               // Mismatch found.
               String commonString = uris.get(i).substring(0, prefixLen);
               return commonString.substring(0, commonString.lastIndexOf('/'));
            }
         }
      }
      return commonURIPath;
   }

   /**
    * Extract the common suffix between a set of URIs
    * @param uris A set of URIs that are expected to share a common suffix
    * @return A string representing the common suffix of given URIs
    */
   public static String extractCommonSuffix(List<String> uris) {
      // 1st pass on collection: find a common suffix.
      for (int suffixLen = 0; suffixLen < uris.get(0).length(); suffixLen++) {
         char c = uris.get(0).charAt(uris.get(0).length() - suffixLen - 1);
         for (int i = 1; i < uris.size(); i++) {
            if (suffixLen >= uris.get(i).length() || uris.get(i).charAt(uris.get(i).length() - suffixLen - 1) != c) {
               // Mismatch found. Have we found at least one common char ?
               if (suffixLen > 0) {
                  String commonString = uris.get(i).substring(uris.get(i).length() - suffixLen - 1);
                  if (commonString.indexOf('/') != -1) {
                     return commonString.substring(commonString.indexOf('/'));
                  }
                  return null;
               } else {
                  return null;
               }
            }
         }
      }
      return null;
   }

   /**
    * Extract from given URIs a dispatching rule representing the number of variable parts in this different URIs. For
    * example, given 'http://s/r/f//d/m/s' and 'http://s/r/f/d', method will detect 2 variable parts ('m' and 's').
    * Because it does know anything about the semantics of this parts, it produces a generic dispatch rule
    * <code>'part1 &amp;&amp; part2'</code> telling that URIs can be templatized like 'http://s/r/f/d/{part1}/{part2}
    * and that this 2 parts should be taken into account when dispatching request to response.
    * @param uris A set of URIs that are expected to share a common prefix
    * @return A string representing dispatch rules for the corresponding incoming request.
    */
   public static String extractPartsFromURIs(List<String> uris) {
      // 1st pass on collection: find a common prefix.
      String commonURIPath = extractCommonPrefix(uris);

      // 2nd pass on collection: find a common suffix.
      String commonURIEnd = extractCommonSuffix(uris);

      // 3rd pass on collection: guess the max number of part.
      int partsLen = 0;
      for (String uri : uris) {
         String parts = uri.substring(commonURIPath.length() + 1);
         if (commonURIEnd != null) {
            parts = parts.substring(0, parts.lastIndexOf(commonURIEnd));
         }
         int numOfParts = parts.split("/").length;
         if (numOfParts > partsLen) {
            partsLen = numOfParts;
         }
      }

      if (partsLen > 0) {
         StringBuilder parts = new StringBuilder();
         for (int i = 0; i < partsLen; i++) {
            parts.append("part").append(i + 1);
            if (i < partsLen - 1) {
               parts.append(" && ");
            }
         }
         return parts.toString();
      }
      return "";
   }

   /**
    * Build a template URL like 'http://s/r/f/d/{part1}/{part2}' with parts extracted from given URIs. For example,
    * given 'http://s/r/f/d/m/s' and 'http://s/r/f/d', method will detect 2 variable parts ('m' and 's'). Because it
    * does know anything about the semantics of this parts, it produces a generic dispatch rule
    * <code>'part1 &amp;&amp; part2'</code> telling that URIs can be templatized like 'http://s/r/f/d/{part1}/{part2}'
    * @param uris A set of URIs that are expected to share a common prefix
    * @return A templatized URL containing parts.
    */
   public static String buildTemplateURLWithPartsFromURIs(List<String> uris) {
      // 1st pass on collection: find a common prefix.
      String commonURIPath = extractCommonPrefix(uris);

      // 2nd pass on collection: find a common suffix.
      String commonURIEnd = extractCommonSuffix(uris);

      StringBuilder partsURI = new StringBuilder();
      partsURI.append(commonURIPath);

      int partsLen = 0;
      for (String uri : uris) {
         String parts = uri.substring(commonURIPath.length() + 1);
         if (commonURIEnd != null) {
            parts = parts.substring(0, parts.lastIndexOf(commonURIEnd));
         }
         int numOfParts = parts.split("/").length;
         if (numOfParts > partsLen) {
            partsLen = numOfParts;
         }
      }

      if (partsLen > 0) {
         for (int i = 0; i < partsLen; i++) {
            if (i == 0) {
               partsURI.append("/");
            }
            partsURI.append("{part").append(i + 1).append("}");
            if (i < partsLen - 1) {
               partsURI.append("/");
            }
         }
      }

      if (commonURIEnd != null) {
         partsURI.append("/").append(commonURIEnd);
      }

      return partsURI.toString();
   }

   /**
    * Extract a dispatch rule string from URI pattern (containing variable parts within {}) in order to explain which
    * parts are variables.
    * @param pattern The URI pattern containing variables parts ({} or :part patterns)
    * @return A string representing dispatch rules for the corresponding incoming request.
    */
   public static String extractPartsFromURIPattern(String pattern) {
      // Sanitize pattern as it may contains query params expressed using '{{}}'.
      if (pattern.contains("?")) {
         pattern = pattern.substring(0, pattern.indexOf('?'));
      }
      // and as it may contains variables using '{{}}'.
      if (pattern.contains("{{") && pattern.contains("}}")) {
         pattern = pattern.replace("{{", "");
         pattern = pattern.replace("}}", "");
      }
      // and as it may contains $ signs with forms like '/items/$count'.
      if (pattern.contains("$")) {
         pattern = pattern.replace("$", "");
      }

      // Build a pattern for extracting parts from pattern.
      String partsPattern = null;
      if (pattern.contains("/{")) {
         partsPattern = pattern.replaceAll(CURLY_PART_PATTERN, CURLY_PART_EXTRACTION_PATTERN);
      } else if (pattern.contains("/:")) {
         // We should add a leading / to avoid getting port number ;-)
         partsPattern = pattern.replaceAll("(/:[^:^/]+)", "\\/:(.+)");
      }
      return buildPartsDispatchRule(pattern, partsPattern);
   }

   /**
    * Extract a dispatch rule string from String pattern (containing variable parts within {}) in order to explain which
    * parts are variables.
    * @param pattern The String pattern containing variables parts ({} patterns)
    * @return A string representing dispatch rules for the corresponding incoming request.
    */
   public static String extractPartsFromStringPattern(String pattern) {
      // Build a pattern for extracting parts from pattern.
      String partsPattern = null;
      if (pattern.contains("{")) {
         partsPattern = pattern.replaceAll(CURLY_PART_PATTERN, CURLY_PART_EXTRACTION_PATTERN);
      }
      return buildPartsDispatchRule(pattern, partsPattern);
   }

   /** Apply a regexp on pattern to extract parts and create a dispatch rule. */
   private static String buildPartsDispatchRule(String pattern, String partsExtractPattern) {
      if (partsExtractPattern != null) {
         Pattern partsP = Pattern.compile(partsExtractPattern);
         Matcher partsM = partsP.matcher(pattern);

         if (partsM.matches()) {
            StringBuilder parts = new StringBuilder();
            for (int i = 1; i < partsM.groupCount() + 1; i++) {
               parts.append(partsM.group(i));
               if (i < partsM.groupCount()) {
                  parts.append(" && ");
               }
            }
            return parts.toString();
         }
      }
      return "";
   }

   /**
    * Extract and build a dispatch criteria string from URI pattern (containing variable parts within {} or prefixed
    * with :), projected onto a real instanciated URI.
    * @param paramsRuleString The dispatch rules referencing parameters to consider
    * @param pattern          The URI pattern containing variables parts ({})
    * @param realURI          The real URI that should match pattern.
    * @return A string representing dispatch criteria for the corresponding incoming request.
    */
   public static String extractFromURIPattern(String paramsRuleString, String pattern, String realURI) {
      Map<String, String> criteriaMap = new TreeMap<>();
      pattern = sanitizeURLForRegExp(pattern);
      realURI = sanitizeURLForRegExp(realURI);

      // Build a pattern for extracting parts from pattern and a pattern for extracting values
      // from realURI. Supporting both {id} and :id.
      String partsPattern = null;
      String valuesPattern = null;
      if (pattern.indexOf("/{") != -1) {
         partsPattern = pattern.replaceAll(CURLY_PART_PATTERN, CURLY_PART_EXTRACTION_PATTERN);
         valuesPattern = pattern.replaceAll(CURLY_PART_PATTERN, "(.+)");
      } else {
         partsPattern = pattern.replaceAll("(:[^:^/]+)", "\\:(.+)");
         valuesPattern = pattern.replaceAll("(:[^:^/]+)", "(.+)");
      }
      if (pattern.contains("$")) {
         partsPattern = partsPattern.replace("$", "\\$");
         valuesPattern = valuesPattern.replace("$", "\\$");
      }
      Pattern partsP = Pattern.compile(partsPattern);
      Matcher partsM = partsP.matcher(pattern);

      Pattern valuesP = Pattern.compile(valuesPattern);
      Matcher valuesM = valuesP.matcher(realURI);

      // Rule string can be a URI_ELEMENT rule and containers ?? elements.
      // We must remove them before parsing the URI parts.
      if (paramsRuleString.contains("??")) {
         paramsRuleString = paramsRuleString.split("\\?\\?")[0];
      }
      final var paramsRule = Arrays.stream(paramsRuleString.split("&&")).map(String::trim).distinct()
            .collect(Collectors.toUnmodifiableSet());

      // Both should match and have the same group count.
      if (valuesM.matches() && partsM.matches() && valuesM.groupCount() == partsM.groupCount()) {
         for (int i = 1; i < partsM.groupCount() + 1; i++) {
            final String paramName = partsM.group(i);
            final String paramValue = valuesM.group(i);
            if (paramsRule.contains(paramName)) {
               criteriaMap.put(paramName, paramValue);
            }
         }
      }

      // Just appends sorted entries, separating them with /.
      StringBuilder result = new StringBuilder();
      for (Map.Entry<String, String> criteria : criteriaMap.entrySet()) {
         result.append("/").append(criteria.getKey()).append("=").append(criteria.getValue());
      }
      return result.toString();
   }

   /**
    * Build a dispatch criteria string from map of parts (key is part name, value is part real value)
    * @param partsRule The dispatch rules referencing parts to consider
    * @param partsMap  The Map containing parts (not necessarily sorted)
    * @return A string representing dispatch criteria for the corresponding incoming request.
    */
   public static String buildFromPartsMap(String partsRule, Map<String, String> partsMap) {
      if (partsMap != null && !partsMap.isEmpty()) {
         Multimap<String, String> multimap = partsMap.entrySet().stream().collect(ArrayListMultimap::create,
               (m, e) -> m.put(e.getKey(), e.getValue()), Multimap::putAll);
         return buildFromPartsMap(partsRule, multimap);
      }
      return "";
   }


   /**
    * Build a dispatch criteria string from map of parts (key is part name, value is part real value)
    * @param partsRule The dispatch rules referencing parts to consider
    * @param partsMap  The Multimap containing parts (not necessarily sorted)
    * @return A string representing dispatch criteria for the corresponding incoming request.
    */
   public static String buildFromPartsMap(String partsRule, Multimap<String, String> partsMap) {
      if (partsMap != null && !partsMap.isEmpty()) {
         Multimap<String, String> criteriaMap = TreeMultimap.create(partsMap);

         // Just appends sorted entries, separating them with /.
         StringBuilder result = new StringBuilder();
         for (Map.Entry<String, String> criteria : criteriaMap.entries()) {
            /*
             * Check that criteria is embedded into the rule. Simply check word boundary with \b is not enough as - are
             * valid in params (according RFC 3986) but not included into word boundary - so "word-ext" string is
             * matching ".*\\bword\\b.*" We need to tweak it a bit to prevent matching when there's a - before or after
             * the criteria we're looking for (see
             * https://stackoverflow.com/questions/32380375/hyphen-dash-to-be-included-in-regex-word-boundary-b)
             */
            if (partsRule.matches(".*(^|[^-])\\b" + criteria.getKey() + "\\b([^-]|$).*")) {
               result.append("/").append(criteria.getKey()).append("=").append(criteria.getValue());
            }
         }
         return result.toString();
      }
      return "";
   }

   /**
    * Build a dispatch criteria string from URI parameters contained into a map
    * @param paramsRule The dispatch rules referencing parameters to consider
    * @param paramsMap  The Map containing URI params (not necessarily sorted)
    * @return A string representing a dispatch criteria for the corresponding incoming request.
    */
   public static String buildFromParamsMap(String paramsRule, Multimap<String, String> paramsMap) {
      if (paramsMap != null && !paramsMap.isEmpty()) {
         Multimap<String, String> criteriaMap = TreeMultimap.create(paramsMap);

         // Just appends sorted entries, separating them with ?.
         StringBuilder result = new StringBuilder();
         for (Map.Entry<String, String> criteria : criteriaMap.entries()) {
            /*
             * Check that criteria is embedded into the rule. Simply check word boundary with \b is not enough as - are
             * valid in params (according RFC 3986) but not included into word boundary - so "word-ext" string is
             * matching ".*\\bword\\b.*" We need to tweak it a bit to prevent matching when there's a - before or after
             * the criteria we're looking for (see
             * https://stackoverflow.com/questions/32380375/hyphen-dash-to-be-included-in-regex-word-boundary-b)
             */
            if (paramsRule.matches(".*(^|[^-])\\b" + criteria.getKey() + "\\b([^-]|$).*")) {
               result.append("?").append(criteria.getKey()).append("=").append(criteria.getValue());
            }
         }
         return result.toString();
      }
      return "";
   }

   /**
    * Extract and build a dispatch criteria string from URI parameters
    * @param paramsRule The dispatch rules referencing parameters to consider
    * @param uri        The URI from which we should build a specific dispatch criteria
    * @return A string representing a dispatch criteria for the corresponding incoming request.
    */
   public static String extractFromURIParams(String paramsRule, String uri) {
      Multimap<String, String> criteriaMap = TreeMultimap.create();

      if (uri.contains("?") && uri.contains("=")) {
         String parameters = uri.substring(uri.indexOf("?") + 1);

         for (String parameter : parameters.split("&")) {
            String[] pair = parameter.split("=");
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            criteriaMap.put(key, value);
         }

         // Just appends sorted entries, separating them with ?.
         StringBuilder result = new StringBuilder();
         for (Map.Entry<String, String> criteria : criteriaMap.entries()) {
            if (paramsRule.contains(criteria.getKey())) {
               result.append("?").append(criteria.getKey()).append("=").append(criteria.getValue());
            }
         }
         return result.toString();
      }
      return "";
   }

   /**
    * Extract and build a dispatch criteria string from URI parameters already stored into a Map.
    * @param paramsRule The dispatch rules referencing parameters to consider
    * @param paramMap   The URI fetched params from which we should build a specific dispatch criteria
    * @return A string representing a dispatch criteria for the corresponding incoming request.
    */
   public static String extractFromParamMap(String paramsRule, Map<String, String> paramMap) {
      Set<String> sortedKeys = paramMap.keySet().stream().sorted().collect(Collectors.toSet());

      StringBuilder result = new StringBuilder();
      for (String param : sortedKeys) {
         if (paramsRule.contains(param)) {
            result.append("?").append(param).append("=").append(paramMap.get(param));
         }
      }
      return result.toString();
   }

   private static String sanitizeURLForRegExp(String url) {
      return url.replace("+", " ");
   }
}
