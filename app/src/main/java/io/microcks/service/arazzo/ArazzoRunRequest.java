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
package io.microcks.service;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * A run of an Arazzo workflow, as Microcks posts it to the Arazzo minion. The minion has its own model of a run: only
 * this request and the reports it posts back cross the boundary.
 * @param runId          The id of the run, to report its steps and its end to
 * @param workflowId     The workflow to run
 * @param arazzoDocument The Arazzo document, as imported
 * @param inputs         The inputs of the workflow, by name
 * @param sources        Where to call the operations of each linked source description, by source name
 * @param timeoutMS      How long a step waits for its response, in milliseconds
 * @param secret         The secret whose authorization every call carries; null for none
 * @param validateSchema Whether each response is validated against the OpenAPI schema of its operation
 * @author SebastienDegodez
 */
public record ArazzoRunRequest(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
      Map<String, Source> sources, long timeoutMS, @JsonInclude(JsonInclude.Include.NON_NULL) Secret secret,
      boolean validateSchema) {

   /**
    * A run whose calls carry no secret and whose responses are only checked by the success criteria of their steps.
    * @param runId          The id of the run, to report its steps and its end to
    * @param workflowId     The workflow to run
    * @param arazzoDocument The Arazzo document, as imported
    * @param inputs         The inputs of the workflow, by name
    * @param sources        Where to call the operations of each linked source description, by source name
    * @param timeoutMS      How long a step waits for its response, in milliseconds
    */
   public ArazzoRunRequest(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
         Map<String, Source> sources, long timeoutMS) {
      this(runId, workflowId, arazzoDocument, inputs, sources, timeoutMS, null, false);
   }

   /**
    * A run whose responses are only checked by the success criteria of their steps.
    * @param runId          The id of the run, to report its steps and its end to
    * @param workflowId     The workflow to run
    * @param arazzoDocument The Arazzo document, as imported
    * @param inputs         The inputs of the workflow, by name
    * @param sources        Where to call the operations of each linked source description, by source name
    * @param timeoutMS      How long a step waits for its response, in milliseconds
    * @param secret         The secret whose authorization every call carries; null for none
    */
   public ArazzoRunRequest(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
         Map<String, Source> sources, long timeoutMS, Secret secret) {
      this(runId, workflowId, arazzoDocument, inputs, sources, timeoutMS, secret, false);
   }

   /**
    * A run whose calls carry no secret.
    * @param runId          The id of the run, to report its steps and its end to
    * @param workflowId     The workflow to run
    * @param arazzoDocument The Arazzo document, as imported
    * @param inputs         The inputs of the workflow, by name
    * @param sources        Where to call the operations of each linked source description, by source name
    * @param timeoutMS      How long a step waits for its response, in milliseconds
    * @param validateSchema Whether each response is validated against the OpenAPI schema of its operation
    */
   public ArazzoRunRequest(String runId, String workflowId, String arazzoDocument, Map<String, Object> inputs,
         Map<String, Source> sources, long timeoutMS, boolean validateSchema) {
      this(runId, workflowId, arazzoDocument, inputs, sources, timeoutMS, null, validateSchema);
   }

   /**
    * The authorization a secret of Microcks defines, as contract tests apply it: Basic with a username and a password,
    * the token in its own header when the secret names one, Bearer otherwise.
    * @param name        The name of the secret in Microcks
    * @param username    The username of Basic authentication; null for none
    * @param password    The password of Basic authentication; null for none
    * @param token       The token; null for none
    * @param tokenHeader The header carrying the token; null for Authorization: Bearer
    */
   public record Secret(String name, String username, String password, String token, String tokenHeader) {
   }

   /**
    * Where to call the operations of a source description.
    * @param baseUrl                The base URL of the operations: relative to Microcks for its mocks
    * @param operations             The method and path of each operation of the linked API, by operationId
    * @param openApiContract        The OpenAPI contract of the linked API, when the responses are validated against it;
    *                               null otherwise
    * @param openApiContractBaseUrl The URL the relative references of the contract resolve against, sent with the
    *                               contract; null otherwise
    */
   public record Source(String baseUrl, Map<String, Operation> operations, String openApiContract,
         String openApiContractBaseUrl) {

      /** A source whose responses are not validated against a contract. */
      public Source(String baseUrl, Map<String, Operation> operations) {
         this(baseUrl, operations, null);
      }

      /** A source whose contract has no base URL to resolve its relative references against. */
      public Source(String baseUrl, Map<String, Operation> operations, String openApiContract) {
         this(baseUrl, operations, openApiContract, null);
      }
   }

   /**
    * @param method The HTTP method of an operation
    * @param path   Its path template
    */
   public record Operation(String method, String path) {
   }
}
