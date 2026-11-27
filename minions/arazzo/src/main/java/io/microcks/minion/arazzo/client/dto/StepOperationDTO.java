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
package io.microcks.minion.arazzo.client.dto;

/**
 * The operation a step calls, as the minion reports it.
 * @param sourceName  The source description
 * @param operationId The operationId
 * @param method      The HTTP method; null when not known
 * @param path        The path template; null when not known
 * @author SebastienDegodez
 */
public record StepOperationDTO(String sourceName, String operationId, String method, String path) {
}
