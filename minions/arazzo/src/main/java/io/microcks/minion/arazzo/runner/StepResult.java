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

import java.util.List;
import java.util.Map;

/**
 * A step of a run, as executed. Its index is its place in the execution order of the run.
 * @param index         The place of the step in the execution order, from 0
 * @param stepId        The id of the step in the workflow
 * @param operation     The operation the step calls
 * @param status        How the step ended
 * @param statusCode    The status code of its response; null when it got none
 * @param elapsedTime   How long the step took, in milliseconds
 * @param criteria      Its success criteria, evaluated
 * @param outputs       Its outputs, by name; empty when it did not pass
 * @param request       The request it sent; null when it sent none
 * @param response      The response it received; null when it received none
 * @param message       Why the step failed when no criterion tells it; null otherwise
 * @param parameters    The parameters it sent, resolved, in the order of the step; empty when it sent none
 * @param outputOrigins The runtime expression each of its outputs came from, by output name
 * @author SebastienDegodez
 */
public record StepResult(int index, String stepId, StepOperation operation, StepStatus status, Integer statusCode,
      long elapsedTime, List<CriterionResult> criteria, Map<String, Object> outputs, HttpCall request,
      HttpOutcome response, String message, List<ParameterValue> parameters, Map<String, String> outputOrigins) {
}
