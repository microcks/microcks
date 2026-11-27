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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * A clock that only moves when told to, so that the timing of steps and runs is known.
 * @author SebastienDegodez
 */
final class ManualClock extends Clock {

   private Instant now = Instant.parse("2026-10-21T10:00:00Z");

   void advance(long millis) {
      now = now.plusMillis(millis);
   }

   @Override
   public ZoneId getZone() {
      return ZoneOffset.UTC;
   }

   @Override
   public Clock withZone(ZoneId zone) {
      return this;
   }

   @Override
   public Instant instant() {
      return now;
   }
}
