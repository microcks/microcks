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
package io.microcks.domain.workflow;

import java.util.List;

/**
 * The example the Microcks mocks lack for a run against them to go on: the step that failed sent values for which the
 * operation it called has no example.
 * @param stepIndex     The index of the step that failed, in the execution order of the run
 * @param stepId        The id of that step in the workflow
 * @param serviceId     The id of the Microcks API whose mocks the step called
 * @param operationId   The operationId of the operation the step called, such as GetPastryByName
 * @param operationName The name of that operation in Microcks, such as GET /pastry/{name}
 * @param parameters    The parameters the mocks dispatch on, as the step sent them, with no example for these values
 * @author SebastienDegodez
 */
public record WorkflowMissingExample(int stepIndex, String stepId, String serviceId, String operationId,
      String operationName, List<WorkflowParameterValue> parameters) {
}
