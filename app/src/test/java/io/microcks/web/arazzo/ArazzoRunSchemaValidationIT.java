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

import io.microcks.web.AbstractBaseIT;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSourcesRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;

import io.microcks.service.arazzo.ArazzoMinionClient;
import io.microcks.service.arazzo.HttpArazzoMinionClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Running an Arazzo workflow with its responses validated against their OpenAPI schema, from the webapp, through the
 * Arazzo minion, to Microcks mocks whose PatchPastry example does not conform to the Pastry schema, and back. The
 * examples are in {@code schema-validation.yaml}.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunSchemaValidationIT extends AbstractBaseIT {

   private static final Path FIXTURE = Path
         .of("target/test-classes/io/microcks/service/arazzo/" + "schema-validation.yaml");
   private static final Path UPLOADS = Path.of("target/schema-validation");
   private static final ArazzoMinionProcess MINION = new ArazzoMinionProcess();
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoSourcesRepository arazzoSourcesRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   /** The runs are handed to the minion at its URL, as configured with arazzo-minion.url. */
   @BeforeEach
   void handTheRunsToTheMinionAtItsUrl() {
      QuarkusMock.installMockForType(new HttpArazzoMinionClient(MINION.url()), ArazzoMinionClient.class);
   }

   @BeforeEach
   void startTheMinionAgainstThisWebapp() throws Exception {
      if (!MINION.isStarted()) {
         MINION.start(getServerUrl());
      }
   }

   @AfterEach
   void deleteDocumentsAndRuns() {
      arazzoDocumentRepository.deleteAll();
      arazzoSourcesRepository.deleteAll();
      workflowResultRepository.deleteAll();
   }

   @AfterAll
   static void stopTheMinion() {
      MINION.stop();
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "true  | FAILURE | 0 readPastry PASSED; 1 reprice FAILED /price does not match the Pastry schema: "
               + "string found, number expected",
         "false | SUCCESS | 0 readPastry PASSED; 1 reprice PASSED" })
   void aNonConformantResponseOfTheMocksFailsItsStepOnlyWhenTheResponsesAreValidated(boolean validateSchema,
         String status, String steps) throws Exception {
      upload("api-pastry-non-conformant", true);
      upload("pastry-journeys", true);
      upload("pastry-journeys-sources", false);
      String documentId = null;
      String documents = restTemplate.getForEntity("/api/arazzo/documents", String.class).getBody();
      for (JsonNode document : JSON.readTree(documents)) {
         if ("API Pastry journeys".equals(document.get("title").asText())) {
            documentId = document.get("id").asText();
         }
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      ResponseEntity<String> launched = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/readThenReprice/runs",
            new HttpEntity<>("{\"inputs\": {\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}, \"timeout\": 10000, "
                  + "\"validateSchema\": " + validateSchema + "}", headers),
            String.class);
      assertEquals(201, launched.getStatusCode().value(), launched.getBody());

      JsonNode run = awaitFinalRun(JSON.readTree(launched.getBody()).get("id").asText());

      assertEquals(status, run.get("status").asText(), run.toPrettyString());
      assertEquals(validateSchema, run.get("validateSchema").asBoolean());
      List<String> described = new ArrayList<>();
      run.get("steps").forEach(step -> described.add((step.get("index").asInt() + " " + step.get("stepId").asText()
            + " " + step.get("status").asText() + " " + step.path("message").asText("")).trim()));
      assertEquals(Arrays.asList(steps.split("; ")), described);
   }

   /** Upload an example of the fixture as an artifact of its own. */
   private void upload(String example, boolean mainArtifact) throws Exception {
      String marker = "# example: " + example + " ";
      String document = Arrays.stream(Files.readString(FIXTURE, StandardCharsets.UTF_8).split("\n---\n"))
            .filter(candidate -> candidate.startsWith(marker)).findFirst().orElseThrow();
      Files.createDirectories(UPLOADS);
      Path file = UPLOADS.resolve(example + ".yaml");
      Files.writeString(file, document.substring(document.indexOf('\n') + 1), StandardCharsets.UTF_8);
      uploadArtifactFile(file.toString(), mainArtifact);
   }

   private JsonNode awaitFinalRun(String runId) throws Exception {
      JsonNode run = null;
      for (int attempt = 0; attempt < 100; attempt++) {
         run = JSON.readTree(restTemplate.getForEntity("/api/arazzo/runs/" + runId, String.class).getBody());
         if (!"IN_PROGRESS".equals(run.get("status").asText())) {
            return run;
         }
         Thread.sleep(200);
      }
      return run;
   }
}
