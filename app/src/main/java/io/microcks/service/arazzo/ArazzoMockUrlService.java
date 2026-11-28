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

import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.domain.workflow.WorkflowCriterionResult;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.domain.workflow.WorkflowRunStatus;
import io.microcks.domain.workflow.WorkflowStepResult;
import io.microcks.domain.workflow.WorkflowStepStatus;
import io.microcks.repository.workflow.WorkflowDocumentRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Service bean playing a workflow of an Arazzo document on the Microcks mocks when an application posts its inputs to
 * the Mock URL of the workflow, and answering with its outputs, or with why it did not pass.
 * @author SebastienDegodez
 */
@ApplicationScoped
public class ArazzoMockUrlService {

   /** How often the run is read again while it is in progress, in milliseconds. */
   private static final long POLL_INTERVAL = 50L;
   private static final ObjectMapper JSON = new ObjectMapper();

   private final WorkflowDocumentRepository workflowDocumentRepository;
   private final ArazzoRunService arazzoRunService;

   /** How long a call waits for the run to end before answering 504, in milliseconds. */
   @ConfigProperty(name = "arazzo.mock-url.timeout", defaultValue = "30000")
   long timeout;

   /**
    * Build a new ArazzoMockUrlService with its required dependencies.
    * @param workflowDocumentRepository The repository of the imported Arazzo documents
    * @param arazzoRunService         The service running the workflows and recording their runs
    */
   @Inject
   public ArazzoMockUrlService(WorkflowDocumentRepository workflowDocumentRepository,
         ArazzoRunService arazzoRunService) {
      this.workflowDocumentRepository = workflowDocumentRepository;
      this.arazzoRunService = arazzoRunService;
   }

   /**
    * Play a workflow on the mocks and wait for its outputs.
    * @param documentTitle   The title of the Arazzo document
    * @param documentVersion The version of the Arazzo document
    * @param workflowId      The id of the workflow in that document
    * @param inputs          The inputs of the workflow as posted, a JSON object; null or blank for none
    * @return The outputs of the workflow, or why it did not pass
    */
   public ArazzoMockUrlAnswer play(String documentTitle, String documentVersion, String workflowId, String inputs) {
      WorkflowDocument document = workflowDocumentRepository.findByTitleAndVersion(documentTitle, documentVersion);
      if (document == null) {
         return refusal(404, "Unknown Arazzo document: " + documentTitle + " " + documentVersion);
      }
      if (!document.getWorkflowIds().contains(workflowId)) {
         return refusal(404, "Unknown workflow: " + workflowId);
      }
      Map<String, Object> givenInputs = parse(inputs);
      if (givenInputs == null) {
         return refusal(400, "The inputs must be a JSON object");
      }
      try {
         WorkflowResult run = arazzoRunService.launchMockUrlRun(document.getId(), workflowId, givenInputs)
               .orElseThrow();
         return answerOf(awaitEnd(run));
      } catch (ArazzoRunRefusedException arre) {
         return refusal(400, arre.getMessage());
      }
   }

   /** The posted inputs, by name; null when they are not a JSON object. */
   private static Map<String, Object> parse(String inputs) {
      if (inputs == null || inputs.isBlank()) {
         return Map.of();
      }
      try {
         JsonNode node = JSON.readTree(inputs);
         return node.isObject() ? JSON.convertValue(node, new TypeReference<Map<String, Object>>() {
         }) : null;
      } catch (JsonProcessingException jpe) {
         return null;
      }
   }

   /** The run once it ended, or as it is when the call stops waiting for it: read again every poll interval. */
   private WorkflowResult awaitEnd(WorkflowResult launched) {
      WorkflowResult run = arazzoRunService.getRun(launched.getId()).orElse(launched);
      for (long poll = 0; poll < timeout / POLL_INTERVAL && run.getStatus() == WorkflowRunStatus.IN_PROGRESS; poll++) {
         try {
            Thread.sleep(POLL_INTERVAL);
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return run;
         }
         run = arazzoRunService.getRun(launched.getId()).orElse(run);
      }
      return run;
   }

   /**
    * A passed run answers its outputs; a failed one the error status the mock of its failing step answered, else 502;
    * one still in progress 504.
    */
   private ArazzoMockUrlAnswer answerOf(WorkflowResult run) {
      if (run.getStatus() == WorkflowRunStatus.SUCCESS) {
         return new ArazzoMockUrlAnswer(200, run.getOutputs() == null ? Map.of() : run.getOutputs(), run.getId(), null,
               null);
      }
      if (run.getStatus() == WorkflowRunStatus.IN_PROGRESS) {
         return new ArazzoMockUrlAnswer(504, null, run.getId(), null,
               "Run #" + run.getNumber() + " did not end within " + timeout + " ms");
      }
      WorkflowStepResult failed = run.getSteps().stream().filter(step -> step.status() == WorkflowStepStatus.FAILED)
            .reduce((first, last) -> last).orElse(null);
      if (failed == null) {
         return new ArazzoMockUrlAnswer(502, null, run.getId(), null, run.getMessage());
      }
      int status = failed.statusCode() != null && failed.statusCode() >= 400 ? failed.statusCode() : 502;
      return new ArazzoMockUrlAnswer(status, null, run.getId(), failed.stepId(),
            "Step " + failed.stepId() + " failed: " + whyFailed(failed));
   }

   /** The message of a failed step, else the success criteria it did not meet. */
   private static String whyFailed(WorkflowStepResult failed) {
      if (failed.message() != null) {
         return failed.message();
      }
      return "unmet success criteria " + String.join(", ", failed.criteria().stream()
            .filter(criterion -> !criterion.held()).map(WorkflowCriterionResult::condition).toList());
   }

   private static ArazzoMockUrlAnswer refusal(int status, String reason) {
      return new ArazzoMockUrlAnswer(status, null, null, null, reason);
   }
}
