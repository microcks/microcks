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
package io.microcks.artifact.arazzo;

import java.util.List;
import java.util.Map;

/**
 * A workflow of an Arazzo document.
 * @param workflowId         The id of the workflow
 * @param summary            The summary of the workflow, null if it has none
 * @param description        The description of the workflow, null if it has none
 * @param inputNames         The names of the inputs the workflow declares, in the order of the document
 * @param steps              The steps of the workflow, in the order of the document
 * @param outputs            The outputs of the workflow, by name in the order of the document, with their runtime
 *                           expressions
 * @param inputTypes         The JSON schema type of each input the workflow declares, by name; null for an input
 *                           without type
 * @param requiredInputNames The names of the inputs the inputs schema of the workflow requires, in the order of the
 *                           document
 * @author SebastienDegodez
 */
public record ArazzoWorkflow(String workflowId, String summary, String description, List<String> inputNames,
      List<ArazzoStep> steps, Map<String, String> outputs, Map<String, String> inputTypes,
      List<String> requiredInputNames) {
}
