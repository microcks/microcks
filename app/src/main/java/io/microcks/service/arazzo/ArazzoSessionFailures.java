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
package io.microcks.service.arazzo;

import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.domain.Operation;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.domain.arazzo.ArazzoInjectedFailure;
import io.microcks.domain.arazzo.ArazzoStepCall;
import io.microcks.domain.arazzo.ArazzoStepOperation;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;
import io.microcks.service.arazzo.ArazzoDocumentService.ResolvedSource;
import io.microcks.util.IdBuilder;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service bean making steps of a workflow fail on the mocks, in the verification session that sets them to fail: there,
 * the mocks answer the operation of such a step with the existing example the session chose for it, so that a test can
 * check how an application compensates a refusal. Elsewhere, the mocks answer as usual.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoSessionFailures {

   private final ArazzoSessionRepository arazzoSessionRepository;
   private final ResponseRepository responseRepository;

   /**
    * Build a new ArazzoSessionFailures with its required dependencies.
    * @param arazzoSessionRepository The repository of the sessions, which keep their failures
    * @param responseRepository      The repository of the examples of the mocks
    */
   @Inject
   public ArazzoSessionFailures(ArazzoSessionRepository arazzoSessionRepository,
         ResponseRepository responseRepository) {
      this.arazzoSessionRepository = arazzoSessionRepository;
      this.responseRepository = responseRepository;
   }

   /**
    * The failures a session sets, each with the operation of the mocks that answers it.
    * @param failures The steps set to fail, by step id, with the name of the example each one answers with
    * @param workflow The workflow of the session
    * @param sources  The sources of its document, each with the API it is linked to
    * @return The failures to inject in the session, in the order they were set
    * @throws ArazzoRunRefusedException if a step is not in the workflow, or if its operation has no such example
    */
   List<ArazzoInjectedFailure> resolve(Map<String, String> failures, ArazzoWorkflow workflow,
         Map<String, ResolvedSource> sources) throws ArazzoRunRefusedException {
      List<ArazzoInjectedFailure> injected = new ArrayList<>();
      for (Map.Entry<String, String> failure : failures.entrySet()) {
         String stepId = failure.getKey();
         String exampleName = failure.getValue();
         ArazzoStep step = workflow.steps().stream().filter(candidate -> candidate.stepId().equals(stepId)).findFirst()
               .orElseThrow(() -> refused(stepId, workflow.workflowId() + " has no such step"));
         ArazzoStepOperation operation = ArazzoDocumentService.operationOf(
               new ArazzoStepCall(step.call().sourceName(), step.call().operationId(), step.call().channel()), sources);
         ResolvedSource source = sources.get(step.call().sourceName());
         String operationName = operation.method() + " " + operation.path();
         if (example(source.api(), operationName, exampleName).isEmpty()) {
            throw refused(stepId, operationName + " of " + source.link().apiName() + " " + source.link().apiVersion()
                  + " has no example `" + exampleName + "`");
         }
         injected.add(new ArazzoInjectedFailure(stepId, source.link().apiName(), source.link().apiVersion(),
               operationName, exampleName));
      }
      return injected;
   }

   /**
    * The example the mocks answer a call with when the session the call names set the operation it calls to fail. A
    * closed session injects nothing any more, as it records no more calls: the mocks answer as usual.
    * @param sessionId The id of the session the call names; null when it names none
    * @param api       The API whose mocks received the call
    * @param operation The operation the call was dispatched to
    * @return The example to answer with; empty when the mocks answer as usual
    */
   public Optional<Response> injectedFailure(String sessionId, Service api, Operation operation) {
      if (sessionId == null) {
         return Optional.empty();
      }
      return arazzoSessionRepository.findByIdOptional(sessionId).filter(session -> session.getClosedDate() == null)
            .flatMap(session -> exampleToInject(session.getInjectedFailures(), api, operation))
            .flatMap(exampleName -> example(api, operation.getName(), exampleName));
   }

   /**
    * The decision to inject a failure: a call to an operation that a failure of its session names is answered with the
    * example of that failure.
    * @param failures  The failures of the session the call names
    * @param api       The API whose mocks received the call
    * @param operation The operation the call was dispatched to
    * @return The name of the example to answer with; empty when no failure names the operation
    */
   static Optional<String> exampleToInject(List<ArazzoInjectedFailure> failures, Service api, Operation operation) {
      return failures.stream()
            .filter(failure -> failure.apiName().equals(api.getName()) && failure.apiVersion().equals(api.getVersion())
                  && failure.operationName().equals(operation.getName()))
            .map(ArazzoInjectedFailure::exampleName).findFirst();
   }

   /** The example of an operation of an API, by its name; empty when the operation or the example does not exist. */
   private Optional<Response> example(Service api, String operationName, String exampleName) {
      return api.getOperations().stream().filter(operation -> operation.getName().equals(operationName)).findFirst()
            .flatMap(operation -> responseRepository
                  .findNonCallbackByOperationIdAndName(IdBuilder.buildOperationId(api, operation), exampleName).stream()
                  .findFirst());
   }

   private static ArazzoRunRefusedException refused(String stepId, String reason) {
      return new ArazzoRunRefusedException("Cannot make step `" + stepId + "` fail: " + reason);
   }
}
