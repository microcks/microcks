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

import io.microcks.domain.workflow.WorkflowStepOperation;
import io.microcks.service.arazzo.view.ArazzoApiUsage;
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.service.arazzo.view.ArazzoLastResult;
import io.microcks.service.arazzo.view.ArazzoWorkflowDetail;
import io.microcks.repository.workflow.WorkflowDocumentRepository;
import io.microcks.service.arazzo.ArazzoDocumentService.ResolvedSource;
import io.microcks.util.ObjectMapperFactory;
import io.microcks.artifact.arazzo.ArazzoAction;
import io.microcks.artifact.arazzo.ArazzoCriterion;
import io.microcks.artifact.arazzo.ArazzoDataFlow;
import io.microcks.artifact.arazzo.ArazzoMessaging;
import io.microcks.artifact.arazzo.ArazzoRequestBody;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.artifact.arazzo.ArazzoStepOrder;
import io.microcks.artifact.arazzo.ArazzoUse;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.artifact.arazzo.ArazzoWorkflowReader;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Service bean reading the workflows of imported Arazzo documents.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoWorkflowService {

   private final WorkflowDocumentRepository workflowDocumentRepository;
   private final ArazzoDocumentService arazzoDocumentService;

   /**
    * Build a new ArazzoWorkflowService with its required dependencies.
    * @param workflowDocumentRepository The repository of imported Arazzo documents
    * @param arazzoDocumentService    The service linking the source descriptions of a document to Microcks APIs
    */
   @Inject
   public ArazzoWorkflowService(WorkflowDocumentRepository workflowDocumentRepository,
         ArazzoDocumentService arazzoDocumentService) {
      this.workflowDocumentRepository = workflowDocumentRepository;
      this.arazzoDocumentService = arazzoDocumentService;
   }

   /**
    * Read a workflow of an Arazzo document: its steps, the operations they call, and how data flows between them.
    * @param documentId The id of the Arazzo document
    * @param workflowId The id of the workflow in that document
    * @return The workflow, empty if the document or the workflow does not exist
    */
   public Optional<ArazzoWorkflowDetail> getWorkflowDetail(String documentId, String workflowId) {
      return workflowDocumentRepository.findByIdOptional(documentId).flatMap(document -> ArazzoWorkflowReader
            .workflow(parse(document.getContent()), workflowId).map(workflow -> detail(document, workflow)));
   }

   /**
    * Tell which workflows use an API, and how many workflows call each of its operations.
    * @param serviceId The id of the API
    * @return The workflows using the API, by document, with the number of workflows calling each of its operations
    */
   public ArazzoApiUsage getApiUsage(String serviceId) {
      List<ArazzoApiUsage.Document> documents = new ArrayList<>();
      Map<String, Integer> operationWorkflowCounts = new HashMap<>();
      for (WorkflowDocument document : workflowDocumentRepository.findAllSorted()) {
         Map<String, ResolvedSource> sources = arazzoDocumentService.resolveSources(document);
         List<ArazzoApiUsage.Workflow> workflows = new ArrayList<>();
         for (String workflowId : document.getWorkflowIds()) {
            List<WorkflowStepOperation> apiCalls = document.getWorkflowCalls().get(workflowId).stream()
                  .filter(call -> serviceId.equals(sources.get(call.sourceName()).serviceId())).toList();
            if (!apiCalls.isEmpty()) {
               workflows.add(new ArazzoApiUsage.Workflow(workflowId, ArazzoLastResult.NEVER_RUN));
               calledOperationNames(apiCalls, sources)
                     .forEach(operationName -> operationWorkflowCounts.merge(operationName, 1, Integer::sum));
            }
         }
         if (!workflows.isEmpty()) {
            documents.add(
                  new ArazzoApiUsage.Document(document.getId(), document.getTitle(), document.getVersion(), workflows));
         }
      }
      return new ArazzoApiUsage(documents, operationWorkflowCounts);
   }

   /**
    * A workflow calls each Microcks operation once, however many of its steps call it; an operation the API does not
    * define has no Microcks operation.
    */
   private static Set<String> calledOperationNames(List<WorkflowStepOperation> apiCalls,
         Map<String, ResolvedSource> sources) {
      Set<String> operationNames = new HashSet<>();
      for (WorkflowStepOperation call : apiCalls) {
         WorkflowStepOperation operation = call.operationId() == null ? null
               : sources.get(call.sourceName()).operations().get(call.operationId());
         if (operation != null) {
            operationNames.add(operation.method() + " " + operation.path());
         }
      }
      return operationNames;
   }

   private ArazzoWorkflowDetail detail(WorkflowDocument document, ArazzoWorkflow workflow) {
      Map<String, ResolvedSource> sources = arazzoDocumentService.resolveSources(document);
      return new ArazzoWorkflowDetail(document.getId(), document.getTitle(), document.getVersion(),
            document.getFormatVersion(), document.getFileName(), document.getImportDate(), document.getWorkflowIds(),
            sources.values().stream().map(source -> sourceDescription(source, document)).toList(),
            workflow.workflowId(), workflow.summary(), workflow.description(), inputs(workflow),
            workflow.steps().stream().map(step -> step(step, workflow, sources)).toList(), workflow.outputs(),
            ArazzoDataFlow.stepOutputPassings(workflow).stream()
                  .map(passing -> new ArazzoWorkflowDetail.DataPassing(passing.fromStepId(), passing.outputName(),
                        expressionUse(passing.use())))
                  .toList(),
            ArazzoDocumentService.withAsyncSteps(
                  ArazzoDocumentService.status(workflow.workflowId(),
                        document.getWorkflowCalls().get(workflow.workflowId()), sources),
                  ArazzoDocumentService.asyncStepIds(workflow)),
            arazzoDocumentService.getExamples(document, workflow.workflowId()));
   }

   /** The inputs steps use or the workflow declares, then those only its outputs use or its schema requires. */
   private static List<ArazzoWorkflowDetail.Input> inputs(ArazzoWorkflow workflow) {
      List<String> neededInputs = ArazzoDataFlow.neededInputs(workflow);
      Map<String, List<ArazzoUse>> inputUses = new LinkedHashMap<>(ArazzoDataFlow.inputUses(workflow));
      neededInputs.forEach(inputName -> inputUses.putIfAbsent(inputName, List.of()));
      return inputUses.entrySet().stream()
            .map(input -> new ArazzoWorkflowDetail.Input(input.getKey(),
                  input.getValue().stream().map(ArazzoWorkflowService::expressionUse).toList(),
                  workflow.inputTypes().get(input.getKey()), neededInputs.contains(input.getKey())))
            .toList();
   }

   private static ArazzoWorkflowDetail.ExpressionUse expressionUse(ArazzoUse use) {
      return new ArazzoWorkflowDetail.ExpressionUse(use.stepId(), use.place(), use.target());
   }

   /**
    * A linked source tells how many operations its API defines, and how many of them the steps of the document call.
    */
   private static ArazzoWorkflowDetail.Source sourceDescription(ResolvedSource source, WorkflowDocument document) {
      Set<String> usedOperationIds = new HashSet<>();
      document.getWorkflowCalls().values()
            .forEach(calls -> calls.stream()
                  .filter(call -> call.sourceName().equals(source.link().sourceName()) && call.operationId() != null
                        && source.operations().containsKey(call.operationId()))
                  .forEach(call -> usedOperationIds.add(call.operationId())));
      return new ArazzoWorkflowDetail.Source(source.link(), source.serviceId(),
            ArazzoDocumentService.unlinkedReason(source.link()), source.operations().size(), usedOperationIds.size());
   }

   /**
    * A step tells the steps it declares it depends on, and the steps it must come after, under the rule of
    * {@link ArazzoStepOrder}.
    */
   private static ArazzoWorkflowDetail.Step step(ArazzoStep step, ArazzoWorkflow workflow,
         Map<String, ResolvedSource> sources) {
      return new ArazzoWorkflowDetail.Step(step.stepId(), step.description(), ArazzoDocumentService.operationOf(
            ArazzoDocumentService.declaredOf(step.call()), sources),
            step.parameters().stream()
                  .map(parameter -> new ArazzoWorkflowDetail.Parameter(parameter.name(), parameter.in(),
                        parameter.value()))
                  .toList(),
            requestBody(step.requestBody()), criteria(step.successCriteria()), step.outputs(),
            actions(step.onSuccess()), actions(step.onFailure()), messaging(step.messaging()),
            step.dependsOn() == null ? List.of() : step.dependsOn(),
            List.copyOf(ArazzoStepOrder.prerequisitesOf(workflow, step.stepId())));
   }

   private static ArazzoWorkflowDetail.Messaging messaging(ArazzoMessaging messaging) {
      if (messaging == null) {
         return null;
      }
      return new ArazzoWorkflowDetail.Messaging(messaging.action(), messaging.correlationId(), messaging.timeout());
   }

   private static List<ArazzoWorkflowDetail.Criterion> criteria(List<ArazzoCriterion> criteria) {
      return criteria.stream()
            .map(criterion -> new ArazzoWorkflowDetail.Criterion(criterion.condition(), criterion.context(),
                  criterion.type()))
            .toList();
   }

   private static List<ArazzoWorkflowDetail.Action> actions(List<ArazzoAction> actions) {
      return actions.stream().map(action -> new ArazzoWorkflowDetail.Action(action.name(), action.type(),
            action.retryAfter(), action.retryLimit(), criteria(action.criteria()))).toList();
   }

   private static ArazzoWorkflowDetail.RequestBody requestBody(ArazzoRequestBody requestBody) {
      if (requestBody == null) {
         return null;
      }
      return new ArazzoWorkflowDetail.RequestBody(requestBody.contentType(), requestBody.payload(),
            requestBody.replacements());
   }

   /**
    * @param content The content of a stored Arazzo document
    * @return The parsed document; a missing node when it cannot be read
    */
   static JsonNode parse(String content) {
      try {
         return ObjectMapperFactory.getYamlObjectMapper().readTree(content);
      } catch (JsonProcessingException jpe) {
         // A stored document that cannot be read has no workflow.
         return MissingNode.getInstance();
      }
   }
}
