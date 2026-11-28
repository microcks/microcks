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
package io.microcks.service.arazzo.view;

import io.microcks.domain.workflow.WorkflowExampleVerdict;
import io.microcks.domain.workflow.WorkflowRunOrigin;
import io.microcks.domain.workflow.WorkflowRunStatus;
import io.microcks.domain.workflow.WorkflowRunTarget;
import java.util.Date;

/**
 * What the runs history shows of a run of a workflow: its number in its document, what it ran against, and how it
 * ended.
 * @param id             The id of the run
 * @param number         The number of the run in its Arazzo document, from 1; null for a run launched before runs were
 *                       numbered
 * @param workflowId     The id of the workflow that ran
 * @param target         What the steps of the run called
 * @param status         How the run ended, or that it is in progress
 * @param failedStepId   The id of the step the run failed at; null when no step failed
 * @param startedDate    When the run started
 * @param elapsedTime    How long the run took, in milliseconds
 * @param example        The name of the recorded example the run played; null when it played none
 * @param exampleVerdict Whether the run matches its example; null when it played none or is in progress
 * @param origin         Where the run was launched from; null when it was launched from the Microcks UI or API
 * @author SebastienDegodez
 */
public record WorkflowRunSummary(String id, Long number, String workflowId, WorkflowRunTarget target,
      WorkflowRunStatus status, String failedStepId, Date startedDate, long elapsedTime, String example,
      WorkflowExampleVerdict exampleVerdict, WorkflowRunOrigin origin) {
}
