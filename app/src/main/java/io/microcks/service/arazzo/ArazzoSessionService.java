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
import io.microcks.artifact.arazzo.ArazzoWorkflowReader;
import io.microcks.artifact.openapi.OpenAPISchemaValidator;
import io.microcks.domain.Request;
import io.microcks.domain.Response;
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoSession;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.domain.arazzo.ArazzoStepCall;
import io.microcks.domain.arazzo.ArazzoStepOperation;
import io.microcks.domain.arazzo.ArazzoWorkflowStatus;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.domain.arazzo.WorkflowRunOrigin;
import io.microcks.domain.arazzo.WorkflowRunStatus;
import io.microcks.domain.arazzo.WorkflowRunTarget;
import io.microcks.domain.arazzo.WorkflowSessionCall;
import io.microcks.domain.arazzo.WorkflowSessionCheck;
import io.microcks.domain.arazzo.WorkflowStepOperation;
import io.microcks.domain.arazzo.WorkflowStepResult;
import io.microcks.domain.arazzo.WorkflowStepStatus;
import io.microcks.repository.arazzo.ArazzoSessionCallRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import io.microcks.service.arazzo.ArazzoDocumentService.ResolvedSource;
import io.microcks.service.arazzo.ArazzoSessionVerifier.ExpectedStep;
import io.microcks.service.arazzo.ArazzoSessionVerifier.StepVerdict;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service bean verifying that an application followed a workflow of an Arazzo document against the Microcks mocks: a
 * test opens a session on the workflow, the mocks record the calls the application makes in that session, then the test
 * asks for the verification of the session.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoSessionService {

   private final ArazzoDocumentService arazzoDocumentService;
   private final ArazzoRunService arazzoRunService;
   private final ArazzoSessionRepository arazzoSessionRepository;
   private final ArazzoSessionCallRepository arazzoSessionCallRepository;
   private final WorkflowResultRepository workflowResultRepository;

   /** The URL the relative references of the stored contracts resolve against, as for OpenAPI contract tests. */
   @ConfigProperty(name = "validation.resourceUrl", defaultValue = "http://localhost:8080/api/resources/")
   String validationResourceUrl;

   /** How long an open session records calls, in seconds, before it expires with them. */
   @ConfigProperty(name = "arazzo.sessions.max-open-duration", defaultValue = "3600")
   long maxOpenDuration;

   /** How long a closed session is kept with its calls, in seconds, before it expires. */
   @ConfigProperty(name = "arazzo.sessions.retention", defaultValue = "600")
   long retention;

   /** How many calls a session records at most; the next ones are not recorded. */
   @ConfigProperty(name = "arazzo.sessions.max-calls", defaultValue = "1000")
   long maxCalls;

   /**
    * Build a new ArazzoSessionService with its required dependencies.
    * @param arazzoDocumentService       The service finding the Arazzo documents and linking their sources to APIs
    * @param arazzoRunService            The service numbering the runs and storing their exchanges
    * @param arazzoSessionRepository     The repository of the sessions
    * @param arazzoSessionCallRepository The repository of the calls the sessions received
    * @param workflowResultRepository    The repository the verifications are kept in, with the runs
    */
   @Inject
   public ArazzoSessionService(ArazzoDocumentService arazzoDocumentService, ArazzoRunService arazzoRunService,
         ArazzoSessionRepository arazzoSessionRepository, ArazzoSessionCallRepository arazzoSessionCallRepository,
         WorkflowResultRepository workflowResultRepository) {
      this.arazzoDocumentService = arazzoDocumentService;
      this.arazzoRunService = arazzoRunService;
      this.arazzoSessionRepository = arazzoSessionRepository;
      this.arazzoSessionCallRepository = arazzoSessionCallRepository;
      this.workflowResultRepository = workflowResultRepository;
   }

   /**
    * Open a verification session on a workflow.
    * @param documentId The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId The id of the workflow in that document
    * @return The session, open; empty if the document does not exist
    * @throws ArazzoRunRefusedException if the workflow is unknown, or if one of its steps calls no operation of a
    *                                   linked API
    */
   public Optional<ArazzoSession> openSession(String documentId, String workflowId) throws ArazzoRunRefusedException {
      return openSession(documentId, workflowId, null);
   }

   /**
    * Open a verification session on a workflow, with the inputs of the workflow whose values the calls must carry.
    * @param documentId The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId The id of the workflow in that document
    * @param inputs     The inputs of the workflow, by name; null to check no value coming from them
    * @return The session, open; empty if the document does not exist
    * @throws ArazzoRunRefusedException if the workflow is unknown, if one of its steps calls no operation of a linked
    *                                   API, or if an input is unknown, invalid or missing
    */
   public Optional<ArazzoSession> openSession(String documentId, String workflowId, Map<String, Object> inputs)
         throws ArazzoRunRefusedException {
      Optional<ArazzoDocument> found = arazzoDocumentService.getDocument(documentId);
      if (found.isEmpty()) {
         return Optional.empty();
      }
      ArazzoDocument document = found.get();
      if (!document.getWorkflowIds().contains(workflowId)) {
         throw new ArazzoRunRefusedException("Unknown workflow: " + workflowId);
      }
      ArazzoWorkflowStatus status = ArazzoDocumentService.status(workflowId,
            document.getWorkflowCalls().get(workflowId), arazzoDocumentService.resolveSources(document));
      if (!status.runnable()) {
         throw new ArazzoRunRefusedException(
               "Workflow " + workflowId + " cannot be verified: " + String.join(", ", status.reasons()));
      }
      if (inputs != null) {
         Optional<String> refusal = ArazzoSessionInputs.refusal(workflowOf(document, workflowId), inputs);
         if (refusal.isPresent()) {
            throw new ArazzoRunRefusedException(refusal.get());
         }
      }
      Date now = new Date();
      ArazzoSession session = new ArazzoSession();
      session.setDocumentId(document.getId());
      session.setDocumentTitle(document.getTitle());
      session.setDocumentVersion(document.getVersion());
      session.setWorkflowId(workflowId);
      session.setOpenedDate(now);
      session.setInputs(inputs);
      // A session that is never closed expires after its maximum duration, so that its calls are not kept forever.
      session.setExpireAt(new Date(now.getTime() + maxOpenDuration * 1000L));
      return Optional.of(arazzoSessionRepository.save(session));
   }

   /**
    * Record a call the mocks received naming a session; a call naming no open session, or a session that recorded its
    * maximum of calls, is not recorded.
    * @param sessionId The id of the session the call names
    * @param call      The call, as the mocks received and answered it
    */
   public void recordCall(String sessionId, ArazzoSessionCall call) {
      Date now = new Date();
      ArazzoSession session = arazzoSessionRepository.countCall(sessionId, maxCalls, now);
      if (session == null) {
         // A session that recorded its maximum of calls counts those it does not record.
         arazzoSessionRepository.countDroppedCall(sessionId, maxCalls, now);
      } else {
         call.setSessionId(sessionId);
         call.setIndex(session.getCallCount() - 1);
         call.setReceivedDate(now);
         call.setExpireAt(session.getExpireAt());
         arazzoSessionCallRepository.save(call);
      }
   }

   /**
    * Close a session: it records no more calls, and it is kept with its calls for the retention delay.
    * @param sessionId The id of the session
    * @return Whether the session exists
    */
   public boolean closeSession(String sessionId) {
      Date now = new Date();
      Date expireAt = new Date(now.getTime() + retention * 1000L);
      if (arazzoSessionRepository.closeOpen(sessionId, now, expireAt)) {
         arazzoSessionCallRepository.expireCalls(sessionId, expireAt);
         return true;
      }
      return arazzoSessionRepository.findByIdOptional(sessionId).isPresent();
   }

   /**
    * Verify that the calls a session received follow the steps of its workflow.
    * @param sessionId The id of the session
    * @return The verification, kept with the runs of the workflow; empty if the session does not exist
    */
   public Optional<WorkflowResult> verify(String sessionId) {
      return arazzoSessionRepository.findByIdOptional(sessionId).flatMap(session -> arazzoDocumentService
            .getDocument(session.getDocumentId()).map(document -> verify(session, document)));
   }

   private WorkflowResult verify(ArazzoSession session, ArazzoDocument document) {
      Map<String, ResolvedSource> sources = arazzoDocumentService.resolveSources(document);
      List<ArazzoSessionCall> calls = arazzoSessionCallRepository.findBySessionIdOrderByIndexAsc(session.getId());
      Map<String, Object> inputs = session.getInputs() == null ? Map.of() : session.getInputs();
      List<StepVerdict> verdicts = ArazzoSessionVerifier.verify(workflowOf(document, session.getWorkflowId()),
            expectedSteps(document, session, sources), calls, inputs, validationResourceUrl);

      WorkflowResult result = new WorkflowResult();
      result.setDocumentId(document.getId());
      result.setDocumentTitle(document.getTitle());
      result.setDocumentVersion(document.getVersion());
      result.setWorkflowId(session.getWorkflowId());
      result.setTarget(WorkflowRunTarget.MOCKS);
      result.setInputs(inputs);
      result.setStartedDate(new Date());
      result.setNumber(arazzoRunService.nextNumber(document.getId()));
      result.setOrigin(WorkflowRunOrigin.SESSION);
      WorkflowResult saved = workflowResultRepository.save(result);

      List<WorkflowStepResult> steps = new ArrayList<>();
      Map<Long, String> stepOfCall = new HashMap<>();
      for (StepVerdict verdict : verdicts) {
         if (verdict.call() != null) {
            stepOfCall.put(verdict.call().getIndex(), verdict.expected().step().stepId());
         }
         steps.add(stepResult(saved.getId(), verdict));
      }
      saved.setSteps(steps);
      saved.setOutputs(ArazzoSessionVerifier.workflowOutputs(workflowOf(document, session.getWorkflowId()).outputs(),
            inputs, verdicts));
      // Calls the session did not record may be steps of the workflow: the verification cannot pass without them.
      long dropped = session.getDroppedCallCount();
      if (dropped > 0) {
         saved.setMessage("session reached " + session.getCallCount() + " calls, " + dropped
               + (dropped == 1 ? " call was" : " calls were") + " not recorded");
      }
      saved.setStatus(dropped == 0 && verdicts.stream().allMatch(verdict -> verdict.failures().isEmpty())
            ? WorkflowRunStatus.SUCCESS
            : WorkflowRunStatus.FAILURE);
      saved.setSession(new WorkflowSessionCheck(session.getId(),
            calls.stream().map(call -> new WorkflowSessionCall(call.getIndex(), call.getServiceName(),
                  call.getServiceVersion(), call.getMethod(), call.getPath(), stepOfCall.get(call.getIndex())))
                  .toList()));
      return workflowResultRepository.save(saved);
   }

   /** The steps of the workflow of the session, each with its operation in the API its source is linked to. */
   private List<ExpectedStep> expectedSteps(ArazzoDocument document, ArazzoSession session,
         Map<String, ResolvedSource> sources) {
      ArazzoWorkflow workflow = workflowOf(document, session.getWorkflowId());
      Map<String, JsonNode> contracts = new HashMap<>();
      List<ExpectedStep> steps = new ArrayList<>();
      for (ArazzoStep step : workflow.steps()) {
         ArazzoStepOperation operation = ArazzoDocumentService.operationOf(
               new ArazzoStepCall(step.call().sourceName(), step.call().operationId(), step.call().channel()), sources);
         ResolvedSource source = sources.get(step.call().sourceName());
         steps.add(new ExpectedStep(steps.size(), step, operation, source.link().apiName(), source.link().apiVersion(),
               contracts.computeIfAbsent(source.link().sourceName(), sourceName -> contractOf(source))));
      }
      return steps;
   }

   /** The main OpenAPI contract of the API a source is linked to, read; null when it has none or it cannot be read. */
   private JsonNode contractOf(ResolvedSource source) {
      String contract = arazzoRunService.openApiContractOf(source);
      try {
         return contract == null ? null : OpenAPISchemaValidator.getJsonNodeForSchema(contract);
      } catch (IOException unreadable) {
         return null;
      }
   }

   private static ArazzoWorkflow workflowOf(ArazzoDocument document, String workflowId) {
      return ArazzoWorkflowReader.workflow(ArazzoWorkflowService.parse(document.getContent()), workflowId)
            .orElseThrow();
   }

   /**
    * A step matched to a call keeps the exchange of the call, as a step of a run keeps its own; a step fails with the
    * reasons of its verdict.
    */
   private WorkflowStepResult stepResult(String resultId, StepVerdict verdict) {
      ExpectedStep expected = verdict.expected();
      ArazzoStepOperation operation = expected.operation();
      WorkflowStepOperation stepOperation = new WorkflowStepOperation(operation.sourceName(), operation.operationId(),
            operation.method(), operation.path());
      WorkflowStepStatus status = verdict.failures().isEmpty() ? WorkflowStepStatus.PASSED : WorkflowStepStatus.FAILED;
      String message = verdict.failures().isEmpty() ? null : String.join("; ", verdict.failures());
      ArazzoSessionCall call = verdict.call();
      if (call == null) {
         return new WorkflowStepResult(expected.index(), expected.step().stepId(), stepOperation, status, null, 0,
               List.of(), Map.of(), null, message, null, List.of(), Map.of());
      }
      String testCaseId = resultId + "-" + expected.index();
      arazzoRunService.storeExchange(testCaseId, requestOf(call, expected), responseOf(call, expected));
      return new WorkflowStepResult(expected.index(), expected.step().stepId(), stepOperation, status,
            call.getResponseStatus(), 0, List.of(), verdict.outputs(), testCaseId, message, call.getRequestUrl(),
            verdict.parameters(), outputOrigins(expected.step(), verdict.outputs()));
   }

   /** The runtime expression each output of a step comes from, for the outputs that have a value. */
   private static Map<String, String> outputOrigins(ArazzoStep step, Map<String, Object> outputs) {
      Map<String, String> origins = new LinkedHashMap<>();
      step.outputs().forEach((name, expression) -> {
         if (outputs.containsKey(name)) {
            origins.put(name, expression);
         }
      });
      return origins;
   }

   private static Request requestOf(ArazzoSessionCall call, ExpectedStep expected) {
      Request request = new Request();
      request.setName(expected.step().stepId());
      request.setContent(call.getRequestBody());
      request.setHeaders(call.getRequestHeaders());
      return request;
   }

   private static Response responseOf(ArazzoSessionCall call, ExpectedStep expected) {
      Response response = new Response();
      response.setName(expected.step().stepId());
      response.setStatus(String.valueOf(call.getResponseStatus()));
      response.setContent(call.getResponseBody());
      response.setHeaders(call.getResponseHeaders());
      return response;
   }
}
