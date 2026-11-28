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
package io.microcks.service.arazzo;

import java.util.Map;

/**
 * What the Mock URL of a workflow answers: the outputs of the workflow once played on the mocks, or why it failed.
 * @param status     The HTTP status of the answer: 200 with the outputs, else the status telling the failure
 * @param outputs    The outputs of the workflow, by name, when it passed; null otherwise
 * @param runId      The id of the run that played the workflow; null when no run was created
 * @param failedStep The id of the step the run failed at; null when no step failed
 * @param reason     Why the workflow did not pass; null when it passed
 * @author SebastienDegodez
 */
public record ArazzoMockUrlAnswer(int status, Map<String, Object> outputs, String runId, String failedStep,
      String reason) {
}
