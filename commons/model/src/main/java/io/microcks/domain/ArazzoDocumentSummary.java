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

/**
 * What the Workflows page shows of an imported Arazzo document: what identifies it, the APIs its source descriptions
 * are linked to, and its workflows.
 * @param id            The id of the imported document
 * @param title         The title of the info object of the document
 * @param version       The version of the info object of the document
 * @param arazzoVersion The version of the Arazzo specification the document uses
 * @param fileName      The name of the file the document was uploaded from; null when it is not known
 * @param importDate    When the document was last imported
 * @param sources       The source descriptions of the document and their APIs, in the order of the document
 * @param workflows     The workflows of the document, in the order of the document
 * @author SebastienDegodez
 */
public record ArazzoDocumentSummary(String id, String title, String version, String arazzoVersion, String fileName,
      Date importDate, List<ArazzoSourceSummary> sources, List<ArazzoWorkflowSummary> workflows) {
}
