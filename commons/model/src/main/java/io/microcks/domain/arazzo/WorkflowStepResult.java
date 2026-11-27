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
package io.microcks.domain;

import java.util.List;
import java.util.Map;

/**
 * A step of a run. Its index is its place in the execution order of the run, given by the minion when it starts the
 * step: a step called twice has two indexes, a step not run after a failure keeps its own.
 * @param index         The place of the step in the execution order, from 0
 * @param stepId        The id of the step in the workflow
 * @param operation     The operation the step called
 * @param status        How the step ended
 * @param statusCode    The status code of its response; null when it got none
 * @param elapsedTime   How long the step took, in milliseconds
 * @param criteria      Its success criteria, as evaluated
 * @param outputs       Its outputs, by name
 * @param testCaseId    The id the request and response of the step are stored under; null when it sent none
 * @param message       Why the step failed when no criterion tells it; null otherwise
 * @param requestUrl    The URL the step sent its request to; null when it sent none
 * @param parameters    The parameters it sent, resolved, with the runtime expression each came from
 * @param outputOrigins The runtime expression each of its outputs came from, by output name
 * @author SebastienDegodez
 */
public record WorkflowStepResult(int index, String stepId, WorkflowStepOperation operation, WorkflowStepStatus status,
      Integer statusCode, long elapsedTime, List<WorkflowCriterionResult> criteria, Map<String, Object> outputs,
      String testCaseId, String message, String requestUrl, List<WorkflowParameterValue> parameters,
      Map<String, String> outputOrigins) {
}
