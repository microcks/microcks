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
package io.github.microcks.util.soapui.assertions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * A base class for assertions needing to du fuzzy matching using wildcards.
 * @author laurent
 */
public abstract class WildcardMatchingAssertion implements SoapUIAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(WildcardMatchingAssertion.class);

   /**
    * Allow wildcards configuration parameter. Value is expected to be parsed as boolean: "true" or "false". Default is
    * false.
    */
   public static final String ALLOW_WILDCARDS = "allowWildcards";

   protected boolean allowWildcards = false;

   @Override
   public void configure(Map<String, String> configParams) {
      if (configParams.containsKey(ALLOW_WILDCARDS)) {
         allowWildcards = Boolean.valueOf(configParams.get(ALLOW_WILDCARDS));
      }
   }

   /**
    * Managed wildcards specified in expected content to check if real content is similar.
    * @param expectedContent Expected content with wildcards
    * @param realContent     Real content to match
    * @return true if matching, false otherwise
    */
   protected boolean isSimilar(String expectedContent, String realContent) {
      // Build a pattern from expected content.
      StringBuilder patternBuilder = new StringBuilder();
      if (expectedContent.startsWith("*")) {
         patternBuilder.append(".*");
      }
      String[] tokens = expectedContent.split(Pattern.quote("*"));
      boolean first = true;
      for (int i = 0; i < tokens.length; ++i) {
         String token = tokens[i];
         if (!token.isEmpty()) {
            if (!first) {
               patternBuilder.append(".*");
            }
            first = false;
            patternBuilder.append(Pattern.quote(token));
         }
      }
      if (expectedContent.endsWith("*")) {
         patternBuilder.append(".*");
      }

      log.debug("Compiled a pattern for wildcard match, will use '{}'", patternBuilder);

      return Pattern.compile(patternBuilder.toString()).matcher(realContent).matches();
   }
}
