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
 * A source description of an Arazzo document about to be imported, with the Microcks API it would be linked to.
 * @param sourceName          The name of the source description
 * @param declaredUrl         The url the source description declares for its contract
 * @param match               What the declared contract says of the API to link the source to
 * @param link                The link of the source: the one chosen, or else the one matched
 * @param operationCount      How many distinct operations the steps call on this source
 * @param foundOperationCount How many of these operations the linked API defines
 * @param unresolvedStepCount How many steps call an operation of this source that is not found
 * @author SebastienDegodez
 */
public record ArazzoSourceAnalysis(String sourceName, String declaredUrl, ArazzoSourceMatch match,
      ArazzoSourceLink link, int operationCount, int foundOperationCount, int unresolvedStepCount) {
}
