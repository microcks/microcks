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

import io.microcks.domain.Operation;
import io.microcks.domain.Service;
import io.microcks.domain.workflow.WorkflowMissingExample;
import io.microcks.domain.workflow.WorkflowParameterValue;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.domain.workflow.WorkflowRunTarget;
import io.microcks.domain.workflow.WorkflowStepResult;
import io.microcks.domain.workflow.WorkflowStepStatus;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.util.DispatchCriteriaHelper;
import io.microcks.util.DispatchCriteriaHelper.DispatcherDetails;
import io.microcks.util.DispatchStyles;
import io.microcks.util.IdBuilder;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service bean telling whether a step of a run against the Microcks mocks failed because the mocks have no example for
 * what the step sent.
 * <p>
 * Rather than reading the answer of the mocks, it looks for the example the mocks would have answered with: Microcks
 * finds the example of a call from the values of the parameters its operation dispatches on, so a failed step misses an
 * example when no example of its operation has the dispatch criteria built from the values the step sent. Only the
 * dispatchers on path and query parameters are told from the parameters of a step; a script, a request body or no
 * dispatcher at all tell nothing.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoMockCoherenceService {

   private static final String PATH = "path";
   private static final String QUERY = "query";

   /** Where the parameters each dispatcher considers are sent. */
   private static final Map<String, Set<String>> DISPATCHED_PARAMETERS = Map.of(DispatchStyles.URI_PARTS, Set.of(PATH),
         DispatchStyles.URI_PARAMS, Set.of(QUERY), DispatchStyles.URI_ELEMENTS, Set.of(PATH, QUERY));

   private final ServiceRepository serviceRepository;
   private final ResponseRepository responseRepository;

   /**
    * Build a new ArazzoMockCoherenceService with its required dependencies.
    * @param serviceRepository  The repository of the APIs whose mocks the runs call
    * @param responseRepository The repository of the examples of these APIs
    */
   @Inject
   public ArazzoMockCoherenceService(ServiceRepository serviceRepository, ResponseRepository responseRepository) {
      this.serviceRepository = serviceRepository;
      this.responseRepository = responseRepository;
   }

   /**
    * @param run  A run of a workflow
    * @param step A step of that run, as the minion reports it
    * @return The example the mocks lack for this step; empty when the step did not fail for a missing example
    */
   public Optional<WorkflowMissingExample> missingExample(WorkflowResult run, WorkflowStepResult step) {
      if (run.getTarget() != WorkflowRunTarget.MOCKS || step.status() != WorkflowStepStatus.FAILED) {
         return Optional.empty();
      }
      return run.getSources().stream().filter(source -> source.sourceName().equals(step.operation().sourceName()))
            .findFirst().flatMap(source -> serviceRepository.findByIdOptional(source.serviceId()))
            .flatMap(service -> missingExample(service, step));
   }

   /** Microcks names the operations of an OpenAPI contract after their method and path. */
   private Optional<WorkflowMissingExample> missingExample(Service service, WorkflowStepResult step) {
      String operationName = step.operation().method() + " " + step.operation().path();
      return service.getOperations().stream().filter(operation -> operation.getName().equals(operationName)).findFirst()
            .flatMap(operation -> missingExample(service, operation, step));
   }

   private Optional<WorkflowMissingExample> missingExample(Service service, Operation operation,
         WorkflowStepResult step) {
      // A fallback dispatches as the dispatcher it wraps before answering its fallback example.
      DispatcherDetails dispatcher = DispatchCriteriaHelper.extractDispatcherWithRules(operation);
      Set<String> dispatched = Optional.ofNullable(dispatcher.rootDispatcher()).map(DISPATCHED_PARAMETERS::get)
            .orElse(Set.of());
      String rules = dispatcher.rootDispatcherRules();
      List<WorkflowParameterValue> dispatching = step.parameters().stream()
            .filter(parameter -> dispatched.contains(parameter.in()))
            .filter(parameter -> !dispatchCriteria(rules, List.of(parameter)).isEmpty()).toList();
      if (dispatching.isEmpty() || !responseRepository.findNonCallbackByOperationIdAndDispatchCriteria(
            IdBuilder.buildOperationId(service, operation), dispatchCriteria(rules, dispatching)).isEmpty()) {
         return Optional.empty();
      }
      return Optional.of(new WorkflowMissingExample(step.index(), step.stepId(), service.getId(),
            step.operation().operationId(), operation.getName(), dispatching));
   }

   /** The dispatch criteria Microcks stores for an example with these values, as its OpenAPI importer builds them. */
   private static String dispatchCriteria(String rules, List<WorkflowParameterValue> parameters) {
      return DispatchCriteriaHelper.buildFromPartsMap(rules, valuesIn(PATH, parameters))
            + DispatchCriteriaHelper.buildFromParamsMap(rules, valuesIn(QUERY, parameters));
   }

   private static Multimap<String, String> valuesIn(String in, List<WorkflowParameterValue> parameters) {
      Multimap<String, String> values = ArrayListMultimap.create();
      parameters.stream().filter(parameter -> in.equals(parameter.in()))
            .forEach(parameter -> values.put(parameter.name(), String.valueOf(parameter.value())));
      return values;
   }
}
