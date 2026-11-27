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

import io.microcks.domain.ArazzoApiUsage;
import io.microcks.domain.ArazzoDataPassing;
import io.microcks.domain.ArazzoDocument;
import io.microcks.domain.ArazzoExpressionUse;
import io.microcks.domain.ArazzoSourceDescription;
import io.microcks.domain.ArazzoStepCall;
import io.microcks.domain.ArazzoLastResult;
import io.microcks.domain.ArazzoStepCriterion;
import io.microcks.domain.ArazzoStepDetail;
import io.microcks.domain.ArazzoStepOperation;
import io.microcks.domain.ArazzoStepParameter;
import io.microcks.domain.ArazzoStepRequestBody;
import io.microcks.domain.ArazzoUsingDocument;
import io.microcks.domain.ArazzoUsingWorkflow;
import io.microcks.domain.ArazzoWorkflowDetail;
import io.microcks.domain.ArazzoWorkflowInput;
import io.microcks.repository.ArazzoDocumentRepository;
import io.microcks.service.ArazzoDocumentService.ResolvedSource;
import io.microcks.util.ObjectMapperFactory;
import io.microcks.artifact.arazzo.ArazzoDataFlow;
import io.microcks.artifact.arazzo.ArazzoRequestBody;
import io.microcks.artifact.arazzo.ArazzoStep;
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

   private final ArazzoDocumentRepository arazzoDocumentRepository;
   private final ArazzoDocumentService arazzoDocumentService;

   /**
    * Build a new ArazzoWorkflowService with its required dependencies.
    * @param arazzoDocumentRepository The repository of imported Arazzo documents
    * @param arazzoDocumentService    The service linking the source descriptions of a document to Microcks APIs
    */
   @Inject
   public ArazzoWorkflowService(ArazzoDocumentRepository arazzoDocumentRepository,
         ArazzoDocumentService arazzoDocumentService) {
      this.arazzoDocumentRepository = arazzoDocumentRepository;
      this.arazzoDocumentService = arazzoDocumentService;
   }

   /**
    * Read a workflow of an Arazzo document: its steps, the operations they call, and how data flows between them.
    * @param documentId The id of the Arazzo document
    * @param workflowId The id of the workflow in that document
    * @return The workflow, empty if the document or the workflow does not exist
    */
   public Optional<ArazzoWorkflowDetail> getWorkflowDetail(String documentId, String workflowId) {
      return arazzoDocumentRepository.findByIdOptional(documentId).flatMap(document -> ArazzoWorkflowReader
            .workflow(parse(document.getContent()), workflowId).map(workflow -> detail(document, workflow)));
   }

   /**
    * Tell which workflows use an API, and how many workflows call each of its operations.
    * @param serviceId The id of the API
    * @return The workflows using the API, by document, with the number of workflows calling each of its operations
    */
   public ArazzoApiUsage getApiUsage(String serviceId) {
      List<ArazzoUsingDocument> documents = new ArrayList<>();
      Map<String, Integer> operationWorkflowCounts = new HashMap<>();
      for (ArazzoDocument document : arazzoDocumentRepository.findAllSorted()) {
         Map<String, ResolvedSource> sources = arazzoDocumentService.resolveSources(document);
         List<ArazzoUsingWorkflow> workflows = new ArrayList<>();
         for (String workflowId : document.getWorkflowIds()) {
            List<ArazzoStepCall> apiCalls = document.getWorkflowCalls().get(workflowId).stream()
                  .filter(call -> serviceId.equals(sources.get(call.sourceName()).serviceId())).toList();
            if (!apiCalls.isEmpty()) {
               workflows.add(new ArazzoUsingWorkflow(workflowId, ArazzoLastResult.NEVER_RUN));
               calledOperationNames(apiCalls, sources)
                     .forEach(operationName -> operationWorkflowCounts.merge(operationName, 1, Integer::sum));
            }
         }
         if (!workflows.isEmpty()) {
            documents.add(
                  new ArazzoUsingDocument(document.getId(), document.getTitle(), document.getVersion(), workflows));
         }
      }
      return new ArazzoApiUsage(documents, operationWorkflowCounts);
   }

   /**
    * A workflow calls each Microcks operation once, however many of its steps call it; an operation the API does not
    * define has no Microcks operation.
    */
   private static Set<String> calledOperationNames(List<ArazzoStepCall> apiCalls, Map<String, ResolvedSource> sources) {
      Set<String> operationNames = new HashSet<>();
      for (ArazzoStepCall call : apiCalls) {
         ArazzoStepOperation operation = sources.get(call.sourceName()).operations().get(call.operationId());
         if (operation != null) {
            operationNames.add(operation.method() + " " + operation.path());
         }
      }
      return operationNames;
   }

   private ArazzoWorkflowDetail detail(ArazzoDocument document, ArazzoWorkflow workflow) {
      Map<String, ResolvedSource> sources = arazzoDocumentService.resolveSources(document);
      return new ArazzoWorkflowDetail(document.getId(), document.getTitle(), document.getVersion(),
            document.getArazzoVersion(), document.getFileName(), document.getImportDate(), document.getWorkflowIds(),
            sources.values().stream().map(source -> sourceDescription(source, document)).toList(),
            workflow.workflowId(), workflow.summary(), workflow.description(), inputs(workflow),
            workflow.steps().stream().map(step -> step(step, sources)).toList(), workflow.outputs(),
            ArazzoDataFlow.stepOutputPassings(workflow).stream()
                  .map(passing -> new ArazzoDataPassing(passing.fromStepId(), passing.outputName(),
                        expressionUse(passing.use())))
                  .toList());
   }

   private static List<ArazzoWorkflowInput> inputs(ArazzoWorkflow workflow) {
      return ArazzoDataFlow.inputUses(workflow).entrySet().stream().map(input -> new ArazzoWorkflowInput(input.getKey(),
            input.getValue().stream().map(ArazzoWorkflowService::expressionUse).toList())).toList();
   }

   private static ArazzoExpressionUse expressionUse(ArazzoUse use) {
      return new ArazzoExpressionUse(use.stepId(), use.place(), use.target());
   }

   /**
    * A linked source tells how many operations its API defines, and how many of them the steps of the document call.
    */
   private static ArazzoSourceDescription sourceDescription(ResolvedSource source, ArazzoDocument document) {
      Set<String> usedOperationIds = new HashSet<>();
      document.getWorkflowCalls().values()
            .forEach(calls -> calls.stream()
                  .filter(call -> call.sourceName().equals(source.link().sourceName())
                        && source.operations().containsKey(call.operationId()))
                  .forEach(call -> usedOperationIds.add(call.operationId())));
      return new ArazzoSourceDescription(source.link(), source.serviceId(),
            ArazzoDocumentService.unlinkedReason(source.link()), source.operations().size(), usedOperationIds.size());
   }

   private static ArazzoStepDetail step(ArazzoStep step, Map<String, ResolvedSource> sources) {
      return new ArazzoStepDetail(step.stepId(), step.description(),
            ArazzoDocumentService.operationOf(new ArazzoStepCall(step.call().sourceName(), step.call().operationId()),
                  sources),
            step.parameters().stream().map(
                  parameter -> new ArazzoStepParameter(parameter.name(), parameter.in(), parameter.value())).toList(),
            requestBody(step.requestBody()),
            step.successCriteria().stream().map(
                  criterion -> new ArazzoStepCriterion(criterion.condition(), criterion.context(), criterion.type()))
                  .toList(),
            step.outputs());
   }

   private static ArazzoStepRequestBody requestBody(ArazzoRequestBody requestBody) {
      if (requestBody == null) {
         return null;
      }
      return new ArazzoStepRequestBody(requestBody.contentType(), requestBody.payload(), requestBody.replacements());
   }

   private static JsonNode parse(String content) {
      try {
         return ObjectMapperFactory.getYamlObjectMapper().readTree(content);
      } catch (JsonProcessingException jpe) {
         // A stored document that cannot be read has no workflow.
         return MissingNode.getInstance();
      }
   }
}
