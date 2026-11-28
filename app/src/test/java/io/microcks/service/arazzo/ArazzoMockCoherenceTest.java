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
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.domain.workflow.WorkflowCriterionResult;
import io.microcks.domain.workflow.WorkflowExpressionValue;
import io.microcks.domain.workflow.WorkflowMissingExample;
import io.microcks.domain.workflow.WorkflowParameterValue;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.domain.workflow.WorkflowRunStatus;
import io.microcks.domain.workflow.WorkflowRunTarget;
import io.microcks.domain.workflow.WorkflowSourceTarget;
import io.microcks.domain.workflow.WorkflowStepOperation;
import io.microcks.domain.workflow.WorkflowStepResult;
import io.microcks.domain.workflow.WorkflowStepStatus;
import io.microcks.repository.ServiceRepository;
import io.microcks.repository.workflow.WorkflowResultRepository;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Acceptance tests of spotting, in a run against the Microcks mocks, the step that failed because the mocks have no
 * example for what it sent, entering through the run use case as the Arazzo minion reports the steps.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoMockCoherenceTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String MOCKS = "http://localhost:8080/rest/API+Pastry+-+2.0/2.0.0";
   private static final WorkflowStepOperation GET_PASTRIES = new WorkflowStepOperation("pastryApi", "GetPastries",
         "GET", "/pastry");
   private static final WorkflowStepOperation GET_PASTRY_BY_NAME = new WorkflowStepOperation("pastryApi",
         "GetPastryByName", "GET", "/pastry/{name}");
   private static final WorkflowParameterValue BABA_RHUM_FROM_LIST = new WorkflowParameterValue("name", "path",
         "Baba Rhum", "$steps.list.outputs.first");
   private static final List<WorkflowCriterionResult> STATUS_WAS_400 = List.of(new WorkflowCriterionResult(
         "$statusCode == 200", List.of(new WorkflowExpressionValue("$statusCode", 400)), false));

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   private final RecordingMinionClient minion = new RecordingMinionClient();

   /** The minion stays at the boundary: runs are recorded, not executed. */
   @BeforeEach
   void recordTheRunsHandedToTheMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   @Test
   void aFailureCausedByAMissingExampleNamesTheOperationAndTheMissingValue() throws Exception {
      WorkflowResult run = launch("listThenReadFirst", Map.of());

      listReturnsBabaRhum(run);
      arazzoRunService.recordStep(run.getId(), readFirstFailsWith(BABA_RHUM_FROM_LIST), null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.FAILURE, Map.of(), 13, null);

      String serviceId = serviceRepository.findByNameAndVersion("API Pastry - 2.0", "2.0.0").getId();
      assertEquals(
            new WorkflowMissingExample(1, "readFirst", serviceId, "GetPastryByName", "GET /pastry/{name}",
                  List.of(BABA_RHUM_FROM_LIST)),
            arazzoRunService.getRun(run.getId()).orElseThrow().getMissingExample());
   }

   @Test
   void noHintForARunThatPassed() throws Exception {
      WorkflowResult run = launch("readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6));

      arazzoRunService
            .recordStep(run.getId(),
                  step(0, "readPastry", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200, List.of(),
                        List.of(new WorkflowParameterValue("name", "path", "Eclair Cafe", "$inputs.pastry"))),
                  null, null);
      arazzoRunService.recordStep(run.getId(),
            step(1, "reprice", new WorkflowStepOperation("pastryApi", "PatchPastry", "PATCH", "/pastry/{name}"),
                  WorkflowStepStatus.PASSED, 200, List.of(),
                  List.of(new WorkflowParameterValue("name", "path", "Eclair Cafe", "$steps.readPastry.outputs.name"))),
            null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.SUCCESS, Map.of("before", 2.5, "after", 2.6), 13,
            null);

      assertNull(arazzoRunService.getRun(run.getId()).orElseThrow().getMissingExample());
   }

   @Test
   void noExampleHintForARunAgainstAnImplementation() throws Exception {
      WorkflowResult run = launch("listThenReadFirst", Map.of());
      run.setTarget(WorkflowRunTarget.IMPLEMENTATIONS);
      workflowResultRepository.save(run);

      listReturnsBabaRhum(run);
      arazzoRunService.recordStep(run.getId(), readFirstFailsWith(BABA_RHUM_FROM_LIST), null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.FAILURE, Map.of(), 13, null);

      assertNull(arazzoRunService.getRun(run.getId()).orElseThrow().getMissingExample());
   }

   /**
    * Microcks finds the example of a call from the parameters its operation dispatches on: a failed step is missing an
    * example when no example of its operation has the values it sent for these parameters. Each operation of the
    * fixture has examples for Eclair Cafe only (S and fr for the query parameters).
    */
   @ParameterizedTest(name = "{0} sending {1}: {2}")
   @CsvSource(delimiter = '|', value = {
         // URI_PARTS: the path parameters of the dispatcher rules.
         "GetPastryByName    | name:path:Baba Rhum                 | name=Baba Rhum",
         "GetPastryByName    | name:path:Eclair Cafe               | none",
         "GetPastryByName    | name:query:Baba Rhum                | none",
         "GetPastryByName    | Accept:header:application/json      | none",
         "GetPastryByName    |                                     | none",
         // URI_PARAMS: the query parameters of the dispatcher rules.
         "GetPastriesBySize  | size:query:M;Accept:header:text/xml  | size=M",
         "GetPastriesBySize  | page:query:2;size:query:M           | size=M",
         "GetPastriesBySize  | size:query:S                        | none",
         "GetPastriesBySize  | size:path:M                         | none",
         // URI_ELEMENTS: both.
         "GetPastryReviews   | name:path:Eclair Cafe;lang:query:en | name=Eclair Cafe,lang=en",
         "GetPastryReviews   | name:path:Baba Rhum;lang:query:fr   | name=Baba Rhum,lang=fr",
         "GetPastryReviews   | name:path:Eclair Cafe;lang:query:fr | none",
         // A FALLBACK dispatches as the dispatcher it wraps before answering its fallback.
         "GetPastryPrice     | name:path:Baba Rhum                 | name=Baba Rhum",
         "GetPastryPrice     | name:path:Eclair Cafe               | none",
         // What a script, or no dispatcher, answers cannot be told from the parameters.
         "GetPastryStock     | name:path:Baba Rhum                 | none",
         "GetPastries        | size:query:M                        | none",
         // An operation, or a source, Microcks does not know.
         "GetPastryRecipe    | name:path:Baba Rhum                 | none",
         "unknownSource      | name:path:Baba Rhum                 | none" })
   void aFailedStepMissesAnExampleWhenNoExampleHasTheValuesItSentForWhatTheMocksDispatchOn(String operationId,
         String sent, String missing) throws Exception {
      String serviceId = pastryExamplesAreImported();
      WorkflowResult run = runAgainstTheMocksOfPastryExamples(serviceId);
      WorkflowStepOperation operation = pastryExamplesOperation(operationId);

      arazzoRunService.recordStep(run.getId(),
            step(0, "read", operation, WorkflowStepStatus.FAILED, 400, STATUS_WAS_400, parameters(sent)), null, null);

      WorkflowMissingExample hint = arazzoRunService.getRun(run.getId()).orElseThrow().getMissingExample();
      assertEquals(missing, hint == null ? "none"
            : hint.parameters().stream().map(p -> p.name() + "=" + p.value()).collect(Collectors.joining(",")));
   }

   @Test
   void theHintNamesTheOperationOfTheStepThatFailedOnTheApiItCalled() throws Exception {
      String serviceId = pastryExamplesAreImported();
      WorkflowResult run = runAgainstTheMocksOfPastryExamples(serviceId);

      arazzoRunService.recordStep(run.getId(), step(2, "reviews", pastryExamplesOperation("GetPastryReviews"),
            WorkflowStepStatus.FAILED, 400, STATUS_WAS_400, parameters("name:path:Baba Rhum;lang:query:fr")), null,
            null);

      WorkflowMissingExample hint = arazzoRunService.getRun(run.getId()).orElseThrow().getMissingExample();
      assertEquals(new WorkflowMissingExample(2, "reviews", serviceId, "GetPastryReviews", "GET /pastry/{name}/reviews",
            List.of(new WorkflowParameterValue("name", "path", "Baba Rhum", null),
                  new WorkflowParameterValue("lang", "query", "fr", null))),
            hint);
   }

   @Test
   void aStepThatPassedIsMissingNoExampleEvenWithoutAnExampleForWhatItSent() throws Exception {
      String serviceId = pastryExamplesAreImported();
      WorkflowResult run = runAgainstTheMocksOfPastryExamples(serviceId);

      arazzoRunService.recordStep(run.getId(), step(0, "price", pastryExamplesOperation("GetPastryPrice"),
            WorkflowStepStatus.PASSED, 200, List.of(), parameters("name:path:Baba Rhum")), null, null);

      assertNull(arazzoRunService.getRun(run.getId()).orElseThrow().getMissingExample());
   }

   private void listReturnsBabaRhum(WorkflowResult run) {
      arazzoRunService.recordStep(run.getId(),
            new WorkflowStepResult(0, "list", GET_PASTRIES, WorkflowStepStatus.PASSED, 200, 5, List.of(),
                  Map.of("first", "Baba Rhum"), null, null, MOCKS + "/pastry?size=S",
                  List.of(new WorkflowParameterValue("size", "query", "S", null)),
                  Map.of("first", "$response.body#/0/name")),
            null, null);
   }

   private static WorkflowStepResult readFirstFailsWith(WorkflowParameterValue name) {
      return new WorkflowStepResult(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, 400, 5,
            STATUS_WAS_400, Map.of(), null, null, MOCKS + "/pastry/Baba%20Rhum", List.of(name), Map.of());
   }

   private static WorkflowStepResult step(int index, String stepId, WorkflowStepOperation operation,
         WorkflowStepStatus status, int statusCode, List<WorkflowCriterionResult> criteria,
         List<WorkflowParameterValue> parameters) {
      return new WorkflowStepResult(index, stepId, operation, status, statusCode, 5, criteria, Map.of(), null, null,
            null, parameters, Map.of());
   }

   /** Parameters as name:in:value, separated by semicolons. */
   private static List<WorkflowParameterValue> parameters(String sent) {
      if (sent == null) {
         return List.of();
      }
      return Arrays.stream(sent.split(";")).map(parameter -> parameter.split(":", 3))
            .map(parts -> new WorkflowParameterValue(parts[0], parts[1], parts[2], null)).toList();
   }

   private static WorkflowStepOperation pastryExamplesOperation(String operationId) {
      Map<String, String> paths = Map.of("GetPastries", "/pastries", "GetPastriesBySize", "/pastries/sized",
            "GetPastryByName", "/pastry/{name}", "GetPastryReviews", "/pastry/{name}/reviews", "GetPastryPrice",
            "/pastry/{name}/price", "GetPastryStock", "/pastry/{name}/stock", "GetPastryRecipe",
            "/pastry/{name}/recipe", "unknownSource", "/pastry/{name}");
      String sourceName = "unknownSource".equals(operationId) ? "otherApi" : "examplesApi";
      return new WorkflowStepOperation(sourceName, operationId, "GET", paths.get(operationId));
   }

   /** A run of a workflow against the mocks of Pastry examples, its steps being reported by each test. */
   private WorkflowResult runAgainstTheMocksOfPastryExamples(String serviceId) {
      WorkflowResult run = new WorkflowResult();
      run.setWorkflowId("readExamples");
      run.setTarget(WorkflowRunTarget.MOCKS);
      run.setSources(List.of(new WorkflowSourceTarget("examplesApi", serviceId, "Pastry examples", "1.0.0",
            "/rest/Pastry+examples/1.0.0")));
      run.setStatus(WorkflowRunStatus.IN_PROGRESS);
      return workflowResultRepository.save(run);
   }

   private String pastryExamplesAreImported() throws Exception {
      apiIsImported("pastry-examples.openapi.yaml");
      return serviceRepository.findByNameAndVersion("Pastry examples", "1.0.0").getId();
   }

   private WorkflowResult launch(String workflowId, Map<String, Object> inputs) throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      WorkflowDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      return arazzoRunService.launchRun(document.getId(), workflowId, inputs, 10000L).orElseThrow();
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoMockCoherenceTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoMockCoherenceTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
