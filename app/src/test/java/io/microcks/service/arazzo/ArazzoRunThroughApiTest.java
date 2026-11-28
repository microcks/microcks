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
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.arazzo.WorkflowCriterionResult;
import io.microcks.domain.arazzo.WorkflowExpressionValue;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.domain.arazzo.WorkflowRunStatus;
import io.microcks.domain.arazzo.WorkflowStepOperation;
import io.microcks.domain.arazzo.WorkflowStepResult;
import io.microcks.domain.arazzo.WorkflowStepStatus;
import io.microcks.repository.arazzo.WorkflowResultRepository;

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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of running an Arazzo workflow against an implementation from a CI pipeline, entering through the run
 * use cases the Microcks API publishes, with the Arazzo minion standing at their boundary: the pipeline addresses the
 * document by its name and version, launches a run, then reads it until it is no longer in progress.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunThroughApiTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   /** How a CI pipeline names API Pastry scenarios 1.0.0, as it names a Microcks API: name:version. */
   private static final String PASTRY_SCENARIOS = "API Pastry scenarios:1.0.0";
   private static final String PASTRY_APP = "http://pastry-app:8080";
   private static final WorkflowStepOperation GET_PASTRY_BY_NAME = new WorkflowStepOperation("pastryApi",
         "GetPastryByName", "GET", "/pastry/{name}");
   private static final WorkflowStepOperation PATCH_PASTRY = new WorkflowStepOperation("pastryApi", "PatchPastry",
         "PATCH", "/pastry/{name}");

   @Inject
   private ArazzoRunService arazzoRunService;

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

   @BeforeEach
   void pastryScenariosAreImportedAndLinked() throws Exception {
      File contract = new File(
            ArazzoRunThroughApiTest.class.getResource(FIXTURES + "api-pastry-2.0.openapi.yaml").toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo("api-pastry-2.0.openapi.yaml", true));
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
   }

   /** AC-01: a run launched through the API passes, and its verdict and outputs can be read. */
   @Test
   void aRunLaunchedThroughTheApiPassesAndItsVerdictAndOutputsCanBeRead() throws Exception {
      String runId = launchReadThenRepriceAgainstPastryApp().getId();

      arazzoRunService.recordStep(runId, step(0, "readPastry", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200,
            List.of(), values("name", "Eclair Cafe", "price", 2.5)), null, null);
      arazzoRunService.recordStep(runId,
            step(1, "reprice", PATCH_PASTRY, WorkflowStepStatus.PASSED, 200, List.of(), Map.of("price", 2.6)), null,
            null);
      arazzoRunService.recordOutcome(runId, WorkflowRunStatus.SUCCESS, values("before", 2.5, "after", 2.6), 21, null);

      WorkflowResult read = arazzoRunService.getRun(runId).orElseThrow();
      assertEquals(WorkflowRunStatus.SUCCESS, read.getStatus());
      assertEquals(Map.of("before", 2.5, "after", 2.6), read.getOutputs());
      assertEquals("readThenReprice pastryApi " + PASTRY_APP, read.getWorkflowId() + " "
            + read.getSources().get(0).sourceName() + " " + read.getSources().get(0).baseUrl());
   }

   /** AC-02: a failing implementation is reported with its failing step and reason. */
   @Test
   void aFailingImplementationIsReportedWithItsFailingStepAndReason() throws Exception {
      String runId = launchReadThenRepriceAgainstPastryApp().getId();
      WorkflowCriterionResult unmet = new WorkflowCriterionResult("$statusCode == 200",
            List.of(new WorkflowExpressionValue("$statusCode", 500)), false);

      arazzoRunService.recordStep(runId, step(0, "readPastry", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200,
            List.of(), values("name", "Eclair Cafe", "price", 2.5)), null, null);
      arazzoRunService.recordStep(runId,
            step(1, "reprice", PATCH_PASTRY, WorkflowStepStatus.FAILED, 500, List.of(unmet), Map.of()), null, null);
      arazzoRunService.recordOutcome(runId, WorkflowRunStatus.FAILURE, Map.of(), 21, null);

      WorkflowResult read = arazzoRunService.getRun(runId).orElseThrow();
      WorkflowStepResult failing = read.getSteps().stream().filter(step -> step.status() == WorkflowStepStatus.FAILED)
            .findFirst().orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, read.getStatus());
      assertEquals("reprice 500", failing.stepId() + " " + failing.statusCode());
      assertEquals(List.of(unmet), failing.criteria().stream().filter(criterion -> !criterion.held()).toList());
   }

   /** AC-03: an unknown workflow is refused without creating a run. */
   @Test
   void anUnknownWorkflowIsRefusedWithoutCreatingARun() {
      ArazzoRunRefusedException refused = assertThrows(ArazzoRunRefusedException.class,
            () -> arazzoRunService.launchRun(PASTRY_SCENARIOS, "deleteAll", Map.of(), null,
                  new ArazzoRunImplementations(Map.of("pastryApi", PASTRY_APP), null), false));

      assertEquals("Unknown workflow: deleteAll", refused.getMessage());
      assertEquals(0, workflowResultRepository.count());
      assertEquals(List.of(), minion.requests());
   }

   /** AC-04: a run still executing says so. */
   @Test
   void aRunStillExecutingIsReportedInProgress() throws Exception {
      String runId = launchReadThenRepriceAgainstPastryApp().getId();

      arazzoRunService.recordStep(runId, step(0, "readPastry", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200,
            List.of(), values("name", "Eclair Cafe", "price", 2.5)), null, null);

      assertEquals(WorkflowRunStatus.IN_PROGRESS, arazzoRunService.getRun(runId).orElseThrow().getStatus());
   }

   /** The pipeline names a document as it names a Microcks API: name:version, with + standing for a space. */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "API Pastry scenarios:1.0.0 | true", "API+Pastry+scenarios:1.0.0 | true",
         "API Pastry scenarios:9.9.9 | false", "Unknown scenarios:1.0.0 | false" })
   void aDocumentIsFoundByItsNameAndVersion(String document, boolean found) throws Exception {
      Optional<WorkflowResult> run = arazzoRunService.launchRun(document, "listThenReadFirst", Map.of(), null);

      assertEquals(found, run.isPresent());
      assertEquals(found ? 1 : 0, minion.requests().size());
   }

   private WorkflowResult launchReadThenRepriceAgainstPastryApp() throws ArazzoRunRefusedException {
      return arazzoRunService
            .launchRun(PASTRY_SCENARIOS, "readThenReprice", values("pastry", "Eclair Cafe", "newPrice", 2.6), null,
                  new ArazzoRunImplementations(Map.of("pastryApi", PASTRY_APP), null), false)
            .orElseThrow();
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
      try (InputStream stream = ArazzoRunThroughApiTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
