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

import io.microcks.domain.workflow.WorkflowCriterionResult;
import io.microcks.domain.workflow.WorkflowExpressionValue;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.domain.workflow.WorkflowRunOrigin;
import io.microcks.domain.workflow.WorkflowRunStatus;
import io.microcks.service.arazzo.view.WorkflowRunSummary;
import io.microcks.domain.workflow.WorkflowStepOperation;
import io.microcks.domain.workflow.WorkflowStepResult;
import io.microcks.domain.workflow.WorkflowStepStatus;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import io.microcks.repository.arazzo.WorkflowRunFilter;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance tests of the Mock URL of an Arazzo workflow, one per acceptance criterion of the story: an application
 * posts the inputs of a workflow, Microcks plays it on its mocks and answers with its outputs. The Arazzo minion stands
 * at the boundary and reports the steps and the outcome of each run it is handed.
 * @author SebastienDegodez
 */
@QuarkusTest
@TestProfile(ArazzoWorkflowMockUrlTest.WaitingAtMost600Ms.class)
class ArazzoWorkflowMockUrlTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String TITLE = "API Pastry scenarios";
   private static final String VERSION = "1.0.0";
   private static final String INPUTS = "{\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}";
   private static final WorkflowStepOperation GET_PASTRY_BY_NAME = new WorkflowStepOperation("pastryApi",
         "GetPastryByName", "GET", "/pastry/{name}");
   private static final WorkflowStepOperation PATCH_PASTRY = new WorkflowStepOperation("pastryApi", "PatchPastry",
         "PATCH", "/pastry/{name}");

   /** How long a call waits for the run to end, as configured with arazzo.mock-url.timeout. */
   public static class WaitingAtMost600Ms implements QuarkusTestProfile {

      @Override
      public Map<String, String> getConfigOverrides() {
         return Map.of("arazzo.mock-url.timeout", "600");
      }
   }

   @Inject
   private ArazzoMockUrlService arazzoMockUrlService;

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoRunHistoryService arazzoRunHistoryService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   private final RecordingMinionClient minion = new RecordingMinionClient();

   @BeforeEach
   void pastryScenariosAreImportedAndLinked() throws Exception {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
      File contract = new File(
            ArazzoWorkflowMockUrlTest.class.getResource(FIXTURES + "api-pastry-2.0.openapi.yaml").toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo("api-pastry-2.0.openapi.yaml", true));
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
   }

   /**
    * AC-02, domain example 1: the inputs posted to the Mock URL are played on the mocks, and the answer is exactly the
    * outputs of the workflow.
    */
   @Test
   void theWorkflowIsPlayedOnTheMocksAndAnswersExactlyItsOutputs() {
      minion.plays(request -> {
         arazzoRunService.recordStep(request.runId(), step(0, "readPastry", GET_PASTRY_BY_NAME,
               WorkflowStepStatus.PASSED, 200, List.of(), values("name", "Eclair Cafe", "price", 2.5)), null, null);
         arazzoRunService.recordStep(request.runId(),
               step(1, "reprice", PATCH_PASTRY, WorkflowStepStatus.PASSED, 200, List.of(), Map.of("price", 2.6)), null,
               null);
         arazzoRunService.recordOutcome(request.runId(), WorkflowRunStatus.SUCCESS, values("before", 2.5, "after", 2.6),
               21, null);
      });

      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(TITLE, VERSION, "readThenReprice", INPUTS);

      String runId = workflowResultRepository.listAll().get(0).getId();
      assertEquals(new ArazzoMockUrlAnswer(200, values("before", 2.5, "after", 2.6), runId, null, null), answer);
      ArazzoRunRequest played = minion.requests().get(0);
      assertEquals("readThenReprice {pastry=Eclair Cafe, newPrice=2.6} on /rest/API+Pastry+-+2.0/2.0.0",
            played.workflowId() + " " + played.inputs() + " on " + played.sources().get("pastryApi").baseUrl());
   }

   /** AC-02: a workflow that needs no input is played with no body. */
   @ParameterizedTest
   @CsvSource(value = { "''", "'  '", "{}" }, nullValues = "null")
   void aWorkflowWithoutInputsIsPlayedWithoutBody(String inputs) {
      minion.plays(request -> arazzoRunService.recordOutcome(request.runId(), WorkflowRunStatus.SUCCESS,
            Map.of("first", "Baba Rhum"), 5, null));

      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(TITLE, VERSION, "listThenReadFirst", inputs);

      assertEquals("200 {first=Baba Rhum} {}",
            answer.status() + " " + answer.outputs() + " " + minion.requests().get(0).inputs());
   }

   /**
    * AC-03, domain example 3: a step whose mock answered an error status fails the call with that status, any other
    * failure with 502, naming the run and the failing step.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "404; ; 404; Step reprice failed: unmet success criteria $statusCode == 200",
         "400; ; 400; Step reprice failed: unmet success criteria $statusCode == 200",
         "500; ; 500; Step reprice failed: unmet success criteria $statusCode == 200",
         "200; ; 502; Step reprice failed: unmet success criteria $statusCode == 200",
         "; Connection refused; 502; Step reprice failed: Connection refused" })
   void aFailingStepFailsTheCallWithTheStatusOfItsMockOr502(Integer statusCode, String message, int status,
         String reason) {
      WorkflowCriterionResult unmet = new WorkflowCriterionResult("$statusCode == 200",
            List.of(new WorkflowExpressionValue("$statusCode", statusCode)), false);
      WorkflowCriterionResult held = new WorkflowCriterionResult("$inputs.newPrice > 0",
            List.of(new WorkflowExpressionValue("$inputs.newPrice", 2.6)), true);
      minion.plays(request -> {
         arazzoRunService.recordStep(request.runId(), step(0, "readPastry", GET_PASTRY_BY_NAME,
               WorkflowStepStatus.PASSED, 200, List.of(), values("name", "Eclair Cafe", "price", 2.5)), null, null);
         arazzoRunService.recordStep(request.runId(),
               new WorkflowStepResult(1, "reprice", PATCH_PASTRY, WorkflowStepStatus.FAILED, statusCode, 5,
                     message == null ? List.of(held, unmet) : List.of(), Map.of(), null, message, null, List.of(),
                     Map.of()),
               null, null);
         arazzoRunService.recordOutcome(request.runId(), WorkflowRunStatus.FAILURE, Map.of(), 21, null);
      });

      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(TITLE, VERSION, "readThenReprice", INPUTS);

      String runId = workflowResultRepository.listAll().get(0).getId();
      assertEquals(new ArazzoMockUrlAnswer(status, null, runId, "reprice", reason), answer);
   }

   /**
    * AC-03: the failing step is the step that failed, not a later one that did not run; a step retried fails the call
    * with the status of its last attempt.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "readPastry; 404; NOT_RUN; ; readPastry; 404",
         "reprice; 503; FAILED; 404; reprice; 404" })
   void theFailingStepIsTheLastStepThatFailed(String firstStepId, Integer firstStatusCode, WorkflowStepStatus then,
         Integer thenStatusCode, String failedStep, int status) {
      minion.plays(request -> {
         arazzoRunService.recordStep(
               request.runId(), new WorkflowStepResult(0, firstStepId, PATCH_PASTRY, WorkflowStepStatus.FAILED,
                     firstStatusCode, 5, List.of(), Map.of(), null, "Not available", null, List.of(), Map.of()),
               null, null);
         arazzoRunService.recordStep(request.runId(), new WorkflowStepResult(1, "reprice", PATCH_PASTRY, then,
               thenStatusCode, 5, List.of(), Map.of(), null, "Not available", null, List.of(), Map.of()), null, null);
         arazzoRunService.recordOutcome(request.runId(), WorkflowRunStatus.FAILURE, Map.of(), 21, null);
      });

      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(TITLE, VERSION, "readThenReprice", INPUTS);

      assertEquals(status + " " + failedStep + ": Step " + failedStep + " failed: Not available",
            answer.status() + " " + answer.failedStep() + ": " + answer.reason());
   }

   /** AC-03: a run that fails before any step, such as a minion that cannot be reached, fails the call with 502. */
   @Test
   void aRunThatFailsBeforeAnyStepFailsTheCallWith502() {
      minion.failWith(new ArazzoMinionException("The Arazzo minion cannot be reached at http://localhost:8082", null));

      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(TITLE, VERSION, "readThenReprice", INPUTS);

      String runId = workflowResultRepository.listAll().get(0).getId();
      assertEquals(new ArazzoMockUrlAnswer(502, null, runId, null,
            "The Arazzo minion cannot be reached at http://localhost:8082"), answer);
   }

   /** AC-03: a run that does not end in time fails the call with 504, naming the run. */
   @Test
   void aRunThatDoesNotEndInTimeFailsTheCallWith504() {
      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(TITLE, VERSION, "readThenReprice", INPUTS);

      WorkflowResult run = workflowResultRepository.listAll().get(0);
      assertEquals(new ArazzoMockUrlAnswer(504, null, run.getId(), null, "Run #1 did not end within 600 ms"), answer);
      assertEquals(WorkflowRunStatus.IN_PROGRESS, run.getStatus());
   }

   /** AC-03, domain example 2: missing or invalid inputs are refused with 400 and the reason, and nothing runs. */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "{\"pastry\": \"Eclair Cafe\"}; Missing input: newPrice",
         "''; Missing inputs: pastry, newPrice", "[\"Eclair Cafe\", 2.6]; The inputs must be a JSON object",
         "pastry=Eclair Cafe; The inputs must be a JSON object" })
   void missingOrInvalidInputsAreRefusedWith400AndNothingRuns(String inputs, String reason) {
      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(TITLE, VERSION, "readThenReprice", inputs);

      assertEquals(new ArazzoMockUrlAnswer(400, null, null, null, reason), answer);
      assertEquals(0, workflowResultRepository.count());
      assertEquals(List.of(), minion.requests());
   }

   /** AC-04, domain example 4: an unknown document, version or workflow is refused with 404 and nothing runs. */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "API Pastry scenarios; 1.0.0; deleteAll; Unknown workflow: deleteAll",
         "API Pastry scenarios; 9.9.9; readThenReprice; Unknown Arazzo document: API Pastry scenarios 9.9.9",
         "Pastry scenarios; 1.0.0; readThenReprice; Unknown Arazzo document: Pastry scenarios 1.0.0" })
   void anUnknownDocumentVersionOrWorkflowIsRefusedWith404(String title, String version, String workflowId,
         String reason) {
      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(title, version, workflowId, INPUTS);

      assertEquals(new ArazzoMockUrlAnswer(404, null, null, null, reason), answer);
      assertEquals(0, workflowResultRepository.count());
      assertEquals(List.of(), minion.requests());
   }

   /** AC-05: the run is stored with its origin, the Mock URL, and listed so in the runs history. */
   @Test
   void theRunIsStoredWithTheMockUrlAsItsOriginAndListedSoInTheHistory() throws Exception {
      minion.plays(request -> arazzoRunService.recordOutcome(request.runId(), WorkflowRunStatus.SUCCESS,
            Map.of("first", "Baba Rhum"), 5, null));
      arazzoRunService.launchRun("API Pastry scenarios:1.0.0", "listThenReadFirst", Map.of(), null);

      arazzoMockUrlService.play(TITLE, VERSION, "listThenReadFirst", null);

      List<WorkflowRunSummary> runs = arazzoRunHistoryService.getRuns(
            workflowResultRepository.listAll().get(0).getDocumentId(), new WorkflowRunFilter(null, null, null), 0, 10);
      assertEquals(List.of("#2 MOCKS MOCK_URL", "#1 MOCKS null"),
            runs.stream().map(run -> "#" + run.number() + " " + run.target() + " " + run.origin()).toList());
      assertEquals(WorkflowRunOrigin.MOCK_URL, arazzoRunService.getRun(runs.get(0).id()).orElseThrow().getOrigin());
   }

   private static WorkflowStepResult step(int index, String stepId, WorkflowStepOperation operation,
         WorkflowStepStatus status, Integer statusCode, List<WorkflowCriterionResult> criteria,
         Map<String, Object> outputs) {
      return new WorkflowStepResult(index, stepId, operation, status, statusCode, 5, criteria, outputs, null, null,
            null, List.of(), Map.of());
   }

   private static Map<String, Object> values(String name, Object value, String otherName, Object otherValue) {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put(name, value);
      values.put(otherName, otherValue);
      return values;
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoWorkflowMockUrlTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
