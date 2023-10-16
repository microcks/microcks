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
 * An assertion that checks if duration of an exchange is under given SLA.
 * @author laurent
 */
public class SLAAssertion implements SoapUIAssertion {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SLAAssertion.class);

   /** The SLA parameter of this assertion. Value is expected be a long integer string representation. */
   public static final String SLA_PARAM = "SLA";

   private long sla = 200L;

   private String errorMessage;

   @Override
   public void configure(Map<String, String> configParams) {
      if (configParams.containsKey(SLA_PARAM)) {
         try {
            sla = Long.valueOf(configParams.get(SLA_PARAM));
         } catch (NumberFormatException nfe) {
            log.warn("SLA config parameters cannot be cast to long, using 200ms as a default");
         }
      }
   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      log.debug("Asserting response SLA of {}, actual duration: {}", sla, exchange.duration());

      if (exchange.duration() > sla) {
         errorMessage = "Response did not meet SLA " + exchange.duration() + "/" + sla;
         return AssertionStatus.FAILED;
      }
      return AssertionStatus.VALID;
   }

   @Override
   public List<String> getErrorMessages() {
      return List.of(errorMessage);
   }
}
