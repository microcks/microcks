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

/**
 * What the Workflows page shows of a workflow of an Arazzo document.
 * @param status      Whether the workflow can run against the APIs of Microcks, and why not
 * @param description The summary of the workflow, or else its description; null when it has none
 * @param stepCount   The number of steps of the workflow
 * @param lastResult  How the last run of the workflow went
 * @author SebastienDegodez
 */
public record ArazzoWorkflowSummary(ArazzoWorkflowStatus status, String description, int stepCount,
      ArazzoLastResult lastResult) {
}
