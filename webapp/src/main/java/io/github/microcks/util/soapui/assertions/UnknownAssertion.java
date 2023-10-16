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
 * Unknown assertion just fails all the time. Used for un-managed assertion types.
 * @author laurent
 */
public class UnknownAssertion implements SoapUIAssertion {

   private String type;

   public UnknownAssertion(String type) {
      this.type = type;
   }

   @Override
   public void configure(Map<String, String> configParams) {

   }

   @Override
   public AssertionStatus assertResponse(RequestResponseExchange exchange, ExchangeContext context) {
      return AssertionStatus.FAILED;
   }

   @Override
   public List<String> getErrorMessages() {
      return List.of("Assertion '" + type + "' is not managed by Microcks at the moment");
   }
}
