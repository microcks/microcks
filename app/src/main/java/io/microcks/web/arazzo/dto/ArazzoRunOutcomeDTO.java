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
package io.microcks.web.dto;

import io.microcks.domain.WorkflowRunStatus;

import java.util.Map;

/**
 * The end of a run, as the Arazzo minion reports it.
 * @param status      SUCCESS or FAILURE
 * @param outputs     The outputs of the workflow, by name
 * @param elapsedTime How long the run took, in milliseconds
 * @param message     Why the run failed when no step tells it; null otherwise
 * @author SebastienDegodez
 */
public record ArazzoRunOutcomeDTO(String status, Map<String, Object> outputs, long elapsedTime, String message) {

   /** @return How the run ended */
   public WorkflowRunStatus runStatus() {
      return WorkflowRunStatus.valueOf(status);
   }
}
