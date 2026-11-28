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
import io.microcks.service.ServiceService;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.service.arazzo.view.ArazzoExampleLaunch;
import io.microcks.domain.workflow.WorkflowExampleCheck;
import io.microcks.domain.workflow.WorkflowExampleVerdict;
import io.microcks.domain.workflow.WorkflowResult;

import io.microcks.domain.workflow.WorkflowRunStatus;
import io.microcks.service.arazzo.view.WorkflowRunSummary;
import io.microcks.domain.workflow.WorkflowStepOperation;
import io.microcks.domain.workflow.WorkflowStepResult;
import io.microcks.domain.workflow.WorkflowStepStatus;
import io.microcks.repository.arazzo.WorkflowRunFilter;
import io.microcks.repository.arazzo.WorkflowResultRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of replaying the recorded examples of a workflow, entering through the run use case, with the Arazzo
 * minion standing at its boundary: the run keeps its own result, and the example gets its own verdict.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoExampleRunTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String EXAMPLES = "examples.arazzo-examples.yaml";
   private static final WorkflowStepOperation GET_PASTRIES = new WorkflowStepOperation("pastryApi", "GetPastries",
         "GET", "/pastry");
   private static final WorkflowStepOperation GET_PASTRY_BY_NAME = new WorkflowStepOperation("pastryApi",
         "GetPastryByName", "GET", "/pastry/{name}");
   private static final WorkflowStepOperation PATCH_PASTRY = new WorkflowStepOperation("pastryApi", "PatchPastry",
         "PATCH", "/pastry/{name}");

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

   /** The minion stays at the boundary: runs are recorded, not executed. */
   @BeforeEach
   void recordTheRunsHandedToTheMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   @Test
   void playingAnExampleRunsTheWorkflowWithItsInputsAndRecordsWhatItExpects() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");

      WorkflowResult run = play(document, "readThenReprice", "eclair-repriced");

      assertEquals(List.of(values("pastry", "Eclair Cafe", "newPrice", 2.6)),
            minion.requests().stream().map(ArazzoRunRequest::inputs).toList());
      WorkflowResult stored = workflowResultRepository.findByIdOptional(run.getId()).orElseThrow();
      assertEquals(values("pastry", "Eclair Cafe", "newPrice", 2.6), stored.getInputs());
      WorkflowExampleCheck example = stored.getExample();
      assertEquals("eclair-repriced", example.name());
      assertEquals(values("before", 2.5, "after", 2.6), example.outputs());
      assertNull(example.verdict());
      assertEquals(List.of(), example.unmetExpectations());
   }

   @Test
   void aRunWithoutExampleRecordsNone() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");

      WorkflowResult run = arazzoRunService
            .launchRun(document.getId(), "readThenReprice", values("pastry", "Eclair Cafe", "newPrice", 2.7), null)
            .orElseThrow();
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.SUCCESS, values("before", 2.5, "after", 2.7), 9,
            null);

      assertNull(arazzoRunService.getRun(run.getId()).orElseThrow().getExample());
   }

   @Test
   void aRunProducingTheExpectedOutputsMatchesTheExample() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult run = play(document, "readThenReprice", "eclair-repriced");

      repriced(run, 2.5, 2.6);

      WorkflowResult ended = arazzoRunService.getRun(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.SUCCESS, ended.getStatus());
      assertEquals(WorkflowExampleVerdict.MATCHES, ended.getExample().verdict());
      assertEquals(List.of(), ended.getExample().unmetExpectations());
   }

   @Test
   void anUnexpectedOutputDoesNotMatchTheExampleWhileTheRunKeepsItsResult() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult run = play(document, "readThenReprice", "eclair-repriced");

      repriced(run, 2.5, 2.7);

      WorkflowResult ended = arazzoRunService.getRun(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.SUCCESS, ended.getStatus());
      assertEquals(WorkflowExampleVerdict.DOES_NOT_MATCH, ended.getExample().verdict());
      assertEquals(List.of("Output after: expected 2.6 but was 2.7"), ended.getExample().unmetExpectations());
   }

   @Test
   void theOutputsAnExampleDoesNotListAreNotChecked() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult run = play(document, "readThenReprice", "millefeuille-repriced");

      repriced(run, 4.1, 4.4);

      assertEquals(WorkflowExampleVerdict.MATCHES,
            arazzoRunService.getRun(run.getId()).orElseThrow().getExample().verdict());
   }

   /** Outputs are compared as JSON values: a number matches the same number, however it is written. */
   @Test
   void numbersAreComparedByTheirValue() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("numbers");
      WorkflowResult run = play(document, "readThenReprice", "tartelette-repriced");

      repriced(run, 2, 3.0);

      WorkflowExampleCheck example = arazzoRunService.getRun(run.getId()).orElseThrow().getExample();
      assertEquals(List.of(), example.unmetExpectations());
      assertEquals(WorkflowExampleVerdict.MATCHES, example.verdict());
   }

   @Test
   void aWorkflowStoppingBeforeItsEndDoesNotMatchAnExampleExpectingItsOutputs() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult run = play(document, "readThenReprice", "eclair-repriced");

      arazzoRunService.recordStep(run.getId(), step(0, "readPastry", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200,
            values("name", "Eclair Cafe", "price", 2.5)), null, null);
      arazzoRunService.recordStep(run.getId(),
            step(1, "reprice", PATCH_PASTRY, WorkflowStepStatus.FAILED, 500, Map.of()), null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.FAILURE, Map.of(), 9, null);

      WorkflowResult ended = arazzoRunService.getRun(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, ended.getStatus());
      assertEquals(List.of("Expected to run to its end but stopped at reprice",
            "Output before: expected 2.5 but was absent", "Output after: expected 2.6 but was absent"),
            ended.getExample().unmetExpectations());
   }

   @Test
   void anExampleTheMinionDoesNotReceiveDoesNotMatch() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      minion.failWith(new ArazzoMinionException("The Arazzo minion cannot be reached at http://localhost:8082", null));

      WorkflowResult run = play(document, "listThenReadFirst", "unknown-first-pastry");

      WorkflowResult stored = workflowResultRepository.findByIdOptional(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, stored.getStatus());
      assertEquals(WorkflowExampleVerdict.DOES_NOT_MATCH, stored.getExample().verdict());
      assertEquals(List.of("Step list: expected to run but was not run",
            "Step readFirst: expected to run but was not run",
            "Expected to stop at readFirst but the run failed: The Arazzo minion cannot be reached at http://localhost:8082"),
            stored.getExample().unmetExpectations());
   }

   @Test
   void aWorkflowFailingExactlyAsTheExampleExpectsMatchesIt() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult run = play(document, "listThenReadFirst", "unknown-first-pastry");

      listedThenRead(run, "200 Baba Rhum", "404");

      WorkflowResult ended = arazzoRunService.getRun(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, ended.getStatus());
      assertEquals(WorkflowExampleVerdict.MATCHES, ended.getExample().verdict());
      assertEquals(List.of(), ended.getExample().unmetExpectations());
   }

   /**
    * The steps of listThenReadFirst as the minion reports them: the status and first pastry of list, or its status
    * alone when it failed; the status of readFirst, "none" when it got no response, "-" when it did not run.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "200 Baba Rhum | 400 | Step readFirst: expecting 404 but got 400",
         "200 Baba Rhum | none | Step readFirst: expecting 404 but got no response",
         "200 Divorces  | 404 | Step list output first: expected \"Baba Rhum\" but was \"Divorces\"",
         "200 Baba Rhum | 200 | Step readFirst: expecting 404 but got 200; Expected to stop at readFirst but the workflow ran to its end",
         "500           | -   | Step list: expecting 200 but got 500; Step list output first: expected \"Baba Rhum\" but was absent; "
               + "Step readFirst: expected to run but was not run; Expected to stop at readFirst but stopped at list" })
   void eachUnmetExpectationOfAStepIsGiven(String list, String readFirst, String unmet) throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult run = play(document, "listThenReadFirst", "unknown-first-pastry");

      listedThenRead(run, list, readFirst);

      WorkflowExampleCheck example = arazzoRunService.getRun(run.getId()).orElseThrow().getExample();
      assertEquals(WorkflowExampleVerdict.DOES_NOT_MATCH, example.verdict());
      assertEquals(Arrays.asList(unmet.split("; ")), example.unmetExpectations());
   }

   @Test
   void aStepCalledTwiceIsJudgedOnItsLastCall() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult run = play(document, "listThenReadFirst", "unknown-first-pastry");

      arazzoRunService.recordStep(run.getId(),
            step(0, "list", GET_PASTRIES, WorkflowStepStatus.PASSED, 200, Map.of("first", "Divorces")), null, null);
      arazzoRunService.recordStep(run.getId(),
            step(1, "list", GET_PASTRIES, WorkflowStepStatus.PASSED, 200, Map.of("first", "Baba Rhum")), null, null);
      arazzoRunService.recordStep(run.getId(),
            step(2, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, 404, Map.of()), null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.FAILURE, Map.of(), 9, null);

      assertEquals(WorkflowExampleVerdict.MATCHES,
            arazzoRunService.getRun(run.getId()).orElseThrow().getExample().verdict());
   }

   @Test
   void aRunStopsAtItsLastFailedStepWhenAnAttemptOfARetriedStepFailedBefore() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult run = play(document, "listThenReadFirst", "unknown-first-pastry");

      arazzoRunService.recordStep(run.getId(), step(0, "list", GET_PASTRIES, WorkflowStepStatus.FAILED, 503, Map.of()),
            null, null);
      arazzoRunService.recordStep(run.getId(),
            step(1, "list", GET_PASTRIES, WorkflowStepStatus.PASSED, 200, Map.of("first", "Baba Rhum")), null, null);
      arazzoRunService.recordStep(run.getId(),
            step(2, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, 404, Map.of()), null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.FAILURE, Map.of(), 9, null);

      WorkflowExampleCheck example = arazzoRunService.getRun(run.getId()).orElseThrow().getExample();
      assertEquals(WorkflowExampleVerdict.MATCHES, example.verdict());
      assertEquals(List.of(), example.unmetExpectations());
   }

   @Test
   void anExampleTheWorkflowDoesNotRecordIsRefused() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");

      ArazzoRunRefusedException refusal = assertThrows(ArazzoRunRefusedException.class, () -> arazzoRunService
            .launchExampleRun(document.getId(), "readThenReprice", "eclair-flambe", null, null, false));

      assertEquals("Workflow readThenReprice has no example eclair-flambe", refusal.getMessage());
      assertEquals(List.of(), minion.requests());
   }

   @Test
   void anExampleOfAnUnknownDocumentIsNotFound() throws Exception {
      pastryScenariosWithExamples("recorded");

      assertEquals(Optional.empty(),
            arazzoRunService.launchExampleRun("unknown", "readThenReprice", "eclair-repriced", null, null, false));
      assertEquals(Optional.empty(),
            arazzoRunService.launchExampleRuns("unknown", "readThenReprice", null, null, false));
      assertEquals(List.of(), minion.requests());
   }

   @Test
   void anExampleOfAnUnknownWorkflowIsRefusedNamingIt() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");

      ArazzoRunRefusedException refused = assertThrows(ArazzoRunRefusedException.class, () -> arazzoRunService
            .launchExampleRun(document.getId(), "readTwice", "eclair-repriced", null, null, false));
      ArazzoRunRefusedException refusedAll = assertThrows(ArazzoRunRefusedException.class,
            () -> arazzoRunService.launchExampleRuns(document.getId(), "readTwice", null, null, false));

      assertEquals("Unknown workflow: readTwice", refused.getMessage());
      assertEquals("Unknown workflow: readTwice", refusedAll.getMessage());
      assertEquals(List.of(), minion.requests());
   }

   @Test
   void runningAllExamplesLaunchesOneRunPerExampleAndRefusesAnExampleMissingAnInput() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("missing-input");

      List<ArazzoExampleLaunch> launches = arazzoRunService
            .launchExampleRuns(document.getId(), "readThenReprice", 5000L, null, false).orElseThrow();

      assertEquals(
            List.of("eclair-repriced: run 1", "millefeuille-repriced: run 2",
                  "eclair-without-price: refused, Missing input: newPrice"),
            launches.stream().map(ArazzoExampleRunTest::describe).toList());
      assertEquals(
            List.of(values("pastry", "Eclair Cafe", "newPrice", 2.6),
                  values("pastry", "Millefeuille", "newPrice", 4.5)),
            minion.requests().stream().map(ArazzoRunRequest::inputs).toList());
      assertEquals(List.of(5000L, 5000L), minion.requests().stream().map(ArazzoRunRequest::timeoutMS).toList());
      assertEquals(List.of("eclair-repriced", "millefeuille-repriced"),
            launches.stream().filter(launch -> launch.runId() != null)
                  .map(launch -> workflowResultRepository.findByIdOptional(launch.runId()).orElseThrow().getExample().name())
                  .toList());
   }

   @Test
   void aWorkflowWithoutExampleHasNoExampleToRun() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("numbers");

      assertEquals(Optional.of(List.of()),
            arazzoRunService.launchExampleRuns(document.getId(), "listThenReadFirst", null, null, false));
      assertEquals(List.of(), minion.requests());
   }

   @Test
   void theRunsHistoryShowsTheExampleEachRunPlayedAndItsVerdict() throws Exception {
      WorkflowDocument document = pastryScenariosWithExamples("recorded");
      WorkflowResult matching = play(document, "readThenReprice", "eclair-repriced");
      repriced(matching, 2.5, 2.6);
      WorkflowResult notMatching = play(document, "listThenReadFirst", "unknown-first-pastry");
      listedThenRead(notMatching, "200 Baba Rhum", "400");
      play(document, "readThenReprice", "millefeuille-repriced");
      arazzoRunService.launchRun(document.getId(), "listThenReadFirst", Map.of(), null);

      List<WorkflowRunSummary> runs = arazzoRunHistoryService.getRuns(document.getId(),
            new WorkflowRunFilter(null, null, null), 0, 10);

      assertEquals(List.of("#4 listThenReadFirst", "#3 readThenReprice millefeuille-repriced null",
            "#2 listThenReadFirst unknown-first-pastry DOES_NOT_MATCH", "#1 readThenReprice eclair-repriced MATCHES"),
            runs.stream().map(ArazzoExampleRunTest::describe).toList());
   }

   private WorkflowResult play(WorkflowDocument document, String workflowId, String exampleName)
         throws ArazzoRunRefusedException {
      return arazzoRunService.launchExampleRun(document.getId(), workflowId, exampleName, null, null, false)
            .orElseThrow();
   }

   /** readThenReprice read a pastry at a price, then repriced it. */
   private void repriced(WorkflowResult run, Object before, Object after) {
      arazzoRunService.recordStep(run.getId(),
            step(0, "readPastry", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200, Map.of("price", before)), null,
            null);
      arazzoRunService.recordStep(run.getId(),
            step(1, "reprice", PATCH_PASTRY, WorkflowStepStatus.PASSED, 200, Map.of("price", after)), null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.SUCCESS, values("before", before, "after", after),
            9, null);
   }

   private void listedThenRead(WorkflowResult run, String list, String readFirst) {
      String[] listed = list.strip().split(" ", 2);
      int listStatus = Integer.parseInt(listed[0]);
      boolean listPassed = listStatus == 200;
      arazzoRunService.recordStep(run.getId(),
            step(0, "list", GET_PASTRIES, listPassed ? WorkflowStepStatus.PASSED : WorkflowStepStatus.FAILED,
                  listStatus, listPassed ? Map.of("first", listed[1]) : Map.of()),
            null, null);
      boolean readFirstPassed = "200".equals(readFirst);
      arazzoRunService.recordStep(run.getId(), switch (readFirst) {
         case "-" -> step(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.NOT_RUN, null, Map.of());
         case "none" -> step(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, null, Map.of());
         default -> step(1, "readFirst", GET_PASTRY_BY_NAME,
               readFirstPassed ? WorkflowStepStatus.PASSED : WorkflowStepStatus.FAILED, Integer.parseInt(readFirst),
               Map.of());
      }, null, null);
      arazzoRunService.recordOutcome(run.getId(),
            readFirstPassed ? WorkflowRunStatus.SUCCESS : WorkflowRunStatus.FAILURE, Map.of(), 9, null);
   }

   private static WorkflowStepResult step(int index, String stepId, WorkflowStepOperation operation,
         WorkflowStepStatus status, Integer statusCode, Map<String, Object> outputs) {
      return new WorkflowStepResult(index, stepId, operation, status, statusCode, 5, List.of(), outputs, null, null,
            null, List.of(), Map.of());
   }

   private static Map<String, Object> values(String name, Object value, String otherName, Object otherValue) {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put(name, value);
      values.put(otherName, otherValue);
      return values;
   }

   private static String describe(ArazzoExampleLaunch launch) {
      return launch.example() + ": "
            + (launch.refusal() == null ? "run " + launch.number() : "refused, " + launch.refusal());
   }

   private static String describe(WorkflowRunSummary run) {
      return "#" + run.number() + " " + run.workflowId()
            + (run.example() == null ? "" : " " + run.example() + " " + run.exampleVerdict());
   }

   private WorkflowDocument pastryScenariosWithExamples(String examples) throws Exception {
      File contract = new File(
            ArazzoExampleRunTest.class.getResource(FIXTURES + "api-pastry-2.0.openapi.yaml").toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo("api-pastry-2.0.openapi.yaml", true));
      WorkflowDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      arazzoDocumentService.importArazzoExamples(exampleOf(examples));
      return document;
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoExampleRunTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   private static String exampleOf(String example) throws IOException {
      String marker = "# example: " + example + " ";
      return Arrays.stream(fixture(EXAMPLES).split("\n---\n")).filter(document -> document.contains(marker)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + EXAMPLES));
   }
}
