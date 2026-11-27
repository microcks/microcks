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
package io.microcks.artifact.arazzo;

import java.util.List;

/**
 * What Microcks reads from an Arazzo document to identify it: the title and version of its info object, and its
 * workflows.
 * @param title       The title of the info object
 * @param version     The version of the info object
 * @param workflowIds The ids of the workflows, in the order of the document
 * @author SebastienDegodez
 */
public record ArazzoDescription(String title, String version, List<String> workflowIds) {
}
