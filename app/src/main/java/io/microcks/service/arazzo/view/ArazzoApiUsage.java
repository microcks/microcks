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
package io.microcks.service.arazzo.view;

import java.util.List;
import java.util.Map;

/**
 * What the page of an API shows of the Arazzo workflows that use it.
 * @param documents               The Arazzo documents with a workflow using the API, by title then version; empty when
 *                                no workflow uses it
 * @param operationWorkflowCounts The number of workflows calling each operation of the API, by operation name; an
 *                                operation no workflow calls is absent
 * @author SebastienDegodez
 */
public record ArazzoApiUsage(List<Document> documents, Map<String, Integer> operationWorkflowCounts) {

   /**
    * An Arazzo document with workflows using an API, as the page of this API shows it.
    * @param id        The id of the document
    * @param title     The title of the document
    * @param version   The version of the document
    * @param workflows The workflows of the document using the API, in the order of the document
    * @author SebastienDegodez
    */
   public record Document(String id, String title, String version, List<Workflow> workflows) {
   }

   /**
    * A workflow using an API, as the page of this API shows it.
    * @param workflowId The id of the workflow in its Arazzo document
    * @param lastResult How the last run of the workflow went
    * @author SebastienDegodez
    */
   public record Workflow(String workflowId, ArazzoLastResult lastResult) {
   }
}
