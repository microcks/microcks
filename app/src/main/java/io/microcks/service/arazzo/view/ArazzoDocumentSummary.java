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

import io.microcks.domain.ServiceType;
import io.microcks.domain.workflow.WorkflowFormat;
import java.util.Date;
import java.util.List;

/**
 * What the Workflows page shows of an imported Arazzo document: what identifies it, the APIs its source descriptions
 * are linked to, and its workflows.
 * @param id            The id of the imported document
 * @param title         The title of the info object of the document
 * @param version       The version of the info object of the document
 * @param format        The format of the document: the Arazzo specification it follows
 * @param formatVersion The version of that format the document uses
 * @param fileName      The name of the file the document was uploaded from; null when it is not known
 * @param importDate    When the document was last imported
 * @param sources       The source descriptions of the document and their APIs, in the order of the document
 * @param workflows     The workflows of the document, in the order of the document
 * @author SebastienDegodez
 */
public record ArazzoDocumentSummary(String id, String title, String version, WorkflowFormat format,
      String formatVersion, String fileName, Date importDate, List<Source> sources, List<Workflow> workflows) {

   /**
    * What the Workflows page shows of a workflow of an Arazzo document.
    * @param status      Whether the workflow can run against the APIs of Microcks, and why not
    * @param description The summary of the workflow, or else its description; null when it has none
    * @param stepCount   The number of steps of the workflow
    * @param lastResult  How the last run of the workflow went
    * @param lastRun     The last run of the workflow; null when it never ran
    * @author SebastienDegodez
    */
   public record Workflow(ArazzoWorkflowStatus status, String description, int stepCount,
         ArazzoLastResult lastResult, WorkflowRunSummary lastRun) {
   }

   /**
    * A source description of an Arazzo document, with the Microcks API it is linked to.
    * @param link    The link of the source description to a Microcks API
    * @param apiId   The id of the linked API; null when the source is not linked
    * @param apiType The type of the linked API; null when the source is not linked
    * @author SebastienDegodez
    */
   public record Source(ArazzoSourceLink link, String apiId, ServiceType apiType) {
   }
}
