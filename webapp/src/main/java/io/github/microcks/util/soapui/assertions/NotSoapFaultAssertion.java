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

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An assertion that checks if response is not Soap Fault. It fails if it's a Soap Fault.
 * @author laurent
 */
public class NotSoapFaultAssertion implements SoapUIAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(NotSoapFaultAssertion.class);

   /** Regular expression pattern for capturing Soap Fault name from body. */
   private static final Pattern FAULT_CAPTURE_PATTERN = Pattern.compile("(.*):Body>(\\s*)<((\\w+):|)Fault(.*)(/)?>(.*)",
         Pattern.DOTALL);

   private String errorMessage;

   @Override
   public void configure(Map<String, String> configParams) {
   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      log.debug("Asserting response is not a SOAP Fault");

      String responseContent = exchange.responseContent();
      // First check presence of Fault tag in content.
      if (responseContent.contains(":Fault") || responseContent.contains("<Fault")) {
         log.debug("Found some Fault in body");
         Matcher matcher = FAULT_CAPTURE_PATTERN.matcher(responseContent);
         if (matcher.find()) {
            log.debug("RegExp is matching, that's a fault!");
            errorMessage = "Response is a SOAP Fault";
            return AssertionStatus.FAILED;
         }
      }
      return AssertionStatus.VALID;
   }

   @Override
   public List<String> getErrorMessages() {
      return List.of(errorMessage);
   }
}
