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
 * Where a step of an Arazzo workflow uses a runtime expression.
 * @param stepId The id of the step
 * @param place  Where the step uses it: path, query or header for a parameter, body for a request body replacement,
 *               criterion for a success criterion
 * @param target The name of the parameter, or the pointer replaced in the body; null for a criterion
 * @author SebastienDegodez
 */
public record ArazzoExpressionUse(String stepId, String place, String target) {
}
