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
import io.microcks.domain.Header;
import io.microcks.domain.Request;
import io.microcks.domain.RequestResponsePair;
import io.microcks.domain.Response;
import io.microcks.domain.WorkflowCriterionResult;
import io.microcks.domain.WorkflowExpressionValue;
import io.microcks.domain.WorkflowParameterValue;
import io.microcks.domain.WorkflowResult;
import io.microcks.domain.WorkflowRunStatus;
import io.microcks.domain.WorkflowRunTarget;
import io.microcks.domain.WorkflowSourceTarget;
import io.microcks.domain.WorkflowStepOperation;
import io.microcks.domain.WorkflowStepResult;
import io.microcks.domain.WorkflowStepStatus;
import io.microcks.repository.ServiceRepository;
import io.microcks.repository.WorkflowResultRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of running an Arazzo workflow against the Microcks mocks, entering through the run use case, with
 * the Arazzo minion standing at its boundary: what Microcks hands it, and what it reports back.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String MOCKS = "/rest/API+Pastry+-+2.0/2.0.0";
   private static final String MICROCKS = "http://localhost:8080";
   private static final WorkflowStepOperation GET_PASTRY_BY_NAME = new WorkflowStepOperation("pastryApi",
         "GetPastryByName", "GET", "/pastry/{name}");

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private MessageService messageService;

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
   void aRunnableWorkflowIsHandedToTheMinionAgainstTheMocksOfItsLinkedApis() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");

      WorkflowResult run = launch(document, "readThenReprice", pastryInputs(), 5000L);

      String serviceId = serviceRepository.findByNameAndVersion("API Pastry - 2.0", "2.0.0").getId();
      assertEquals(
            List.of(new ArazzoRunRequest(run.getId(), "readThenReprice", document.getContent(), pastryInputs(),
                  Map.of("pastryApi",
                        new ArazzoRunRequest.Source(MOCKS,
                              Map.of("GetPastries", new ArazzoRunRequest.Operation("GET", "/pastry"), "GetPastryByName",
                                    new ArazzoRunRequest.Operation("GET", "/pastry/{name}"), "PatchPastry",
                                    new ArazzoRunRequest.Operation("PATCH", "/pastry/{name}")))),
                  5000L)),
            minion.requests());
      WorkflowResult stored = workflowResultRepository.findByIdOptional(run.getId()).orElseThrow();
      assertEquals("API Pastry scenarios 1.0.0 readThenReprice " + document.getId(), stored.getDocumentTitle() + " "
            + stored.getDocumentVersion() + " " + stored.getWorkflowId() + " " + stored.getDocumentId());
      assertEquals(WorkflowRunTarget.MOCKS, stored.getTarget());
      assertEquals(List.of(new WorkflowSourceTarget("pastryApi", serviceId, "API Pastry - 2.0", "2.0.0", MOCKS)),
            stored.getSources());
      assertEquals(pastryInputs(), stored.getInputs());
      assertEquals(5000L, stored.getTimeout());
      assertEquals(WorkflowRunStatus.IN_PROGRESS, stored.getStatus());
      assertEquals(List.of(), stored.getSteps());
   }

   @Test
   void aJourneyConsistentWithTheMocksPassesAndExposesItsOutputs() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "readThenReprice", pastryInputs(), 10000L);

      arazzoRunService.recordStep(run.getId(), step(0, "readPastry", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200,
            outputs("name", "Eclair Cafe", "price", 2.5)), null, null);
      arazzoRunService.recordStep(run.getId(),
            step(1, "reprice", new WorkflowStepOperation("pastryApi", "PatchPastry", "PATCH", "/pastry/{name}"),
                  WorkflowStepStatus.PASSED, 200, Map.of("price", 2.6)),
            null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.SUCCESS, outputs("before", 2.5, "after", 2.6), 13,
            null);

      WorkflowResult passed = arazzoRunService.getRun(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.SUCCESS, passed.getStatus());
      assertEquals(List.of("0 readPastry PASSED", "1 reprice PASSED"), described(passed));
      assertEquals(Map.of("before", 2.5, "after", 2.6), passed.getOutputs());
      assertEquals(13, passed.getElapsedTime());
   }

   @Test
   void theRunStopsAtTheFirstFailingStepAndNamesTheUnmetCriterion() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);
      List<WorkflowCriterionResult> unmet = List.of(new WorkflowCriterionResult("$statusCode == 200",
            List.of(new WorkflowExpressionValue("$statusCode", 400)), false));

      arazzoRunService.recordStep(run.getId(),
            step(0, "list", new WorkflowStepOperation("pastryApi", "GetPastries", "GET", "/pastry"),
                  WorkflowStepStatus.PASSED, 200, Map.of("first", "Baba Rhum")),
            null, null);
      arazzoRunService.recordStep(run.getId(), new WorkflowStepResult(1, "readFirst", GET_PASTRY_BY_NAME,
            WorkflowStepStatus.FAILED, 400, 5, unmet, Map.of(), null, null, null, List.of(), Map.of()), null, null);
      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.FAILURE, Map.of(), 13, null);

      WorkflowResult failed = arazzoRunService.getRun(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, failed.getStatus());
      assertEquals(List.of("0 list PASSED", "1 readFirst FAILED"), described(failed));
      assertEquals(unmet, failed.getSteps().get(1).criteria());
      assertEquals(Map.of("first", "Baba Rhum"), failed.getSteps().get(0).outputs());
   }

   @Test
   void theFailingStepShowsWhatWasSentAndReceived() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);
      Request request = new Request();
      request.setName("readFirst");
      Response response = new Response();
      response.setName("readFirst");
      response.setStatus("400");
      response.setContent("The response /name=Baba Rhum does not exist!");

      arazzoRunService.recordStep(run.getId(),
            new WorkflowStepResult(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, 400, 5, List.of(),
                  Map.of(), null, null, MICROCKS + MOCKS + "/pastry/Baba%20Rhum", List.of(), Map.of()),
            request, response);

      WorkflowStepResult readFirst = arazzoRunService.getRun(run.getId()).orElseThrow().getSteps().get(0);
      assertEquals("GET " + MICROCKS + MOCKS + "/pastry/Baba%20Rhum 400",
            readFirst.operation().method() + " " + readFirst.requestUrl() + " " + readFirst.statusCode());
      Response received = messageService.getRequestResponseByTestCase(readFirst.testCaseId()).get(0).getResponse();
      assertEquals("400 The response /name=Baba Rhum does not exist!",
            received.getStatus() + " " + received.getContent());
   }

   @Test
   void valuesPassedBetweenStepsShowTheirOrigin() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);
      List<WorkflowParameterValue> sent = List.of(
            new WorkflowParameterValue("name", "path", "Baba Rhum", "$steps.list.outputs.first"),
            new WorkflowParameterValue("Accept", "header", "application/json", null));

      arazzoRunService.recordStep(run.getId(),
            new WorkflowStepResult(0, "list", new WorkflowStepOperation("pastryApi", "GetPastries", "GET", "/pastry"),
                  WorkflowStepStatus.PASSED, 200, 5, List.of(), Map.of("first", "Baba Rhum"), null, null,
                  MICROCKS + MOCKS + "/pastry", List.of(), Map.of("first", "$response.body#/0/name")),
            null, null);
      arazzoRunService.recordStep(
            run.getId(), new WorkflowStepResult(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, 400, 5,
                  List.of(), Map.of(), null, null, MICROCKS + MOCKS + "/pastry/Baba%20Rhum", sent, Map.of()),
            null, null);

      List<WorkflowStepResult> steps = arazzoRunService.getRun(run.getId()).orElseThrow().getSteps();
      assertEquals(Map.of("first", "$response.body#/0/name"), steps.get(0).outputOrigins());
      assertEquals(sent, steps.get(1).parameters());
   }

   @Test
   void aRunInProgressBecomesFinalWhenTheMinionReportsItsEnd() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "readThenReprice", pastryInputs(), 10000L);

      arazzoRunService.recordStep(run.getId(),
            step(0, "readPastry", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200, Map.of()), null, null);
      assertEquals(WorkflowRunStatus.IN_PROGRESS, arazzoRunService.getRun(run.getId()).orElseThrow().getStatus());

      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.SUCCESS, Map.of(), 13, null);
      assertEquals(WorkflowRunStatus.SUCCESS, arazzoRunService.getRun(run.getId()).orElseThrow().getStatus());
   }

   @Test
   void aRunThatFailsWithoutAFailingStepKeepsTheReason() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);

      arazzoRunService.recordOutcome(run.getId(), WorkflowRunStatus.FAILURE, Map.of(), 8,
            "Unknown output of step list: first");

      assertEquals("Unknown output of step list: first",
            arazzoRunService.getRun(run.getId()).orElseThrow().getMessage());
   }

   @Test
   void stepsKeepTheOrderTheMinionGivesThemWhateverTheOrderTheirReportsArrive() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);

      arazzoRunService.recordStep(run.getId(),
            step(2, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.NOT_RUN, null, Map.of()), null, null);
      arazzoRunService.recordStep(run.getId(),
            step(0, "list", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200, Map.of()), null, null);
      arazzoRunService.recordStep(run.getId(),
            step(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200, Map.of()), null, null);
      arazzoRunService.recordStep(run.getId(),
            step(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, 400, Map.of()), null, null);

      assertEquals(List.of("0 list PASSED", "1 readFirst FAILED", "2 readFirst NOT_RUN"),
            described(arazzoRunService.getRun(run.getId()).orElseThrow()));
   }

   @Test
   void theExchangeOfAStepIsStoredUnderTheRunAndTheIndexOfTheStep() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);
      Request request = new Request();
      request.setName("readFirst");
      request.setHeaders(Set.of(new Header("Accept", Set.of("application/json"))));
      Response response = new Response();
      response.setName("readFirst");
      response.setStatus("400");
      response.setContent("The response /name=Baba Rhum does not exist!");

      WorkflowResult recorded = arazzoRunService.recordStep(run.getId(),
            step(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, 400, Map.of()), request, response)
            .orElseThrow();

      String testCaseId = run.getId() + "-1";
      assertEquals(testCaseId, recorded.getSteps().get(0).testCaseId());
      List<RequestResponsePair> exchanges = messageService.getRequestResponseByTestCase(testCaseId);
      assertEquals(1, exchanges.size());
      assertEquals("readFirst Accept", exchanges.get(0).getRequest().getName() + " "
            + exchanges.get(0).getRequest().getHeaders().iterator().next().getName());
      assertEquals("400 The response /name=Baba Rhum does not exist!",
            exchanges.get(0).getResponse().getStatus() + " " + exchanges.get(0).getResponse().getContent());
   }

   @Test
   void aStepWithoutResponseKeepsItsRequest() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);
      Request request = new Request();
      request.setName("list");

      WorkflowResult recorded = arazzoRunService.recordStep(run.getId(),
            step(0, "list", GET_PASTRY_BY_NAME, WorkflowStepStatus.FAILED, null, Map.of()), request, null)
            .orElseThrow();

      assertEquals(run.getId() + "-0", recorded.getSteps().get(0).testCaseId());
      assertEquals(List.of(), messageService.getRequestResponseByTestCase(run.getId() + "-0"));
   }

   @Test
   void aStepThatSentNothingHasNoExchange() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);

      WorkflowResult recorded = arazzoRunService
            .recordStep(run.getId(),
                  step(1, "readFirst", GET_PASTRY_BY_NAME, WorkflowStepStatus.NOT_RUN, null, Map.of()), null, null)
            .orElseThrow();

      assertNull(recorded.getSteps().get(0).testCaseId());
   }

   @Test
   void aWorkflowThatCannotRunIsRefusedWithItsReasonAndNotHandedToTheMinion() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-delete.arazzo.yaml");

      ArazzoRunRefusedException refused = assertThrows(ArazzoRunRefusedException.class,
            () -> arazzoRunService.launchRun(document.getId(), "readThenReprice", Map.of(), 10000L));

      assertEquals("Workflow readThenReprice cannot run: Operation DeletePastry not found in API Pastry - 2.0 2.0.0",
            refused.getMessage());
      assertEquals(List.of(), workflowResultRepository.listAll());
      assertEquals(List.of(), minion.requests());
   }

   /** S8 AC-01: a run missing an input is refused, naming the input, and no call is made to the mocks. */
   @Test
   void aRunMissingAnInputIsRefusedNamingItAndNoCallIsMadeToTheMocks() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");

      ArazzoRunRefusedException refused = assertThrows(ArazzoRunRefusedException.class, () -> arazzoRunService
            .launchRun(document.getId(), "readThenReprice", Map.of("pastry", "Eclair Cafe"), 10000L));

      assertEquals("Missing input: newPrice", refused.getMessage());
      assertEquals(List.of(), workflowResultRepository.listAll());
      assertEquals(List.of(), minion.requests());
   }

   /**
    * S8: every missing input is named, the inputs the workflow declares first, whether its steps or outputs use them or
    * its inputs schema requires them. An input given as null is missing.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "pastry-scenarios.arazzo.yaml; readThenReprice; ; Missing inputs: pastry, newPrice",
         "pastry-scenarios.arazzo.yaml; readThenReprice; newPrice=2.6; Missing input: pastry",
         "pastry-scenarios.arazzo.yaml; readThenReprice; pastry=Eclair Cafe|newPrice=null; Missing input: newPrice",
         "pastry-needed-inputs.arazzo.yaml; readAndTell; comment=Fine; Missing inputs: pastry, note, label",
         "pastry-needed-inputs.arazzo.yaml; readAndTell; pastry=Eclair Cafe|label=Tasty; Missing input: note" })
   void everyMissingInputIsNamedInAStableOrder(String fixture, String workflowId, String given, String reason)
         throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked(fixture);

      ArazzoRunRefusedException refused = assertThrows(ArazzoRunRefusedException.class,
            () -> arazzoRunService.launchRun(document.getId(), workflowId, inputs(given), 10000L));

      assertEquals(reason, refused.getMessage());
      assertEquals(List.of(), workflowResultRepository.listAll());
      assertEquals(List.of(), minion.requests());
   }

   /** S8 AC-02: a workflow that uses no input runs without any. */
   @Test
   void aWorkflowWithoutInputsRunsWithoutInputs() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");

      WorkflowResult withNoInput = launch(document, "listThenReadFirst", Map.of(), 10000L);
      WorkflowResult withoutInputs = launch(document, "listThenReadFirst", null, 10000L);

      assertEquals(List.of(WorkflowRunStatus.IN_PROGRESS, WorkflowRunStatus.IN_PROGRESS),
            List.of(withNoInput.getStatus(), withoutInputs.getStatus()));
      assertEquals(List.of(Map.of(), Map.of()), minion.requests().stream().map(ArazzoRunRequest::inputs).toList());
   }

   @Test
   void anUnknownDocumentHasNoRun() throws Exception {
      pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");

      assertEquals(Optional.empty(), arazzoRunService.launchRun("unknown", "readThenReprice", Map.of(), 10000L));
      assertEquals(List.of(), minion.requests());
   }

   @Test
   void anUnknownWorkflowIsRefusedNamingIt() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");

      ArazzoRunRefusedException refused = assertThrows(ArazzoRunRefusedException.class,
            () -> arazzoRunService.launchRun(document.getId(), "unknown", Map.of(), 10000L));

      assertEquals("Unknown workflow: unknown", refused.getMessage());
      assertEquals(List.of(), minion.requests());
   }

   @Test
   void onlyTheSourcesLinkedToAnApiAreTargeted() throws Exception {
      apiIsImported("customer-api.openapi.yaml");
      arazzoDocumentService.importArazzoSources(fixture("order-fulfilment.arazzo-sources.yaml"));
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));

      WorkflowResult run = launch(document, "searchThenRead", Map.of("name", "Jane"), 10000L);

      assertEquals(List.of("customerApi /rest/Customer+API/1.0.0"),
            run.getSources().stream().map(source -> source.sourceName() + " " + source.baseUrl()).toList());
      assertEquals(Set.of("customerApi"), minion.requests().get(0).sources().keySet());
   }

   @Test
   void aRunWithoutTimeoutWaitsTenSecondsPerStep() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");

      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), null);

      assertEquals(10000L, run.getTimeout());
      assertEquals(10000L, minion.requests().get(0).timeoutMS());
   }

   @Test
   void aRunTheMinionDoesNotReceiveFailsWithTheReason() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked("pastry-scenarios.arazzo.yaml");
      minion.failWith(new ArazzoMinionException("The Arazzo minion cannot be reached at http://localhost:8082", null));
      Date before = new Date();

      WorkflowResult run = launch(document, "listThenReadFirst", Map.of(), 10000L);

      WorkflowResult stored = workflowResultRepository.findByIdOptional(run.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, stored.getStatus());
      assertEquals("The Arazzo minion cannot be reached at http://localhost:8082", stored.getMessage());
      assertTrue(!stored.getStartedDate().before(before), () -> stored.getStartedDate() + " is before " + before);
   }

   @Test
   void theReportsOfAnUnknownRunAreNotRecorded() {
      assertEquals(Optional.empty(), arazzoRunService.recordStep("unknown",
            step(0, "list", GET_PASTRY_BY_NAME, WorkflowStepStatus.PASSED, 200, Map.of()), null, null));
      assertEquals(Optional.empty(),
            arazzoRunService.recordOutcome("unknown", WorkflowRunStatus.SUCCESS, Map.of(), 1, null));
      assertEquals(Optional.empty(), arazzoRunService.getRun("unknown"));
   }

   private WorkflowResult launch(ArazzoDocument document, String workflowId, Map<String, Object> inputs, Long timeout)
         throws ArazzoRunRefusedException {
      return arazzoRunService.launchRun(document.getId(), workflowId, inputs, timeout).orElseThrow();
   }

   private static WorkflowStepResult step(int index, String stepId, WorkflowStepOperation operation,
         WorkflowStepStatus status, Integer statusCode, Map<String, Object> outputs) {
      return new WorkflowStepResult(index, stepId, operation, status, statusCode, 5, List.of(), outputs, null, null,
            null, List.of(), Map.of());
   }

   private static List<String> described(WorkflowResult run) {
      return run.getSteps().stream().map(step -> step.index() + " " + step.stepId() + " " + step.status()).toList();
   }

   /** Inputs written as name=value, separated by |; the value null stands for an input given as null. */
   private static Map<String, Object> inputs(String given) {
      Map<String, Object> inputs = new LinkedHashMap<>();
      if (given != null) {
         for (String input : given.split("\\|")) {
            String[] nameAndValue = input.split("=");
            inputs.put(nameAndValue[0], "null".equals(nameAndValue[1]) ? null : nameAndValue[1]);
         }
      }
      return inputs;
   }

   private static Map<String, Object> pastryInputs() {
      return outputs("pastry", "Eclair Cafe", "newPrice", 2.6);
   }

   private static Map<String, Object> outputs(String name, Object value, String otherName, Object otherValue) {
      Map<String, Object> values = new LinkedHashMap<>();
      values.put(name, value);
      values.put(otherName, otherValue);
      return values;
   }

   private ArazzoDocument pastryScenariosAreImportedAndLinked(String fixture) throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture(fixture));
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoRunTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoRunTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
