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
 * A recorded example of an Arazzo workflow, as its ArazzoSources overlay gives it: the inputs to run it with and what
 * the run is expected to show, as API examples record a request and its expected response.
 * @param workflowId The id of the workflow the example plays
 * @param name       The name of the example in that workflow
 * @param inputs     The inputs to run the workflow with, by name
 * @param outputs    The outputs the workflow is expected to produce, by name; only those listed are checked
 * @param steps      What each listed step is expected to show, in the order of the overlay
 * @param stopsAt    The step the workflow is expected to stop at; null when it is expected to run to its end
 * @author SebastienDegodez
 */
public record ArazzoWorkflowExample(String workflowId, String name, Map<String, Object> inputs,
      Map<String, Object> outputs, List<ArazzoStepExpectation> steps, String stopsAt) {
}
