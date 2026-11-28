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
      List<String> workflowIds, List<ArazzoSourceAnalysis> sources, List<ArazzoWorkflowCheck> checks,
      List<ServiceRef> apis, List<String> warnings) {

   /**
    * @param refusal Why the document cannot be imported
    * @param apis    The APIs of Microcks a source can be linked to
    * @return The analysis of a refused document
    */
   public static ArazzoImportAnalysis refused(String refusal, List<ServiceRef> apis) {
      return new ArazzoImportAnalysis(refusal, null, null, null, List.of(), List.of(), List.of(), apis, List.of());
   }
}
