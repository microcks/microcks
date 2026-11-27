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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of how a mock invocation names the Arazzo verification session it belongs to: its x-microcks-session header,
 * else the microcks-session entry of its W3C baggage, which OpenTelemetry propagates across the hops of the
 * application.
 * @author SebastienDegodez
 */
class ArazzoSessionCarrierTest {

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = {
         // The header names the session, and wins over the baggage.
         "s-1 | null | s-1", "s-1 | microcks-session=s-2 | s-1",
         // The baggage names it, among other entries, with properties or percent-encoded.
         "null | microcks-session=s-2 | s-2", "null | tenant=bakery , microcks-session = s-2;origin=saga | s-2",
         "null | microcks-session=session%201 | session 1", "'  ' | microcks-session=s-2 | s-2",
         "null | tenant=bakery#microcks-session=s-3 | s-3",
         // Nothing names a session: no header, an unrelated, empty or malformed baggage.
         "null | null | null", "'' | tenant=bakery | null", "null | microcks-session= | null", "null | '=,;;=' | null",
         "null | microcks-session | null" })
   void theSessionIsReadFromTheHeaderElseFromTheBaggage(String header, String baggage, String sessionId) {
      List<String> baggageHeaders = baggage == null ? List.of() : Arrays.asList(baggage.split("#"));

      assertEquals(Optional.ofNullable(sessionId), ArazzoSessionCarrier.sessionId(header, baggageHeaders));
   }
}
