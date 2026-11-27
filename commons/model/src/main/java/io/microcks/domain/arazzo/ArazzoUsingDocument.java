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
 * An Arazzo document with workflows using an API, as the page of this API shows it.
 * @param id        The id of the document
 * @param title     The title of the document
 * @param version   The version of the document
 * @param workflows The workflows of the document using the API, in the order of the document
 * @author SebastienDegodez
 */
public record ArazzoUsingDocument(String id, String title, String version, List<ArazzoUsingWorkflow> workflows) {
}
