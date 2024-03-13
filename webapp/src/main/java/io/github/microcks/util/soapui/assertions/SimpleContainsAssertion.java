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

import io.micrometer.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple assertion that checks for a specified token in response content.
 * @author laurent
 */
public class SimpleContainsAssertion implements SoapUIAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SimpleContainsAssertion.class);

   /** Expected token configuration parameter. */
   public static final String TOKEN_PARAM = "token";

   /**
    * Ignore case configuration parameter. Value is expected to be parsed as boolean: "true" or "false". Default is
    * false.
    */
   public static final String IGNORE_CASE_PARAM = "ignoreCase";

   /**
    * Consider token parameter as regular expression? Value is expected to be parsed as boolean: "true" or "false".
    * Default is false.
    */
   public static final String USE_REGEX_PARAM = "useRegEx";


   protected String token;

   protected boolean ignoreCase = false;

   protected boolean useRegEx = false;

   protected String errorMessage;


   @Override
   public void configure(Map<String, String> configParams) {
      token = configParams.get(TOKEN_PARAM);
      if (configParams.containsKey(IGNORE_CASE_PARAM)) {
         ignoreCase = Boolean.valueOf(configParams.get(IGNORE_CASE_PARAM));
      }
      if (configParams.containsKey(USE_REGEX_PARAM)) {
         useRegEx = Boolean.valueOf(configParams.get(USE_REGEX_PARAM));
      }
   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      log.debug("Asserting Simple contains for {}", token);
      if (token == null) {
         token = "";
      }

      String content = normalize(exchange.responseContent());

      int indexOfToken = -1;
      if (useRegEx) {
         String tokenToUse = ignoreCase ? "(?i)" + token : token;
         Pattern p = Pattern.compile(tokenToUse, Pattern.DOTALL);
         Matcher m = p.matcher(content);
         if (m.find()) {
            indexOfToken = 0;
         }
      } else {
         indexOfToken = ignoreCase ? content.toUpperCase().indexOf(token.toUpperCase()) : content.indexOf(token);
      }
      if (indexOfToken == -1) {
         errorMessage = "Missing token [" + token + "] in Response";
         return AssertionStatus.FAILED;
      }

      return AssertionStatus.VALID;
   }

   @Override
   public List<String> getErrorMessages() {
      return List.of(errorMessage);
   }

   private String normalize(String string) {
      if (StringUtils.isNotEmpty(string)) {
         string = string.replace("\r\n", "\n");
      }
      return string;
   }
}
