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
package io.microcks.domain.arazzo;

/**
 * Where a run of a workflow was launched from, when it was not launched from the Microcks UI or API.
 * @author SebastienDegodez
 */
public enum WorkflowRunOrigin {
   /** An application posted the inputs of the workflow to its Mock URL, to get its outputs played on the mocks. */
   MOCK_URL,
   /**
    * Not a run: the verification of the calls an application made to the mocks in a session, against the steps of the
    * workflow.
    */
   SESSION
}
