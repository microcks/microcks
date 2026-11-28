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

import io.microcks.minion.arazzo.runner.OperationTarget;
import io.microcks.minion.arazzo.runner.RunSecret;
import io.microcks.minion.arazzo.runner.RunSpecification;
import io.microcks.minion.arazzo.runner.SourceTarget;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A run request, as Microcks posts it to the minion.
 * @param runId          The id of the run in Microcks, to report its steps and its end to
 * @param workflowId     The workflow to run
 * @param arazzoDocument The Arazzo document, as imported in Microcks
 * @param inputs         The inputs of the workflow, by name
 * @param sources        Where to call the operations of each source description, by source name
 * @param timeoutMS      How long a step waits for its response, in milliseconds
 * @param secret         The secret whose authorization every call carries; null for none
 * @param validateSchema Whether each response is validated against the OpenAPI schema of its operation
 * @author SebastienDegodez
 */
public record RunRequestDTO(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
      Map<String, SourceDTO> sources, long timeoutMS, SecretDTO secret, boolean validateSchema) {

   /**
    * @param microcksUrl The URL of Microcks, that relative base URLs are relative to
    * @return What the minion runs
    */
   public RunSpecification toSpecification(String microcksUrl) {
      Map<String, SourceTarget> targets = new LinkedHashMap<>();
      sources.forEach((name, source) -> {
         Map<String, OperationTarget> operations = new LinkedHashMap<>();
         source.operations().forEach((operationId, operation) -> operations.put(operationId,
               new OperationTarget(operation.method(), operation.path(),
                     operation.requestMediaTypes() == null ? List.of() : operation.requestMediaTypes())));
         // A base URL relative to Microcks is the path of the mocks of the source.
         boolean mocks = source.baseUrl().startsWith("/");
         String baseUrl = mocks ? microcksUrl + source.baseUrl() : source.baseUrl();
         targets.put(name, new SourceTarget(URI.create(baseUrl), operations, mocks, source.openApiContract(),
               source.openApiContractBaseUrl()));
      });
      return new RunSpecification(runId, workflowId, arazzoDocument, inputs, targets, Duration.ofMillis(timeoutMS),
            secret == null ? RunSecret.NONE
                  : new RunSecret(secret.username(), secret.password(), secret.token(), secret.tokenHeader()),
            validateSchema);
   }
}
