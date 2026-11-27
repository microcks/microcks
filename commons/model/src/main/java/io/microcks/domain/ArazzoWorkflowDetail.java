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
package io.microcks.domain;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * What the API developer reads of a workflow of an Arazzo document before running it: its steps, the operations they
 * call, and how data flows between them.
 * @param documentId    The id of the Arazzo document the workflow belongs to
 * @param title         The title of the Arazzo document
 * @param version       The version of the Arazzo document
 * @param arazzoVersion The version of Arazzo the document uses
 * @param fileName      The name of the file the document was imported from, null if unknown
 * @param importDate    When the document was last imported
 * @param workflowIds   The ids of all the workflows of the document, in the order of the document
 * @param sources       Each source description of the document, in the order of the document
 * @param workflowId    The id of the workflow
 * @param summary       The summary of the workflow, null if it has none
 * @param description   The description of the workflow, null if it has none
 * @param inputs        The inputs of the workflow, with where each one is used
 * @param steps         The steps of the workflow, in the order of the document
 * @param outputs       The outputs of the workflow, by name, with their runtime expressions
 * @param dataPassings  The step outputs that later steps use, in the order of the steps that use them
 * @author SebastienDegodez
 */
public record ArazzoWorkflowDetail(String documentId, String title, String version, String arazzoVersion,
      String fileName, Date importDate, List<String> workflowIds, List<ArazzoSourceDescription> sources,
      String workflowId, String summary, String description, List<ArazzoWorkflowInput> inputs,
      List<ArazzoStepDetail> steps, Map<String, String> outputs, List<ArazzoDataPassing> dataPassings) {
}
