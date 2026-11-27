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

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Answers like the Microcks mocks of API Pastry - 2.0 do, from the examples of its OpenAPI contract, taking 8 ms per
 * call. It keeps the requests it receives and the timeout of each.
 * @author SebastienDegodez
 */
final class PastryMocks implements HttpTransport {

   static final URI BASE_URL = URI.create("http://microcks/rest/API+Pastry+-+2.0/2.0.0");
   static final long DELAY = 8;

   private static final String PASTRIES = "[{\"name\":\"Baba Rhum\",\"price\":3.2,\"status\":\"available\"},"
         + "{\"name\":\"Divorces\",\"price\":2.8,\"status\":\"available\"},"
         + "{\"name\":\"Tartelette Fraise\",\"price\":2,\"status\":\"available\"}]";
   private static final String ECLAIR_CAFE = "{\"name\":\"Eclair Cafe\",\"size\":\"M\",\"price\":%s,"
         + "\"status\":\"available\"}";
   private static final Map<String, List<String>> JSON = Map.of("Content-Type", List.of("application/json"));
   private static final Map<String, List<String>> TEXT = Map.of("Content-Type", List.of("text/plain"));

   private final ManualClock clock;
   private final List<HttpCall> calls = new ArrayList<>();
   private final List<Duration> timeouts = new ArrayList<>();
   private IOException failure;
   private boolean repriceFails;

   PastryMocks(ManualClock clock) {
      this.clock = clock;
   }

   /** Every call ends with this exception rather than a response. */
   void failWith(IOException failure) {
      this.failure = failure;
   }

   /** The reprice of Eclair Cafe answers a server error, whose body is not a pastry. */
   void failReprice() {
      this.repriceFails = true;
   }

   List<HttpCall> calls() {
      return calls;
   }

   List<Duration> timeouts() {
      return timeouts;
   }

   @Override
   public HttpOutcome send(HttpCall call, Duration timeout) throws IOException {
      calls.add(call);
      timeouts.add(timeout);
      clock.advance(DELAY);
      if (failure != null) {
         throw failure;
      }
      String path = call.uri().getRawPath().substring(BASE_URL.getRawPath().length());
      return switch (call.method() + " " + path) {
         case "GET /pastry" -> new HttpOutcome(200, JSON, PASTRIES);
         case "GET /pastry/Eclair%20Cafe" -> new HttpOutcome(200, JSON, ECLAIR_CAFE.formatted("2.5"));
         case "PATCH /pastry/Eclair%20Cafe" ->
            repriceFails ? new HttpOutcome(500, JSON, "{\"message\":\"The pastry database is down\"}")
                  : new HttpOutcome(200, JSON, ECLAIR_CAFE.formatted("2.6"));
         default -> new HttpOutcome(400, TEXT, "The response " + path + " does not exist!");
      };
   }
}
