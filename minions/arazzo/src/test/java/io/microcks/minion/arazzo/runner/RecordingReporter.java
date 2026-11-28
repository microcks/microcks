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

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps what a run reports, in the order it is reported.
 * @author SebastienDegodez
 */
final class RecordingReporter implements RunReporter {

   private final List<StepResult> steps = new ArrayList<>();
   private final List<RunOutcome> outcomes = new ArrayList<>();

   @Override
   public void stepEnded(StepResult step) {
      steps.add(step);
   }

   @Override
   public void runEnded(RunOutcome outcome) {
      outcomes.add(outcome);
   }

   List<StepResult> steps() {
      return steps;
   }

   /** The one outcome of the run. */
   RunOutcome outcome() {
      if (outcomes.size() != 1) {
         throw new AssertionError("Expected one outcome, got " + outcomes);
      }
      return outcomes.get(0);
   }

   StepResult step(String stepId) {
      return steps.stream().filter(step -> step.stepId().equals(stepId)).findFirst().orElseThrow();
   }
}
