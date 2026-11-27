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
package io.microcks.web;

import io.microcks.domain.ServiceRef;

import java.util.Map;

/**
 * An Arazzo document dropped in the import dialog, with the links the developer chose for its source descriptions.
 * @param content  The document, in YAML or JSON
 * @param fileName The name of the file the document was dropped from; null when it is not known
 * @param links    The API each source description is linked to, by name, a source absent or mapped to null being not
 *                 linked; null to analyse the document with the APIs its sources are matched to
 * @author SebastienDegodez
 */
public record ArazzoImportRequest(String content, String fileName, Map<String, ServiceRef> links) {
}
