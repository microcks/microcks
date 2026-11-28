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
package io.microcks.minion.arazzo.client;

import io.microcks.minion.arazzo.client.dto.RunOutcomeDTO;
import io.microcks.minion.arazzo.client.dto.StepReportDTO;
import io.microcks.minion.arazzo.runner.RunOutcome;
import io.microcks.minion.arazzo.runner.RunReporter;
import io.microcks.minion.arazzo.runner.StepResult;

/**
 * Reports the steps and the end of a run to Microcks.
 * @author SebastienDegodez
 */
public class MicrocksRunReporter implements RunReporter {

   private final MicrocksAPIConnector connector;
   private final String runId;

   /**
    * @param connector The client of the Microcks APIs
    * @param runId     The id of the run in Microcks
    */
   public MicrocksRunReporter(MicrocksAPIConnector connector, String runId) {
      this.connector = connector;
      this.runId = runId;
   }

   @Override
   public void stepEnded(StepResult step) {
      connector.reportStep(runId, StepReportDTO.from(step));
   }

   @Override
   public void runEnded(RunOutcome outcome) {
      connector.reportOutcome(runId, RunOutcomeDTO.from(outcome));
   }
}
