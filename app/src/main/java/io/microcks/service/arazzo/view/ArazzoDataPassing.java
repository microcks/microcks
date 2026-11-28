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
 * An output of a step of an Arazzo workflow that a later step uses.
 * @param fromStepId The id of the step giving the output
 * @param outputName The name of the output
 * @param to         Where the later step uses it
 * @author SebastienDegodez
 */
public record ArazzoDataPassing(String fromStepId, String outputName, ArazzoExpressionUse to) {
}
