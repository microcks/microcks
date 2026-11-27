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
 * Whether a workflow of an Arazzo document about to be imported is ready to run with the links of its sources.
 * @param workflowId      The id of the workflow
 * @param runnable        Whether each step calls an operation found in a linked API
 * @param apiNames        The names of the linked APIs its steps call, in the order of the steps
 * @param unlinkedSources The source descriptions its steps call that are not linked, in the order of the steps
 * @param reasons         Why a step calling a linked API is not resolved, in the order of the steps
 * @author SebastienDegodez
 */
public record ArazzoWorkflowCheck(String workflowId, boolean runnable, List<String> apiNames,
      List<String> unlinkedSources, List<String> reasons) {
}
