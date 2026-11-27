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
package io.microcks.web.arazzo;

import io.microcks.domain.ServiceRef;

import java.util.Map;

/**
 * The links the developer chose for the source descriptions of an imported Arazzo document.
 * @param links The API each source description is linked to, by name, a source absent or mapped to null being not
 *              linked; null to analyse the document with the links of its overlay
 * @author SebastienDegodez
 */
public record ArazzoLinksRequest(Map<String, ServiceRef> links) {
}
