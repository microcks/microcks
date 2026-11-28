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
 * The operation a step of an Arazzo workflow calls, as found in the main OpenAPI or AsyncAPI contract of the linked
 * Microcks API, or the AsyncAPI channel it sends or receives on.
 * @param sourceName  The name of the source description the operation belongs to
 * @param operationId The id of the operation in that source description; null for a step on a channel
 * @param method      The HTTP method of the operation, or the action Microcks names an AsyncAPI operation with (SEND,
 *                    RECEIVE, PUBLISH or SUBSCRIBE); null when it is not found, or for a step on a channel
 * @param path        The path of the operation, or what Microcks names an AsyncAPI operation with after its action;
 *                    null when it is not found, or for a step on a channel
 * @param reason      Why the operation or the channel is not found, null when it is found
 * @param channel     The address of the AsyncAPI channel the step sends or receives on, else the name the step gives it
 *                    when it is not found; null for an HTTP operation
 * @author SebastienDegodez
 */
public record ArazzoStepOperation(String sourceName, String operationId, String method, String path, String reason,
      String channel) {
}
