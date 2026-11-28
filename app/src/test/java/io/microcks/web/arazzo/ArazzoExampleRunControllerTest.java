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
package io.microcks.web.arazzo;

import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoExampleLaunch;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.service.arazzo.ArazzoDocumentService;
import io.microcks.service.arazzo.ArazzoMinionClient;
import io.microcks.service.arazzo.ArazzoRunService;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.arazzo.RecordingMinionClient;
import io.microcks.service.ServiceService;
import io.microcks.web.arazzo.dto.ArazzoRunLaunchDTO;

import org.junit.jupiter.api.Test;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the endpoints playing the recorded examples of a workflow, as the run dialog and a CI call them, on the run
 * use case with the minion at its boundary.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoExampleRunControllerTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String EXAMPLES = "examples.arazzo-examples.yaml";

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   private final RecordingMinionClient minion = new RecordingMinionClient();

   /** The minion stays at the boundary: runs are recorded, not executed. */
   @BeforeEach
   void recordTheRunsHandedToTheMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   @Test
   void aRunNamingAnExamplePlaysItWithItsInputs() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosWithExamples("recorded");

      Response launched = controller.launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(Map.of("pastry", "Ignored"), 10000L, null, null, null, null, "eclair-repriced"));

      WorkflowResult run = (WorkflowResult) launched.getEntity();
      assertEquals(201, launched.getStatus());
      assertEquals("eclair-repriced", run.getExample().name());
      assertEquals(Map.of("pastry", "Eclair Cafe", "newPrice", 2.6), minion.requests().get(0).inputs());
   }

   @Test
   void aRunNamingAnUnknownExampleIsABadRequestWithItsReason() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosWithExamples("recorded");

      Response refused = controller.launchRun(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(Map.of(), null, null, null, null, null, "eclair-flambe"));

      assertEquals(400, refused.getStatus());
      assertEquals("Workflow readThenReprice has no example eclair-flambe", refused.getEntity());
   }

   @Test
   void runningAllExamplesAnswersHowEachRunWasLaunched() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosWithExamples("missing-input");

      Response launched = controller.launchExampleRuns(document.getId(), "readThenReprice",
            new ArazzoRunLaunchDTO(null, 5000L, Map.of("pastryApi", "http://localhost:8080"), null, null, true, null));

      assertEquals(200, launched.getStatus());
      assertEquals(
            List.of("eclair-repriced 1 null", "millefeuille-repriced 2 null",
                  "eclair-without-price null Missing input: newPrice"),
            ((List<?>) launched.getEntity()).stream().map(ArazzoExampleLaunch.class::cast)
                  .map(launch -> launch.example() + " " + launch.number() + " " + launch.refusal()).toList());
      assertEquals(List.of("http://localhost:8080 true 5000", "http://localhost:8080 true 5000"),
            minion.requests().stream().map(request -> request.sources().get("pastryApi").baseUrl() + " "
                  + request.validateSchema() + " " + request.timeoutMS()).toList());
   }

   @Test
   void runningAllExamplesOfAnUnknownDocumentIsNotFoundAndOfAnUnknownWorkflowIsABadRequest() throws Exception {
      ArazzoRunController controller = new ArazzoRunController(arazzoRunService);
      ArazzoDocument document = pastryScenariosWithExamples("recorded");

      assertEquals(404, controller.launchExampleRuns("unknown", "readThenReprice", new ArazzoRunLaunchDTO(null, null))
            .getStatus());
      Response refused = controller.launchExampleRuns(document.getId(), "readTwice",
            new ArazzoRunLaunchDTO(null, null));
      assertEquals("400 text/plain Unknown workflow: readTwice",
            refused.getStatus() + " " + refused.getMediaType() + " " + refused.getEntity());
   }

   private ArazzoDocument pastryScenariosWithExamples(String examples) throws Exception {
      File contract = new File(
            ArazzoExampleRunControllerTest.class.getResource(FIXTURES + "api-pastry-2.0.openapi.yaml").toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo("api-pastry-2.0.openapi.yaml", true));
      ArazzoDocument document = arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      arazzoDocumentService.importArazzoExamples(exampleOf(examples));
      return document;
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoExampleRunControllerTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   private static String exampleOf(String example) throws IOException {
      String marker = "# example: " + example + " ";
      return Arrays.stream(fixture(EXAMPLES).split("\n---\n")).filter(document -> document.contains(marker)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + EXAMPLES));
   }
}
