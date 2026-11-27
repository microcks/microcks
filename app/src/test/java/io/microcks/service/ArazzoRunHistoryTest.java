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

import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.microcks.service.ArazzoMinionClient;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusMock;
import io.microcks.domain.ArazzoDocument;
import io.microcks.domain.ArazzoWorkflowSummary;
import io.microcks.domain.TestResult;
import io.microcks.domain.WorkflowResult;
import io.microcks.domain.WorkflowRunCounts;
import io.microcks.domain.WorkflowRunStatus;
import io.microcks.domain.WorkflowRunSummary;
import io.microcks.domain.WorkflowRunTarget;
import io.microcks.repository.ServiceRepository;
import io.microcks.repository.TestResultRepository;
import io.microcks.repository.WorkflowResultRepository;
import io.microcks.repository.WorkflowRunFilter;
import io.microcks.util.ObjectMapperFactory;

import com.fasterxml.jackson.databind.MappingIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance tests of the runs history of the workflows of an Arazzo document, entering through the history use case on
 * the runs of <i>API Pastry scenarios</i>: #1 passed <code>readThenReprice</code> on the mocks, #2 failed
 * <code>readThenReprice</code> on the implementations and #3 failed <code>listThenReadFirst</code> on the mocks.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunHistoryTest {

   private final RecordingMinionClient minion = new RecordingMinionClient();

   /** The minion stays at the boundary: runs are recorded, not executed. */
   @BeforeEach
   void recordTheRunsHandedToTheMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String PASTRY_SCENARIOS = "API Pastry scenarios";

   @Inject
   private ArazzoRunHistoryService arazzoRunHistoryService;

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private TestResultRepository testResultRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   @Test
   void runsAreListedNewestFirstWithTheirOutcome() throws Exception {
      runsOfPastryScenariosAre(PASTRY_SCENARIOS);

      List<WorkflowRunSummary> runs = arazzoRunHistoryService.getRuns(PASTRY_SCENARIOS, WorkflowRunFilter.ALL, 0, 20);

      assertEquals(List.of("#3 listThenReadFirst MOCKS FAILURE at readFirst 15 ms",
            "#2 readThenReprice IMPLEMENTATIONS FAILURE at reprice 58 ms",
            "#1 readThenReprice MOCKS SUCCESS at null 24 ms"), described(runs));
      assertEquals(
            List.of(Date.from(Instant.parse("2026-10-21T10:00:00Z")), Date.from(Instant.parse("2026-10-21T09:00:00Z")),
                  Date.from(Instant.parse("2026-10-20T09:00:00Z"))),
            runs.stream().map(WorkflowRunSummary::startedDate).toList());
      assertEquals(workflowResultRepository.listAll().stream()
            .filter(run -> PASTRY_SCENARIOS.equals(run.getDocumentId()) && run.getNumber() == 3L).findFirst()
            .orElseThrow().getId(), runs.get(0).id());
   }

   @Test
   void runsCanBeFilteredByResult() throws Exception {
      runsOfPastryScenariosAre(PASTRY_SCENARIOS);

      List<WorkflowRunSummary> runs = arazzoRunHistoryService.getRuns(PASTRY_SCENARIOS,
            new WorkflowRunFilter(null, null, WorkflowRunStatus.FAILURE), 0, 20);

      assertEquals(List.of(3L, 2L), numbers(runs));
   }

   @Test
   void filtersCombine() throws Exception {
      runsOfPastryScenariosAre(PASTRY_SCENARIOS);

      List<WorkflowRunSummary> runs = arazzoRunHistoryService.getRuns(PASTRY_SCENARIOS,
            new WorkflowRunFilter("readThenReprice", WorkflowRunTarget.IMPLEMENTATIONS, null), 0, 20);

      assertEquals(List.of(2L), numbers(runs));
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', textBlock = """
         passed                        |                   |                 | SUCCESS | 1
         on the mocks                  |                   | MOCKS           |         | 3,1
         listThenReadFirst             | listThenReadFirst |                 |         | 3
         readThenReprice for its runs  | readThenReprice   |                 |         | 2,1
         readThenReprice failed, mocks | readThenReprice   | MOCKS           | FAILURE | ''
         in progress                   |                   |                 | IN_PROGRESS | ''
         """)
   void eachFilterKeepsOnlyTheRunsItMatches(String filtered, String workflowId, WorkflowRunTarget target,
         WorkflowRunStatus result, String expectedNumbers) throws Exception {
      runsOfPastryScenariosAre(PASTRY_SCENARIOS);

      List<WorkflowRunSummary> runs = arazzoRunHistoryService.getRuns(PASTRY_SCENARIOS,
            new WorkflowRunFilter(workflowId, target, result), 0, 20);

      assertEquals(expectedNumbers, String.join(",", numbers(runs).stream().map(String::valueOf).toList()));
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', textBlock = """
         every run       |                 |                 |         | 3 | 1 | 2
         failed          |                 |                 | FAILURE | 2 | 0 | 2
         passed          |                 |                 | SUCCESS | 1 | 1 | 0
         readThenReprice | readThenReprice |                 |         | 2 | 1 | 1
         implementations |                 | IMPLEMENTATIONS |         | 1 | 0 | 1
         """)
   void theHistoryCountsTheRunsItListsWithThoseThatPassedAndFailed(String filtered, String workflowId,
         WorkflowRunTarget target, WorkflowRunStatus result, long total, long passed, long failed) throws Exception {
      runsOfPastryScenariosAre(PASTRY_SCENARIOS);

      WorkflowRunCounts counts = arazzoRunHistoryService.countRuns(PASTRY_SCENARIOS,
            new WorkflowRunFilter(workflowId, target, result));

      assertEquals(new WorkflowRunCounts(total, passed, failed), counts);
   }

   @Test
   void theHistoryIsReadPageByPage() throws Exception {
      runsOfPastryScenariosAre(PASTRY_SCENARIOS);

      assertEquals(List.of(3L, 2L),
            numbers(arazzoRunHistoryService.getRuns(PASTRY_SCENARIOS, WorkflowRunFilter.ALL, 0, 2)));
      assertEquals(List.of(1L),
            numbers(arazzoRunHistoryService.getRuns(PASTRY_SCENARIOS, WorkflowRunFilter.ALL, 1, 2)));
   }

   @Test
   void runsAreNumberedPerDocumentWhenLaunchedIndependentlyOfTheTestsOfTheirApis() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();
      runsOfPastryScenariosAre("another document");
      TestResult apiTest = new TestResult();
      apiTest.setServiceId(serviceRepository.findByNameAndVersion("API Pastry - 2.0", "2.0.0").getId());
      apiTest.setTestNumber(7L);
      testResultRepository.save(apiTest);

      WorkflowResult first = arazzoRunService
            .launchRun(document.getId(), "readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6), null)
            .orElseThrow();
      WorkflowResult second = arazzoRunService.launchRun(document.getId(), "listThenReadFirst", Map.of(), null)
            .orElseThrow();

      assertEquals(List.of(1L, 2L), List.of(first.getNumber(), second.getNumber()));
      assertEquals(List.of(2L, 1L),
            numbers(arazzoRunHistoryService.getRuns(document.getId(), WorkflowRunFilter.ALL, 0, 20)));
   }

   @Test
   void eachWorkflowOfTheWorkflowsListGivesItsLastRun() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();
      runsOfPastryScenariosAre(document.getId());

      List<ArazzoWorkflowSummary> workflows = arazzoDocumentService.getDocumentSummaries(null).get(0).workflows();

      assertEquals(
            List.of("readThenReprice FAILED: #2 readThenReprice IMPLEMENTATIONS FAILURE at reprice 58 ms",
                  "listThenReadFirst FAILED: #3 listThenReadFirst MOCKS FAILURE at readFirst 15 ms"),
            workflows.stream().map(workflow -> workflow.status().workflowId() + " " + workflow.lastResult() + ": "
                  + described(List.of(workflow.lastRun())).get(0)).toList());
   }

   @ParameterizedTest(name = "a last run {0} gives the last result {1}")
   @CsvSource({ "SUCCESS, PASSED", "FAILURE, FAILED", "IN_PROGRESS, IN_PROGRESS" })
   void theLastResultOfAWorkflowIsHowItsLastRunWent(WorkflowRunStatus status, String lastResult) throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();
      WorkflowResult run = new WorkflowResult();
      run.setDocumentId(document.getId());
      run.setWorkflowId("listThenReadFirst");
      run.setNumber(1L);
      run.setStatus(status);
      workflowResultRepository.save(run);

      List<ArazzoWorkflowSummary> workflows = arazzoDocumentService.getDocumentSummaries(null).get(0).workflows();

      assertEquals(List.of("readThenReprice NEVER_RUN null", "listThenReadFirst " + lastResult + " 1"),
            workflows.stream().map(workflow -> workflow.status().workflowId() + " " + workflow.lastResult() + " "
                  + (workflow.lastRun() == null ? null : workflow.lastRun().number())).toList());
   }

   private void runsOfPastryScenariosAre(String documentId) throws IOException {
      try (MappingIterator<WorkflowResult> runs = ObjectMapperFactory.getYamlObjectMapper()
            .readerFor(WorkflowResult.class).readValues(fixture("pastry-scenarios.runs.yaml"))) {
         for (WorkflowResult run : runs.readAll()) {
            if (PASTRY_SCENARIOS.equals(run.getDocumentId())) {
               run.setDocumentId(documentId);
            }
            workflowResultRepository.save(run);
         }
      }
   }

   private static List<String> described(List<WorkflowRunSummary> runs) {
      return runs.stream().map(run -> "#" + run.number() + " " + run.workflowId() + " " + run.target() + " "
            + run.status() + " at " + run.failedStepId() + " " + run.elapsedTime() + " ms").toList();
   }

   private static List<Long> numbers(List<WorkflowRunSummary> runs) {
      return runs.stream().map(WorkflowRunSummary::number).toList();
   }

   private ArazzoDocument pastryScenariosAreImportedAndLinked() throws Exception {
      File contract = new File(
            ArazzoRunHistoryTest.class.getResource(FIXTURES + "api-pastry-2.0.openapi.yaml").toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo("api-pastry-2.0.openapi.yaml", true));
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoRunHistoryTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
