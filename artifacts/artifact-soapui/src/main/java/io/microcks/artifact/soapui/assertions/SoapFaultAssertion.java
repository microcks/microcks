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

/**
 * An assertion that checks if response is actually a Soap Fault. It fails if not a Soap Fault.
 * @author laurent
 */
public class SoapFaultAssertion implements SoapUIAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapFaultAssertion.class);

   private String errorMessage;

   @Override
   public void configure(Map<String, String> configParams) {
   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      log.debug("Asserting response is a SOAP Fault");

      String responseContent = exchange.responseContent();
      // First check presence of Fault tag in content.
      if (!responseContent.contains(":Fault") && !responseContent.contains("<Fault")) {
         errorMessage = "Response is not a SOAP Fault";
         return AssertionStatus.FAILED;
      }
      return AssertionStatus.VALID;
   }

   @Override
   public List<String> getErrorMessages() {
      return List.of(errorMessage);
   }
}
