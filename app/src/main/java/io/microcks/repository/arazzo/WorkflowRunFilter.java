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
package io.microcks.repository.arazzo;

import io.microcks.domain.arazzo.WorkflowRunStatus;
import io.microcks.domain.arazzo.WorkflowRunTarget;

/**
 * The filters of a runs history; a null filter keeps every run.
 * @param workflowId The id of the workflow whose runs are kept
 * @param target     What the kept runs called
 * @param result     How the kept runs ended, or that they are in progress
 * @author SebastienDegodez
 */
public record WorkflowRunFilter(String workflowId, WorkflowRunTarget target, WorkflowRunStatus result) {

   /** The filter keeping every run. */
   public static final WorkflowRunFilter ALL = new WorkflowRunFilter(null, null, null);
}
