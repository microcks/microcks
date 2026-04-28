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
package io.github.microcks.minion.async.handler;

import io.github.microcks.domain.EventMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchTest {

   @Test
   void matchesEventMessage_WithJsonSubsetAndExtraIncomingFields() {
      EventMessage eventMessage = new EventMessage();
      eventMessage.setContent("""
            {
              "streetlightId": "ea89",
              "state": "off",
              "metadata": {
                "source": "sensor"
              }
            }
            """);

      String eventPayload = """
            {
              "streetlightId": "ea89",
              "state": "off",
              "metadata": {
                "source": "sensor",
                "zone": "north"
              },
              "timestamp": "2026-03-03T10:15:30Z"
            }
            """;

      assertTrue(EventData.matchesExpectedMessage(eventMessage, eventPayload));
   }

   @Test
   void matchesEventMessage_FailsWhenExpectedJsonFieldIsMissing() {
      EventMessage eventMessage = new EventMessage();
      eventMessage.setContent("""
            {
              "streetlightId": "ea89",
              "state": "off"
            }
            """);

      String eventPayload = """
            {
              "streetlightId": "ea89"
            }
            """;

      assertFalse(EventData.matchesExpectedMessage(eventMessage, eventPayload));
   }
}
