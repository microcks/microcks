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
package io.microcks.domain.workflow;

/**
 * The operation a workflow step calls: as the document declares it, as found in the contract of the linked Microcks
 * API, or as a run called it. For an AsyncAPI step, the channel it sends or receives on.
 * @param sourceName  The name of the source description the operation belongs to
 * @param operationId The id of the operation in that source description; null for a step on a channel
 * @param method      The HTTP method of the operation, or the action Microcks names an AsyncAPI operation with (SEND,
 *                    RECEIVE, PUBLISH or SUBSCRIBE); null when not resolved yet or not found, or for a step on
 *                    a channel
 * @param path        The path template of the operation, or what Microcks names an AsyncAPI operation with after its
 *                    action; null when not resolved yet or not found, or for a step on a channel
 * @param reason      Why the operation or the channel is not found; null when it is found or not resolved yet
 * @param channel     The address of the AsyncAPI channel the step sends or receives on, else the name the step gives
 *                    it; null for an HTTP operation
 * @author SebastienDegodez
 */
public record WorkflowStepOperation(String sourceName, String operationId, String method, String path, String reason,
      String channel) {

   /** The operation a run called, found by method and path. */
   public WorkflowStepOperation(String sourceName, String operationId, String method, String path) {
      this(sourceName, operationId, method, path, null, null);
   }

   /**
    * The operation as the document declares it, before it is looked up in the contract of the linked API.
    * @param sourceName  The name of the source description
    * @param operationId The id of the operation; null for a step on a channel
    * @param channel     The name of the channel; null for an HTTP operation
    * @return The declared operation
    */
   public static WorkflowStepOperation declared(String sourceName, String operationId, String channel) {
      return new WorkflowStepOperation(sourceName, operationId, null, null, null, channel);
   }
}
