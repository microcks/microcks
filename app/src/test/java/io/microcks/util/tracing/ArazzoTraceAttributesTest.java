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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The baggage header is read as the carrier of the W3C baggage propagator: its value is that of its only key.
 * @author SebastienDegodez
 */
class ArazzoTraceAttributesTest {

   @Test
   void theBaggageHeaderIsTheOnlyKeyOfItsCarrier() {
      assertEquals(List.of("baggage"), ArazzoTraceAttributes.BAGGAGE.keys("tenant=bakery"));
      assertEquals("tenant=bakery", ArazzoTraceAttributes.BAGGAGE.get("tenant=bakery", "baggage"));
   }
}
