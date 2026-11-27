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
 * Answers like the stateful Microcks mocks of the Order status API of the story do, taking 4 ms per call: the status of
 * order 42 is preparing, preparing, then ready, and starts over; order 7 is always preparing; order 999 is unknown; any
 * other order is ready. It keeps the requests it receives.
 * @author SebastienDegodez
 */
final class OrderStatusMocks implements HttpTransport {

   static final URI BASE_URL = URI.create("http://microcks/rest/Order+status/1.0.0");
   static final long DELAY = 4;

   private static final Map<String, List<String>> JSON = Map.of("Content-Type", List.of("application/json"));
   private static final Map<String, String> ITEMS = Map.of("1", "Baba Rhum", "7", "Tartelette Fraise", "42",
         "Eclair Cafe");

   private final ManualClock clock;
   private final List<HttpCall> calls = new ArrayList<>();
   private int order42Polls;
   private IOException failure;

   OrderStatusMocks(ManualClock clock) {
      this.clock = clock;
   }

   /** Every call ends with this exception rather than a response. */
   void failWith(IOException failure) {
      this.failure = failure;
   }

   List<HttpCall> calls() {
      return calls;
   }

   @Override
   public HttpOutcome send(HttpCall call, Duration timeout) throws IOException {
      calls.add(call);
      clock.advance(DELAY);
      if (failure != null) {
         throw failure;
      }
      String[] path = call.uri().getRawPath().substring(BASE_URL.getRawPath().length()).split("/");
      String orderId = path[2];
      if (orderId.equals("999")) {
         return new HttpOutcome(404, Map.of(), "");
      }
      if (path.length == 3) {
         return new HttpOutcome(200, JSON, "{\"id\":\"" + orderId + "\",\"item\":\"" + ITEMS.get(orderId) + "\"}");
      }
      return new HttpOutcome(200, JSON, "{\"status\":\"" + statusOf(orderId) + "\"}");
   }

   private String statusOf(String orderId) {
      if (orderId.equals("7")) {
         return "preparing";
      }
      if (!orderId.equals("42")) {
         return "ready";
      }
      order42Polls = (order42Polls + 1) % 3;
      return order42Polls == 0 ? "ready" : "preparing";
   }
}
