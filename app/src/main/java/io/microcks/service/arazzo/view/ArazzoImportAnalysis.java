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

import io.microcks.domain.ServiceRef;
import java.util.List;

/**
 * What an Arazzo document would become if it were imported with the links of its sources. Nothing is stored.
 * @param refusal       Why the document cannot be imported; null when it can
 * @param arazzoVersion The version of Arazzo the document uses; null when it is refused
 * @param title         The title of the document; null when it is refused
 * @param version       The version of the document; null when it is refused
 * @param workflowIds   The ids of its workflows, in the order of the document; empty when it is refused
 * @param sources       Its source descriptions with their links, in the order of the document; empty when it is refused
 * @param checks        Whether each of its workflows is ready to run, in the order of the document; empty when it is
 *                      refused
 * @param apis          The APIs of Microcks a source can be linked to, by name then version
 * @param warnings      What the document does that Arazzo allows but does not recommend, imported anyway; empty when
 *                      there is nothing to say or when it is refused
 * @author SebastienDegodez
 */
public record ArazzoImportAnalysis(String refusal, String arazzoVersion, String title, String version,
      List<String> workflowIds, List<Source> sources, List<WorkflowCheck> checks,
      List<ServiceRef> apis, List<String> warnings) {

   /**
    * @param refusal Why the document cannot be imported
    * @param apis    The APIs of Microcks a source can be linked to
    * @return The analysis of a refused document
    */
   public static ArazzoImportAnalysis refused(String refusal, List<ServiceRef> apis) {
      return new ArazzoImportAnalysis(refusal, null, null, null, List.of(), List.of(), List.of(), apis, List.of());
   }

   /**
    * A source description of an Arazzo document about to be imported, with the Microcks API it would be linked to.
    * @param sourceName          The name of the source description
    * @param declaredUrl         The url the source description declares for its contract
    * @param match               What the declared contract says of the API to link the source to
    * @param link                The link of the source: the one chosen, or else the one matched
    * @param operationCount      How many distinct operations the steps call on this source
    * @param foundOperationCount How many of these operations the linked API defines
    * @param unresolvedStepCount How many steps call an operation of this source that is not found
    * @author SebastienDegodez
    */
   public record Source(String sourceName, String declaredUrl, Match match,
         ArazzoSourceLink link, int operationCount, int foundOperationCount, int unresolvedStepCount) {
   }

   /**
    * What the contract declared by a source description of an Arazzo document says of the Microcks API to link it to:
    * the title and version of the contract are the name and version of that API.
    * @param status          Whether the contract could be read and whether Microcks has an API with its title and
    *                        version
    * @param contractTitle   The title of the contract; null when it cannot be read
    * @param contractVersion The version of the contract; null when it cannot be read
    * @author SebastienDegodez
    */
   public record Match(Status status, String contractTitle, String contractVersion) {

      /** Whether a source description is matched to a Microcks API. */
      public enum Status {
         /** Microcks has an API with the title and version of the declared contract. */
         MATCHED,
         /** Microcks has no API with the title and version of the declared contract. */
         NO_API,
         /** The declared contract cannot be read, or gives no title and version: the API has to be picked. */
         UNREADABLE
      }
   }

   /**
    * Whether a workflow of an Arazzo document about to be imported is ready to run with the links of its sources.
    * @param workflowId      The id of the workflow
    * @param runnable        Whether each step calls an operation found in a linked API
    * @param apiNames        The names of the linked APIs its steps call, in the order of the steps
    * @param unlinkedSources The source descriptions its steps call that are not linked, in the order of the steps
    * @param reasons         Why a step calling a linked API is not resolved, in the order of the steps
    * @author SebastienDegodez
    */
   public record WorkflowCheck(String workflowId, boolean runnable, List<String> apiNames,
         List<String> unlinkedSources, List<String> reasons) {
   }
}
