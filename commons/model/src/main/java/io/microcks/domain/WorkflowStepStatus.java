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
 * How a step of a run ended.
 * @author SebastienDegodez
 */
public enum WorkflowStepStatus {
   /** The step got a response meeting all its success criteria. */
   PASSED,
   /** The step got no response, or one that does not meet a success criterion. */
   FAILED,
   /** The step was not executed, because an earlier step failed. */
   NOT_RUN
}
