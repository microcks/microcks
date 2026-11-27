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
package io.microcks.minion.arazzo.client.dto;

import io.microcks.minion.arazzo.runner.StepResult;

import java.util.List;
import java.util.Map;

/**
 * A step of a run, as the minion reports it to Microcks when it ends.
 * @param index         The place of the step in the execution order of the run, from 0
 * @param stepId        The id of the step in the workflow
 * @param operation     The operation the step calls
 * @param status        PASSED, FAILED or NOT_RUN
 * @param statusCode    The status code of its response; null when none
 * @param elapsedTime   How long the step took, in milliseconds
 * @param criteria      Its success criteria, evaluated
 * @param outputs       Its outputs, by name
 * @param request       The request it sent; null when none
 * @param response      The response it received; null when none
 * @param message       Why it failed when no criterion tells it; null otherwise
 * @param parameters    The parameters it sent, resolved, with the runtime expression each came from
 * @param outputOrigins The runtime expression each of its outputs came from, by output name
 * @author SebastienDegodez
 */
public record StepReportDTO(int index, String stepId, StepOperationDTO operation, String status, Integer statusCode,
      long elapsedTime, List<CriterionDTO> criteria, Map<String, Object> outputs, RequestDTO request,
      ResponseDTO response, String message, List<ParameterDTO> parameters, Map<String, String> outputOrigins) {

   /**
    * @param step A step of a run, as it ended
    * @return Its report
    */
   public static StepReportDTO from(StepResult step) {
      return new StepReportDTO(step.index(), step.stepId(),
            new StepOperationDTO(step.operation().sourceName(), step.operation().operationId(),
                  step.operation().method(), step.operation().path()),
            step.status().name(), step.statusCode(), step.elapsedTime(),
            step.criteria().stream().map(criterion -> new CriterionDTO(criterion.condition(),
                  criterion.values().stream().map(value -> new ExpressionValueDTO(value.expression(), value.value()))
                        .toList(),
                  criterion.held())).toList(),
            step.outputs(),
            step.request() == null ? null
                  : new RequestDTO(step.request().method(), step.request().uri().toString(), step.request().headers(),
                        step.request().body()),
            step.response() == null ? null
                  : new ResponseDTO(step.response().status(), step.response().headers(), step.response().body()),
            step.message(), step.parameters().stream().map(parameter -> new ParameterDTO(parameter.name(),
                  parameter.in(), parameter.value(), parameter.expression())).toList(),
            step.outputOrigins());
   }
}
