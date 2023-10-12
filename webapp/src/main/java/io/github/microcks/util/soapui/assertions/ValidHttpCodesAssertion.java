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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * An assertion that checks if response http code is contained in specified ones. Default code is 200.
 * @author laurent
 */
public class ValidHttpCodesAssertion implements SoapUIAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ValidHttpCodesAssertion.class);

   /** The codes parameter. Value is expected to be a coma separated list of codes. eg: "401,403" */
   public static final String CODES_PARAM = "codes";

   private String codes = "200,";

   private String errorMessage;

   @Override
   public void configure(Map<String, String> configParams) {
      if (configParams.containsKey(CODES_PARAM)) {
         codes = configParams.get(CODES_PARAM);
         if (!codes.endsWith(",")) {
            codes += ',';
         }
      }
   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      log.debug("Asserting response Http codes in {}", codes);

      String responseCode = null;
      try {
         responseCode = String.valueOf(exchange.response().getStatusCode().value());
         log.debug("Response status code : " + responseCode);
      } catch (IOException ioe) {
         log.debug("IOException while getting raw status code in response", ioe);
         return AssertionStatus.FAILED;
      }

      if (!codes.contains(responseCode + ",")) {
         errorMessage = "Response status code:" + responseCode + " is not in acceptable list of status codes";
         return AssertionStatus.FAILED;
      }
      return AssertionStatus.VALID;
   }

   @Override
   public List<String> getErrorMessages() {
      return List.of(errorMessage);
   }
}
