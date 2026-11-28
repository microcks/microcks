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
import java.util.Map;

/**
 * The recorded example a run plays: its name, what it expects, and, once the run ended, whether the run matches it.
 * @param name              The name of the example
 * @param outputs           The outputs the workflow is expected to produce, by name
 * @param steps             What each listed step is expected to show
 * @param stopsAt           The step the workflow is expected to stop at; null when it is expected to run to its end
 * @param verdict           Whether the run matches the example; null while the run is in progress
 * @param unmetExpectations Each expectation the run does not meet
 * @author SebastienDegodez
 */
public record WorkflowExampleCheck(String name, Map<String, Object> outputs, List<WorkflowStepExpectation> steps,
      String stopsAt, WorkflowExampleVerdict verdict, List<String> unmetExpectations) {
}
