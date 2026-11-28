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
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;

import java.util.List;
import java.util.Optional;

/**
 * The span and event attributes telling Live Traces that a mock invocation comes from an Arazzo workflow: the run, the
 * workflow, the step and the attempt it comes from, and how it reached the mock. A call the Arazzo minion makes to the
 * mocks names them in its W3C baggage: {@code microcks-arazzo-run}, {@code microcks-arazzo-workflow},
 * {@code microcks-arazzo-step} and {@code microcks-arazzo-attempt}.
 * @author SebastienDegodez
 */
public final class ArazzoTraceAttributes {

   /** The header of the W3C baggage. */
   public static final String BAGGAGE_HEADER = "baggage";
   /** How a call of an Arazzo run reaches the mocks. */
   public static final String VIA_RUN = "run";

   private static final String RUN = "microcks-arazzo-run";
   private static final String WORKFLOW = "microcks-arazzo-workflow";
   private static final String STEP = "microcks-arazzo-step";
   private static final String ATTEMPT = "microcks-arazzo-attempt";

   /** The carrier is the value of the baggage header. */
   static final TextMapGetter<String> BAGGAGE = new TextMapGetter<>() {
      @Override
      public Iterable<String> keys(String baggage) {
         return List.of(BAGGAGE_HEADER);
      }

      @Override
      public String get(String baggage, String key) {
         return baggage;
      }
   };

   private ArazzoTraceAttributes() {
   }

   /**
    * @param baggageHeaders The values of the baggage headers of a mock invocation, in their order
    * @return The attributes of the Arazzo run the baggage names; empty when it names none, as any other baggage, or a
    *         malformed one, does
    */
   public static Optional<Attributes> fromBaggage(List<String> baggageHeaders) {
      Baggage baggage = Baggage.fromContext(
            W3CBaggagePropagator.getInstance().extract(Context.root(), String.join(",", baggageHeaders), BAGGAGE));
      String runId = baggage.getEntryValue(RUN);
      if (isBlank(runId)) {
         return Optional.empty();
      }
      AttributesBuilder attributes = Attributes.builder().put(CommonAttributes.ARAZZO_RUN_ID, runId)
            .put(CommonAttributes.ARAZZO_VIA, VIA_RUN);
      putIfPresent(attributes, CommonAttributes.ARAZZO_WORKFLOW_ID, baggage.getEntryValue(WORKFLOW));
      putIfPresent(attributes, CommonAttributes.ARAZZO_STEP_ID, baggage.getEntryValue(STEP));
      attemptOf(baggage.getEntryValue(ATTEMPT))
            .ifPresent(attempt -> attributes.put(CommonAttributes.ARAZZO_ATTEMPT, attempt));
      return Optional.of(attributes.build());
   }

   private static void putIfPresent(AttributesBuilder attributes, AttributeKey<String> key, String value) {
      if (!isBlank(value)) {
         attributes.put(key, value);
      }
   }

   /** An attempt counts the calls of a step from 1: anything else is left out. */
   private static Optional<Long> attemptOf(String value) {
      try {
         long attempt = Long.parseLong(value);
         return attempt > 0 ? Optional.of(attempt) : Optional.empty();
      } catch (NumberFormatException notAnAttempt) {
         return Optional.empty();
      }
   }

   private static boolean isBlank(String value) {
      return value == null || value.isBlank();
   }
}
