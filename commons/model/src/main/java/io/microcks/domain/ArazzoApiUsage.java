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
import java.util.Map;

/**
 * What the page of an API shows of the Arazzo workflows that use it.
 * @param documents               The Arazzo documents with a workflow using the API, by title then version; empty when
 *                                no workflow uses it
 * @param operationWorkflowCounts The number of workflows calling each operation of the API, by operation name; an
 *                                operation no workflow calls is absent
 * @author SebastienDegodez
 */
public record ArazzoApiUsage(List<ArazzoUsingDocument> documents, Map<String, Integer> operationWorkflowCounts) {
}
