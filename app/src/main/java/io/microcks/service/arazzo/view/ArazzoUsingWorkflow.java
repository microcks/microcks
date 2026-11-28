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

/**
 * A workflow using an API, as the page of this API shows it.
 * @param workflowId The id of the workflow in its Arazzo document
 * @param lastResult How the last run of the workflow went
 * @author SebastienDegodez
 */
public record ArazzoUsingWorkflow(String workflowId, ArazzoLastResult lastResult) {
}
