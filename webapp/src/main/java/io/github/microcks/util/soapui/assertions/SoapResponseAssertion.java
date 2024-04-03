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

import io.github.microcks.util.soap.SoapMessageValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Assertion that checks if response body content is actually a valid Soap Envelope.
 * @author laurent
 */
public class SoapResponseAssertion implements SoapUIAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SoapResponseAssertion.class);

   private List<String> errorMessages;

   @Override
   public void configure(Map<String, String> configParams) {
   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      log.debug("Asserting response is a SOAP response");

      errorMessages = SoapMessageValidator.validateSoapEnvelope(exchange.responseContent());
      if (!errorMessages.isEmpty()) {
         return AssertionStatus.FAILED;
      }
      return AssertionStatus.VALID;
   }

   @Override
   public List<String> getErrorMessages() {
      return errorMessages;
   }
}
