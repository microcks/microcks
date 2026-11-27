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
package io.microcks.artifact.arazzo;

/**
 * The operation a step of an Arazzo workflow calls, or the channel of an AsyncAPI source it sends or receives on.
 * @param sourceName  The name of the source description the operation or the channel belongs to
 * @param operationId The id of the operation in that source description; null for a step on a channel
 * @param channel     The name of the channel in that AsyncAPI source description, as its channelPath points to it; null
 *                    for a step calling an operation
 * @author SebastienDegodez
 */
public record ArazzoOperationCall(String sourceName, String operationId, String channel) {

   /**
    * @param sourceName  The name of the source description the operation belongs to
    * @param operationId The id of the operation in that source description
    */
   public ArazzoOperationCall(String sourceName, String operationId) {
      this(sourceName, operationId, null);
   }
}
