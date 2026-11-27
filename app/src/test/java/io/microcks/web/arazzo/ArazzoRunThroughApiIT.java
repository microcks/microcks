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
import io.quarkus.test.junit.QuarkusTest;
import io.microcks.service.arazzo.ArazzoMinionClient;
import io.microcks.service.arazzo.HttpArazzoMinionClient;
import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSourcesRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.HttpMethod;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A CI pipeline runs a workflow of API Pastry scenarios 1.0.0 against the implementation of API Pastry it deployed,
 * through the Microcks API: it launches the run, then reads it until it is no longer in progress. The Arazzo minion
 * really executes the run, and every request and response is checked against the OpenAPI description Microcks publishes
 * of its API.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunThroughApiIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final String LAUNCH = "/arazzo/documents/{documentId}/workflows/{workflowId}/runs";
   private static final String RUN = "/arazzo/runs/{runId}";
   private static final String PASTRY_SCENARIOS = "API Pastry scenarios:1.0.0";
   private static final MicrocksApiDescription MICROCKS_API = MicrocksApiDescription.version("1.16");
   private static final ArazzoMinionProcess MINION = new ArazzoMinionProcess();
   private static final PastryApp PASTRY_APP = new PastryApp();
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
   void pastryScenariosAreImportedAndLinked() throws Exception {
      if (!MINION.isStarted()) {
         MINION.start(getServerUrl());
      }
      uploadArtifactFile(FIXTURES + "api-pastry-2.0.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry-scenarios.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo-sources.yaml", false);
   }

   @AfterEach
   void deleteDocumentsAndRuns() {
      PASTRY_APP.reset();
      arazzoDocumentRepository.deleteAll();
      arazzoSourcesRepository.deleteAll();
      workflowResultRepository.deleteAll();
   }

   @AfterAll
   static void stopTheMinionAndTheImplementation() {
      MINION.stop();
      PASTRY_APP.stop();
   }

   /** AC-01: a run launched through the API passes, and its verdict and outputs can be read. */
   @Test
   void aRunLaunchedThroughTheApiPassesAndItsVerdictAndOutputsCanBeRead() throws Exception {
      ResponseEntity<String> launched = launch("readThenReprice", readThenRepriceAgainstPastryApp());

      assertEquals(201, launched.getStatusCode().value(), launched.getBody());
      String runId = JSON.readTree(launched.getBody()).get("id").asText();
      assertEquals("/api/arazzo/runs/" + runId, java.net.URI.create(launched.getHeaders().getFirst("Location")).getPath());
      JsonNode run = awaitFinalRun(runId);
      assertEquals("SUCCESS", run.get("status").asText(), run.toPrettyString());
      assertEquals(JSON.readTree("{\"before\": 2.5, \"after\": 2.6}"), run.get("outputs"));
   }

   /** AC-02: a failing implementation is reported with its failing step and reason. */
   @Test
   void aFailingImplementationIsReportedWithItsFailingStepAndReason() throws Exception {
      PASTRY_APP.failReprice();

      ResponseEntity<String> launched = launch("readThenReprice", readThenRepriceAgainstPastryApp());

      JsonNode run = awaitFinalRun(JSON.readTree(launched.getBody()).get("id").asText());
      assertEquals("FAILURE", run.get("status").asText(), run.toPrettyString());
      JsonNode reprice = run.get("steps").get(1);
      assertEquals("reprice FAILED 500", reprice.get("stepId").asText() + " " + reprice.get("status").asText() + " "
            + reprice.get("statusCode").asInt(), run.toPrettyString());
      JsonNode unmet = reprice.get("criteria").get(0);
      assertEquals(List.of("$statusCode == 200", "false", "500"), List.of(unmet.get("condition").asText(),
            unmet.get("held").asText(), unmet.get("values").get(0).get("value").asText()), run.toPrettyString());
   }

   /** AC-03: an unknown workflow is refused without creating a run. */
   @Test
   void anUnknownWorkflowIsRefusedWithoutCreatingARun() throws Exception {
      ResponseEntity<String> refused = launch("deleteAll", "{\"inputs\": {}}");

      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Unknown workflow: deleteAll", refused.getBody());
      assertEquals(0, workflowResultRepository.count());
   }

   /** AC-04: a run still executing says so. */
   @Test
   void aRunStillExecutingIsReportedInProgress() throws Exception {
      PASTRY_APP.holdReads();

      ResponseEntity<String> launched = launch("readThenReprice", readThenRepriceAgainstPastryApp());

      String runId = JSON.readTree(launched.getBody()).get("id").asText();
      assertEquals("IN_PROGRESS", read(runId).get("status").asText());
      PASTRY_APP.releaseReads();
      assertEquals("SUCCESS", awaitFinalRun(runId).get("status").asText());
   }

   private static String readThenRepriceAgainstPastryApp() {
      return "{\"inputs\": {\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}, \"timeout\": 10000, "
            + "\"implementations\": {\"pastryApi\": \"" + PASTRY_APP.url() + "\"}}";
   }

   /** Launch a run as a CI pipeline does, checking the request and the response against the published API. */
   private ResponseEntity<String> launch(String workflowId, String body) throws Exception {
      MICROCKS_API.assertRequestConforms("POST", LAUNCH, body);
      ResponseEntity<String> response = restTemplate.exchange(
            "/api" + LAUNCH.replace("{documentId}", java.net.URLEncoder.encode(PASTRY_SCENARIOS, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20")).replace("{workflowId}", workflowId),
            HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("POST", LAUNCH, response);
      return response;
   }

   /** Read a run as a CI pipeline does, checking the response against the published API. */
   private JsonNode read(String runId) throws Exception {
      ResponseEntity<String> response = restTemplate.exchange("/api" + RUN.replace("{runId}", runId), HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("GET", RUN, response);
      return JSON.readTree(response.getBody());
   }

   private JsonNode awaitFinalRun(String runId) throws Exception {
      JsonNode run = null;
      for (int attempt = 0; attempt < 100; attempt++) {
         run = read(runId);
         if (!"IN_PROGRESS".equals(run.get("status").asText())) {
            return run;
         }
         Thread.sleep(200);
      }
      return run;
   }

   private static HttpHeaders jsonHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));
      return headers;
   }
}
