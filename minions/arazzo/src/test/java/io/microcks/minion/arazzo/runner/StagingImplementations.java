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
package io.microcks.minion.arazzo.runner;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Answers like the staging implementations of the Customer and Order APIs, each at its own host. It keeps the requests
 * it receives, as they were sent.
 * @author SebastienDegodez
 */
final class StagingImplementations implements HttpTransport {

   static final String CUSTOMERS = "https://customers.staging.example";
   static final String ORDERS = "https://orders.staging.example";

   private static final Map<String, List<String>> JSON = Map.of("Content-Type", List.of("application/json"));

   private final List<HttpCall> calls = new ArrayList<>();

   List<HttpCall> calls() {
      return calls;
   }

   @Override
   public HttpOutcome send(HttpCall call, Duration timeout) {
      calls.add(call);
      String host = call.uri().getScheme() + "://" + call.uri().getAuthority();
      return switch (call.method() + " " + host + call.uri().getRawPath()) {
         case "GET " + CUSTOMERS + "/customers/c-42" ->
            new HttpOutcome(200, JSON, "{\"id\":\"c-42\",\"name\":\"Ada\"}");
         case "POST " + ORDERS + "/orders" -> new HttpOutcome(201, JSON, "{\"id\":\"o-7\",\"customerId\":\"c-42\"}");
         default -> new HttpOutcome(404, Map.of(), "");
      };
   }
}
