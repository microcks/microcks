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
package io.microcks.minion.arazzo.runner;

import java.time.Duration;
import java.util.Map;

/**
 * What the minion runs: a workflow of an Arazzo document, with its inputs and, for each source description, where its
 * operations are called.
 * @param runId          The id of the run in Microcks
 * @param workflowId     The id of the workflow to run
 * @param arazzoDocument The Arazzo document, as imported in Microcks
 * @param inputs         The inputs of the workflow, by name
 * @param sources        Where the operations of each source description are called, by source name
 * @param timeout        How long a step waits for its response
 * @param secret         The authorization every call carries
 * @param validateSchema Whether each response is validated against the OpenAPI schema of its operation
 * @author SebastienDegodez
 */
public record RunSpecification(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
      Map<String, SourceTarget> sources, Duration timeout, RunSecret secret, boolean validateSchema) {

   /**
    * A run whose calls carry no secret and whose responses are only checked by the success criteria of their steps.
    * @param runId          The id of the run in Microcks
    * @param workflowId     The id of the workflow to run
    * @param arazzoDocument The Arazzo document, as imported in Microcks
    * @param inputs         The inputs of the workflow, by name
    * @param sources        Where the operations of each source description are called, by source name
    * @param timeout        How long a step waits for its response
    */
   public RunSpecification(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
         Map<String, SourceTarget> sources, Duration timeout) {
      this(runId, workflowId, arazzoDocument, inputs, sources, timeout, RunSecret.NONE, false);
   }

   /**
    * A run whose calls carry a secret and whose responses are only checked by the success criteria of their steps.
    * @param runId          The id of the run in Microcks
    * @param workflowId     The id of the workflow to run
    * @param arazzoDocument The Arazzo document, as imported in Microcks
    * @param inputs         The inputs of the workflow, by name
    * @param sources        Where the operations of each source description are called, by source name
    * @param timeout        How long a step waits for its response
    * @param secret         The authorization every call carries
    */
   public RunSpecification(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
         Map<String, SourceTarget> sources, Duration timeout, RunSecret secret) {
      this(runId, workflowId, arazzoDocument, inputs, sources, timeout, secret, false);
   }

   /**
    * A run whose calls carry no secret.
    * @param runId          The id of the run in Microcks
    * @param workflowId     The id of the workflow to run
    * @param arazzoDocument The Arazzo document, as imported in Microcks
    * @param inputs         The inputs of the workflow, by name
    * @param sources        Where the operations of each source description are called, by source name
    * @param timeout        How long a step waits for its response
    * @param validateSchema Whether each response is validated against the OpenAPI schema of its operation
    */
   public RunSpecification(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
         Map<String, SourceTarget> sources, Duration timeout, boolean validateSchema) {
      this(runId, workflowId, arazzoDocument, inputs, sources, timeout, RunSecret.NONE, validateSchema);
   }
}
