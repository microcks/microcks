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
package io.microcks.web;

import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.microcks.service.ArazzoMinionClient;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusMock;
import io.microcks.domain.ArazzoDocument;
import io.microcks.domain.WorkflowResult;
import io.microcks.domain.WorkflowRunStatus;
import io.microcks.service.ArazzoDocumentService;
import io.microcks.service.ArazzoRunService;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.RecordingMinionClient;
import io.microcks.service.ServiceService;
import io.microcks.web.dto.ArazzoRunLaunchDTO;
import io.microcks.web.dto.ArazzoRunOutcomeDTO;
import io.microcks.web.dto.ArazzoStepReportDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the run endpoints as the UI and the Arazzo minion call them, on the run use case with the minion at its
 * boundary.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunControllerTest {

   private final RecordingMinionClient minion = new RecordingMinionClient();

   /** The minion stays at the boundary: runs are recorded, not executed. */
   @BeforeEach
   void recordTheRunsHandedToTheMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Test
   void aLaunchedRunIsCreatedAtItsUrlThenRecordedAsTheMinionReportsIt() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      Response launched = controller.launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(Map.of("pastry", "Eclair Cafe", "newPrice", 2.6), 10000L));

      WorkflowResult run = (WorkflowResult) launched.getEntity();
      assertEquals(201, launched.getStatus());
      assertEquals(URI.create("/api/arazzo/runs/" + run.getId()), URI.create(launched.getLocation().getPath()));
      assertEquals(204, controller.reportStep(run.getId(), contract("passedStep", ArazzoStepReportDTO.class))
            .getStatus());
      assertEquals(204, controller.reportOutcome(run.getId(), contract("runOutcome", ArazzoRunOutcomeDTO.class))
            .getStatus());
      WorkflowResult reported = (WorkflowResult) controller.getRun(run.getId()).getEntity();
      assertEquals(WorkflowRunStatus.SUCCESS, reported.getStatus());
      assertEquals("readPastry " + run.getId() + "-0",
            reported.getSteps().get(0).stepId() + " " + reported.getSteps().get(0).testCaseId());
   }

   @Test
   void aWorkflowThatCannotRunIsABadRequestWithItsReason() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosAreImported("pastry-delete.arazzo.yaml");

      Response refused = controller.launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(Map.of(), null));

      assertEquals(400, refused.getStatus());
      assertEquals("Workflow readThenReprice cannot run: Operation DeletePastry not found in API Pastry - 2.0 2.0.0",
            refused.getEntity());
   }

   /** S8: a run missing an input is a bad request naming the input. */
   @Test
   void aRunMissingAnInputIsABadRequestNamingIt() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      Response refused = controller.launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(Map.of("pastry", "Eclair Cafe"), null));

      assertEquals(400, refused.getStatus());
      assertEquals("Missing input: newPrice", refused.getEntity());
   }

   @Test
   void anUnknownWorkflowOrRunIsNotFound() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      assertEquals(404, controller.launchRun(document.getId(), "unknown", new ArazzoRunLaunchDTO(Map.of(), null))
            .getStatus());
      assertEquals(404, controller.getRun("unknown").getStatus());
      assertEquals(404, controller.reportStep("unknown", contract("passedStep", ArazzoStepReportDTO.class))
            .getStatus());
      assertEquals(404, controller.reportOutcome("unknown", contract("runOutcome", ArazzoRunOutcomeDTO.class))
            .getStatus());
   }

   @Test
   void aRunIsLaunchedAgainstTheImplementationsGivenForItsSources() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      Response launched = controller.launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(Map.of("pastry", "Eclair Cafe", "newPrice", 2.6), null,
                  Map.of("pastryApi", "http://localhost:8080"), null));

      WorkflowResult run = (WorkflowResult) launched.getEntity();
      assertEquals(201, launched.getStatus());
      assertEquals("IMPLEMENTATIONS pastryApi http://localhost:8080",
            run.getTarget() + " " + run.getSources().get(0).sourceName() + " " + run.getSources().get(0).baseUrl());
   }

   @Test
   void aRunWithAnUnknownSecretIsABadRequestWithItsReason() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      Response refused = controller.launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(Map.of("pastry", "Eclair Cafe", "newPrice", 2.6), null,
                  Map.of("pastryApi", "http://localhost:8080"), "unknown-secret"));

      assertEquals(400, refused.getStatus());
      assertEquals("Secret 'unknown-secret' not found. Please ensure the secret exists before running the workflow.",
            refused.getEntity());
   }

   private static <T> T contract(String part, Class<T> type) throws IOException {
      return JSON.treeToValue(ArazzoMinionContractTest.contract().get(part), type);
   }

   private ArazzoDocument pastryScenariosAreImported(String fixture) throws Exception {
      File contract = new File(
            ArazzoRunControllerTest.class.getResource(FIXTURES + "api-pastry-2.0.openapi.yaml").toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo("api-pastry-2.0.openapi.yaml", true));
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture(fixture));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoRunControllerTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
