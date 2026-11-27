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
 * The operation a step of an Arazzo workflow calls, as found in the main OpenAPI contract of the linked Microcks API.
 * @param sourceName  The name of the source description the operation belongs to
 * @param operationId The id of the operation in that source description
 * @param method      The HTTP method of the operation, null when it is not found
 * @param path        The path of the operation, null when it is not found
 * @param reason      Why the operation is not found, null when it is found
 * @author SebastienDegodez
 */
public record ArazzoStepOperation(String sourceName, String operationId, String method, String path, String reason) {
}
