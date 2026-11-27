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
 * The operation a step of an Arazzo workflow calls, or the AsyncAPI channel it sends or receives on, as kept with the
 * imported document.
 * @param sourceName  The name of the source description the operation or the channel belongs to
 * @param operationId The id of the operation in that source description; null for a step on a channel
 * @param channel     The name of the channel in that AsyncAPI source description; null for a step calling an operation,
 *                    and for the documents imported before Arazzo 1.1 was read
 * @author SebastienDegodez
 */
public record ArazzoStepCall(String sourceName, String operationId, String channel) {
}
