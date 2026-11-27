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

import java.util.List;
import java.util.Map;

/**
 * This defines an assertion that may be checked on a test response.
 * @author laurent
 */
public interface SoapUIAssertion {

   /**
    * Assertion may be configured before using it.
    * @param configParams A map of parameters of key:value
    */
   void configure(Map<String, String> configParams);

   /**
    * Assert validations on response at the end of an exchange.
    * @param exchange The raw data of this exchange (request/response, duration)
    * @param context  The context of this exchange (service, operation, resources)
    * @return The Assertion Status
    */
   AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context);

   /**
    * If assertion status is failed or unknown, some error messages may be retrieved later one.
    * @return A list of error messages if assertion didn't succeed.
    */
   List<String> getErrorMessages();
}
