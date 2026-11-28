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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.HttpMethod;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end replay of the recorded examples of a workflow: the ArazzoMetadata and ArazzoExamples uploaded, every
 * example played in one call as a CI does, through the Arazzo minion against mocks answering 404 for an unknown pastry, then
 * each run judged on its example. The requests and responses are checked against the published Microcks API.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoExamplesIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final ArazzoMinionProcess MINION = new ArazzoMinionProcess();
   private static final ObjectMapper JSON = new ObjectMapper();
   private static final MicrocksApiDescription MICROCKS_API = MicrocksApiDescription.version("2.0");
   private static final String LAUNCH = "/workflows/documents/{documentId}/workflows/{workflowId}/runs";
   private static final String LAUNCH_EXAMPLES = "/workflows/documents/{documentId}/workflows/{workflowId}"
         + "/examples/runs";
   private static final String RUN = "/workflows/runs/{runId}";

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

   @AfterAll
   static void stopTheMinion() {
      MINION.stop();
   }

   @Test
   void everyExampleOfAWorkflowIsReplayedInOneCallAndJudgedOnWhatItExpects() throws Exception {
      String documentId = pastryExamplesAreImported();

      ResponseEntity<String> launched = post(LAUNCH_EXAMPLES, documentId, "listThenReadFirst", "{\"timeout\": 10000}");

      assertEquals(200, launched.getStatusCode().value(), launched.getBody());
      List<String> verdicts = new ArrayList<>();
      for (JsonNode launch : JSON.readTree(launched.getBody())) {
         JsonNode run = awaitFinalRun(launch.get("runId").asText());
         verdicts.add(launch.get("example").asText() + ": run " + run.get("status").asText() + ", "
               + run.get("example").get("verdict").asText() + " " + run.get("example").get("unmetExpectations"));
      }
      assertEquals(List.of("unknown-first-pastry: run FAILURE, MATCHES []",
            "first-pastry-read: run FAILURE, DOES_NOT_MATCH [\"Step readFirst: expecting 200 but got 404\","
                  + "\"Expected to run to its end but stopped at readFirst\","
                  + "\"Output price: expected 3.2 but was absent\"]"),
            verdicts);
      List<String> history = new ArrayList<>();
      JSON.readTree(restTemplate.getForEntity("/api/workflows/documents/" + documentId + "/runs", String.class)
            .getBody())
            .forEach(run -> history.add(run.get("example").asText() + " " + run.get("exampleVerdict").asText()));
      assertEquals(List.of("first-pastry-read DOES_NOT_MATCH", "unknown-first-pastry MATCHES"), history);
   }

   @Test
   void aRunNamingAnExamplePlaysItAndTheExamplesAreKeptWhenTheSourcesAreLinkedAgain() throws Exception {
      String documentId = pastryExamplesAreImported();

      ResponseEntity<String> relinked = restTemplate.postForEntity("/api/workflows/documents",
            Map.of("content",
                  Files.readString(Path.of(FIXTURES + "recorded-examples.arazzo.yaml"), StandardCharsets.UTF_8),
                  "fileName", "recorded-examples.arazzo.yaml", "links",
                  Map.of("pastryApi", Map.of("name", "Pastry Examples API", "version", "1.0.0"))),
            String.class);
      assertEquals(201, relinked.getStatusCode().value(), relinked.getBody());
      ResponseEntity<String> launched = post(LAUNCH, documentId, "listThenReadFirst",
            "{\"example\": \"unknown-first-pastry\", \"timeout\": 10000}");

      assertEquals(201, launched.getStatusCode().value(), launched.getBody());
      JsonNode run = awaitFinalRun(JSON.readTree(launched.getBody()).get("id").asText());
      assertEquals("unknown-first-pastry MATCHES",
            run.get("example").get("name").asText() + " " + run.get("example").get("verdict").asText());
   }

   private String pastryExamplesAreImported() throws Exception {
      uploadArtifactFile(FIXTURES + "recorded-examples.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "recorded-examples.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "recorded-examples.arazzo-metadata.yaml", false);
      uploadArtifactFile(FIXTURES + "recorded-examples.arazzo-examples.yaml", false);
      return JSON.readTree(restTemplate.getForEntity("/api/workflows/documents", String.class).getBody()).get(0).get(
            "id").asText();
   }

   /** Launch runs as a CI pipeline does, checking the request and the response against the published API. */
   private ResponseEntity<String> post(String path, String documentId, String workflowId, String body)
         throws Exception {
      MICROCKS_API.assertRequestConforms("POST", path, body);
      ResponseEntity<String> response = restTemplate.exchange(
            "/api" + path.replace("{documentId}", documentId).replace("{workflowId}", workflowId), HttpMethod.POST,
            json(body), String.class);
      MICROCKS_API.assertResponseConforms("POST", path, response);
      return response;
   }

   private static HttpEntity<String> json(String body) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      return new HttpEntity<>(body, headers);
   }

   private JsonNode awaitFinalRun(String runId) throws Exception {
      JsonNode run = null;
      for (int attempt = 0; attempt < 100; attempt++) {
         ResponseEntity<String> response = restTemplate.getForEntity("/api" + RUN.replace("{runId}", runId),
               String.class);
         MICROCKS_API.assertResponseConforms("GET", RUN, response);
         run = JSON.readTree(response.getBody());
         if (!"IN_PROGRESS".equals(run.get("status").asText())) {
            return run;
         }
         Thread.sleep(200);
      }
      return run;
   }
}
