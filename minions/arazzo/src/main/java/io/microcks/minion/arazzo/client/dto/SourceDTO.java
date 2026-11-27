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

import java.util.Map;

/**
 * Where to call the operations of a source description, as Microcks sends it in a run request.
 * @param baseUrl                The base URL of the operations: relative to Microcks for its mocks, such as
 *                               /rest/API+Pastry+-+2.0/2.0.0, or absolute
 * @param operations             The operations of the source, by operationId
 * @param openApiContract        The OpenAPI contract of the API the source is linked to, sent when the responses are
 *                               validated against it; null otherwise
 * @param openApiContractBaseUrl The URL the relative references of the contract resolve against, sent with the
 *                               contract; null otherwise
 * @author SebastienDegodez
 */
public record SourceDTO(String baseUrl, Map<String, OperationDTO> operations, String openApiContract,
      String openApiContractBaseUrl) {
}
