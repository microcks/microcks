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
package io.microcks.util.tracing;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.context.Context;

import java.util.List;
import java.util.Optional;

/**
 * How a mock invocation names the Arazzo verification session the mocks record it in: its {@code x-microcks-session}
 * header or gRPC metadata, else the {@code microcks-session} entry of its W3C baggage, which OpenTelemetry propagates
 * across the hops of the application, asynchronous ones included. There is no fallback in the URL.
 * @author SebastienDegodez
 */
public final class ArazzoSessionCarrier {

   /** The header, or gRPC metadata key, naming the session. */
   public static final String SESSION_HEADER = "x-microcks-session";
   /** The entry of the W3C baggage naming the session. */
   public static final String BAGGAGE_ENTRY = "microcks-session";

   private ArazzoSessionCarrier() {
   }

   /**
    * @param sessionHeader  The value of the x-microcks-session header; null when there is none
    * @param baggageHeaders The values of the baggage headers, in their order
    * @return The id of the session the invocation names; empty when it names none
    */
   public static Optional<String> sessionId(String sessionHeader, List<String> baggageHeaders) {
      if (sessionHeader != null && !sessionHeader.isBlank()) {
         return Optional.of(sessionHeader.strip());
      }
      Baggage baggage = Baggage.fromContext(W3CBaggagePropagator.getInstance().extract(Context.root(),
            String.join(",", baggageHeaders), ArazzoTraceAttributes.BAGGAGE));
      return Optional.ofNullable(baggage.getEntryValue(BAGGAGE_ENTRY)).filter(sessionId -> !sessionId.isBlank());
   }
}
