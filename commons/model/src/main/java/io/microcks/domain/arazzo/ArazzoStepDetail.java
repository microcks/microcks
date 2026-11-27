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
package io.microcks.domain.arazzo;

import java.util.List;
import java.util.Map;

/**
 * A step of an Arazzo workflow, as the API developer reads it.
 * @param stepId          The id of the step
 * @param description     The description of the step, null if it has none
 * @param operation       The operation the step calls
 * @param parameters      The parameters the step sends, in the order of the document
 * @param requestBody     The request body the step sends, null if it sends none
 * @param successCriteria The criteria the response must meet, in the order of the document
 * @param outputs         The outputs of the step, by name, with their runtime expressions
 * @param onSuccess       What the step does when it succeeds, in the order of the document; empty to go on
 * @param onFailure       What the step does when it fails, in the order of the document; empty to end the workflow
 * @author SebastienDegodez
 */
public record ArazzoStepDetail(String stepId, String description, ArazzoStepOperation operation,
      List<ArazzoStepParameter> parameters, ArazzoStepRequestBody requestBody,
      List<ArazzoStepCriterion> successCriteria, Map<String, String> outputs, List<ArazzoStepAction> onSuccess,
      List<ArazzoStepAction> onFailure) {
}
