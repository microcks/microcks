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

/**
 * How the runner waits before retrying a step.
 * @author SebastienDegodez
 */
@FunctionalInterface
public interface Pause {

   /**
    * Sleeps the running thread. An interrupted sleep ends at once and leaves the thread interrupted, so that whoever
    * owns it knows.
    */
   Pause SLEEP = duration -> {
      try {
         Thread.sleep(duration);
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
      }
   };

   /**
    * @param duration How long to wait, not negative
    */
   void pause(Duration duration);
}
