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
 * The operation a step of an Arazzo workflow calls, as kept with the imported document.
 * @param sourceName  The name of the source description the operation belongs to
 * @param operationId The id of the operation in that source description
 * @author SebastienDegodez
 */
public record ArazzoStepCall(String sourceName, String operationId) {
}
