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

import io.microcks.service.ArtifactInfo;
import io.microcks.service.MessageService;
import io.microcks.service.ServiceService;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoStepAction;
import io.microcks.domain.arazzo.ArazzoStepDetail;
import io.microcks.domain.arazzo.ArazzoWorkflowDetail;
import io.microcks.domain.Request;
import io.microcks.domain.RequestResponsePair;
import io.microcks.domain.Response;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.repository.arazzo.WorkflowRunFilter;
import io.microcks.domain.arazzo.WorkflowRunStatus;
import io.microcks.domain.arazzo.WorkflowStepOperation;
import io.microcks.domain.arazzo.WorkflowStepResult;
import io.microcks.domain.arazzo.WorkflowStepStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of S17, retrying a step until it succeeds or ending the workflow early, entering through the
 * services of the webapp with the Arazzo minion standing at its boundary. The examples use the demo of the story: the
 * Order status API, whose status mock answers preparing, preparing, then ready, and the Order tracking document.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoStepActionsTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final WorkflowStepOperation GET_ORDER_STATUS = new WorkflowStepOperation("orderStatusApi",
         "getOrderStatus", "GET", "/order/{id}/status");

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ArazzoWorkflowService arazzoWorkflowService;

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoRunHistoryService arazzoRunHistoryService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private MessageService messageService;

   private final RecordingMinionClient minion = new RecordingMinionClient();

   /** The minion stays at the boundary: runs are recorded, not executed. */
   @BeforeEach
   void recordTheRunsHandedToTheMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   /** AC-01, AC-06: the demo of the story is imported, and its workflows can run against the stateful mock. */
   @Test
   void aDocumentWhoseStepsRetryOrEndTheWorkflowIsImportedAndCanRun() throws Exception {
      ArazzoDocument document = orderTrackingIsImportedAndLinked();

      assertEquals("Order tracking 1.0.0 [waitUntilReady, giveUpQuickly, readUnlessPreparing]",
            document.getTitle() + " " + document.getVersion() + " " + document.getWorkflowIds());
      for (String workflowId : document.getWorkflowIds()) {
         assertTrue(
               arazzoWorkflowService.getWorkflowDetail(document.getId(), workflowId).orElseThrow().status().runnable(),
               workflowId + " cannot run");
      }
   }

   /** AC-05: each step tells what it does when it fails or succeeds, in the order of the document. */
   @ParameterizedTest
   @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
         "waitUntilReady; pollStatus | onFailure retry notReadyYet after 1.0 s up to 5 times, readOrder",
         "giveUpQuickly; pollStatus | onFailure retry onceMore after 1.0 s up to 1 times",
         "readUnlessPreparing; checkStatus | onSuccess end stillPreparing when $response.body#/status == 'preparing' "
               + "| onFailure end unknownOrder, readOrder" })
   void eachStepShowsWhatItDoesOnSuccessAndOnFailure(String workflowId, String steps) throws Exception {
      ArazzoDocument document = orderTrackingIsImportedAndLinked();

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), workflowId)
            .orElseThrow();

      assertEquals(steps, String.join(", ", workflow.steps().stream().map(ArazzoStepActionsTest::actions).toList()));
   }

   /**
    * AC-02: the minion runs the document as imported and reports each attempt of the retried step at its own place in
    * the execution order, with the request and response of that attempt.
    */
   @Test
   void eachAttemptOfARetriedStepKeepsItsOwnSlotAndExchange() throws Exception {
      ArazzoDocument document = orderTrackingIsImportedAndLinked();
      WorkflowResult run = arazzoRunService.launchRun(document.getId(), "waitUntilReady", Map.of("orderId", "42"), null)
            .orElseThrow();

      for (String status : List.of("preparing", "preparing", "ready")) {
         int index = run.getSteps().size();
         run = arazzoRunService.recordStep(run.getId(), pollStatus(index, status), request(index), response(status))
               .orElseThrow();
      }

      assertEquals(document.getContent(), minion.requests().get(0).arazzoDocument());
      assertEquals(List.of("0 pollStatus FAILED", "1 pollStatus FAILED", "2 pollStatus PASSED"),
            run.getSteps().stream().map(step -> step.index() + " " + step.stepId() + " " + step.status()).toList());
      List<String> exchanges = new ArrayList<>();
      for (WorkflowStepResult step : run.getSteps()) {
         RequestResponsePair pair = messageService.getRequestResponseByTestCase(step.testCaseId()).get(0);
         exchanges.add(step.testCaseId().substring(run.getId().length()) + " " + pair.getRequest().getName() + " "
               + pair.getResponse().getContent());
      }
      assertEquals(List.of("-0 attempt 0 {\"status\":\"preparing\"}", "-1 attempt 1 {\"status\":\"preparing\"}",
            "-2 attempt 2 {\"status\":\"ready\"}"), exchanges);
   }

   /**
    * AC-03: the runs history names the step a run failed at once its retries are exhausted, and none for a run that
    * passed after failed attempts.
    */
   @Test
   void aRetriedStepFailsTheRunOnlyOnceItsRetriesAreExhausted() throws Exception {
      ArazzoDocument document = orderTrackingIsImportedAndLinked();
      runEnds(document, "waitUntilReady", List.of("preparing", "preparing", "ready", "readOrder"),
            WorkflowRunStatus.SUCCESS);
      runEnds(document, "giveUpQuickly", List.of("preparing", "preparing"), WorkflowRunStatus.FAILURE);
      runEnds(document, "waitUntilReady",
            List.of("preparing", "preparing", "preparing", "preparing", "preparing", "preparing", "readOrder"),
            WorkflowRunStatus.FAILURE);

      assertEquals(
            List.of("#3 waitUntilReady FAILURE at pollStatus", "#2 giveUpQuickly FAILURE at pollStatus",
                  "#1 waitUntilReady SUCCESS at null"),
            arazzoRunHistoryService.getRuns(document.getId(), WorkflowRunFilter.ALL, 0, 20).stream().map(
                  run -> "#" + run.number() + " " + run.workflowId() + " " + run.status() + " at " + run.failedStepId())
                  .toList());
   }

   private void runEnds(ArazzoDocument document, String workflowId, List<String> statuses, WorkflowRunStatus outcome)
         throws Exception {
      WorkflowResult run = arazzoRunService.launchRun(document.getId(), workflowId, Map.of("orderId", "42"), null)
            .orElseThrow();
      for (int index = 0; index < statuses.size(); index++) {
         arazzoRunService.recordStep(run.getId(), stepOf(index, statuses.get(index), outcome), null, null);
      }
      arazzoRunService.recordOutcome(run.getId(), outcome, Map.of(), 3000, null);
   }

   /** An attempt of pollStatus answering a status, or readOrder: passed after a passed run, else not run. */
   private static WorkflowStepResult stepOf(int index, String status, WorkflowRunStatus outcome) {
      if (!"readOrder".equals(status)) {
         return pollStatus(index, status);
      }
      return new WorkflowStepResult(index, "readOrder",
            new WorkflowStepOperation("orderStatusApi", "getOrder", "GET", "/order/{id}"),
            outcome == WorkflowRunStatus.SUCCESS ? WorkflowStepStatus.PASSED : WorkflowStepStatus.NOT_RUN, null, 0,
            List.of(), Map.of(), null, null, null, List.of(), Map.of());
   }

   private static WorkflowStepResult pollStatus(int index, String status) {
      return new WorkflowStepResult(index, "pollStatus", GET_ORDER_STATUS,
            "ready".equals(status) ? WorkflowStepStatus.PASSED : WorkflowStepStatus.FAILED, 200, 4, List.of(), Map.of(),
            null, null, null, List.of(), Map.of());
   }

   private static Request request(int attempt) {
      Request request = new Request();
      request.setName("attempt " + attempt);
      return request;
   }

   private static Response response(String status) {
      Response response = new Response();
      response.setStatus("200");
      response.setContent("{\"status\":\"" + status + "\"}");
      return response;
   }

   private static String actions(ArazzoStepDetail step) {
      List<String> parts = new ArrayList<>();
      parts.add(step.stepId());
      step.onSuccess().forEach(action -> parts.add("onSuccess " + describe(action)));
      step.onFailure().forEach(action -> parts.add("onFailure " + describe(action)));
      return String.join(" | ", parts);
   }

   private static String describe(ArazzoStepAction action) {
      String retry = action.retryAfter() == null ? ""
            : " after " + action.retryAfter() + " s up to " + action.retryLimit() + " times";
      String criteria = String.join(", ", action.criteria().stream().map(criterion -> criterion.condition()).toList());
      return action.type() + " " + action.name() + retry + (criteria.isEmpty() ? "" : " when " + criteria);
   }

   private ArazzoDocument orderTrackingIsImportedAndLinked() throws Exception {
      File contract = new File(ArazzoStepActionsTest.class.getResource(FIXTURES + "order-status.openapi.yaml").toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo("order-status.openapi.yaml", true));
      arazzoDocumentService.importArazzoSources(fixture("order-tracking.arazzo-sources.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture("order-tracking.arazzo.yaml"));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoStepActionsTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
