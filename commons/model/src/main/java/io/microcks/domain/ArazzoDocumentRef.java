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

import java.util.List;

/**
 * The Arazzo document an import job imported, or whose ArazzoSources overlay it imported, as the Importers page shows
 * it.
 * @param documentId      The identifier of the document; null when the job imported the overlay of a document not
 *                        imported yet
 * @param title           The title of the document
 * @param version         The version of the document
 * @param unlinkedSources The source descriptions of the document linked to no API of Microcks after the import, in the
 *                        order of the document
 * @author SebastienDegodez
 */
public record ArazzoDocumentRef(String documentId, String title, String version, List<String> unlinkedSources) {
}
