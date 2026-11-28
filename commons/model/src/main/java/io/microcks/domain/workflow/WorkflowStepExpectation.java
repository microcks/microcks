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

import java.util.Map;

/**
 * What a recorded example expects of a step of its workflow.
 * @param stepId  The id of the step
 * @param status  The exact HTTP status code the step is expected to get; null when it is not checked
 * @param outputs The outputs the step is expected to produce, by name; only those listed are checked
 * @author SebastienDegodez
 */
public record WorkflowStepExpectation(String stepId, Integer status, Map<String, Object> outputs) {
}
