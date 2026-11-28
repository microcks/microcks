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
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;
import io.quarkus.test.junit.QuarkusTest;
import io.microcks.service.arazzo.HttpArazzoMinionClient;
import io.microcks.service.arazzo.ArazzoMinionClient;
import io.quarkus.test.junit.QuarkusMock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Walking skeleton of running an Arazzo workflow: from the webapp, through the Arazzo minion, to the Microcks mocks the
 * webapp serves, and back to the run stored by the webapp.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunWalkingSkeletonIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final ArazzoMinionProcess MINION = new ArazzoMinionProcess();
   private static final ObjectMapper JSON = new ObjectMapper();

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
   void readThenRepricePassesAgainstTheMocksOfApiPastry() throws Exception {
      uploadArtifactFile(FIXTURES + "api-pastry-2.0.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry-scenarios.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo-metadata.yaml", false);
      String documents = restTemplate.getForEntity("/api/arazzo/documents", String.class).getBody();
      String documentId = JSON.readTree(documents).get(0).get("id").asText();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      ResponseEntity<String> launched = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/readThenReprice/runs",
            new HttpEntity<>("{\"inputs\": {\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}, \"timeout\": 10000}",
                  headers),
            String.class);
      assertEquals(201, launched.getStatusCode().value(), launched.getBody());

      JsonNode run = awaitFinalRun(JSON.readTree(launched.getBody()).get("id").asText());

      assertEquals("SUCCESS", run.get("status").asText(), run.toPrettyString());
      List<String> steps = new ArrayList<>();
      run.get("steps").forEach(step -> steps
            .add(step.get("index").asInt() + " " + step.get("stepId").asText() + " " + step.get("status").asText()));
      assertEquals(List.of("0 readPastry PASSED", "1 reprice PASSED"), steps);
      assertEquals(JSON.readTree("{\"before\": 2.5, \"after\": 2.6}"), run.get("outputs"));
   }

   /** The reprice does not say its contentType: the operation of the contract accepts JSON only. */
   @Test
   void readThenRepriceWithoutContentTypePassesWhenTheContractDeclaresOneMediaType() throws Exception {
      JsonNode run = runRepriceWithoutContentTypeOn("api-pastry-2.0-json-only.openapi.yaml");

      assertEquals("SUCCESS", run.get("status").asText(), run.toPrettyString());
      assertEquals(JSON.readTree("{\"before\": 2.5, \"after\": 2.6}"), run.get("outputs"));
   }

   /** API Pastry - 2.0 accepts JSON and XML: the run does not choose, and ends instead of staying in progress. */
   @Test
   void readThenRepriceWithoutContentTypeFailsWhenTheContractDeclaresSeveralMediaTypes() throws Exception {
      JsonNode run = runRepriceWithoutContentTypeOn("api-pastry-2.0.openapi.yaml");

      assertEquals("FAILURE", run.get("status").asText(), run.toPrettyString());
      assertEquals("No contentType for the request body, and PatchPastry accepts several: application/json, text/xml",
            run.get("steps").get(1).get("message").asText(), run.toPrettyString());
   }

   private JsonNode runRepriceWithoutContentTypeOn(String contract) throws Exception {
      uploadArtifactFile(FIXTURES + contract, true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo-metadata.yaml", false);
      String documents = restTemplate.getForEntity("/api/arazzo/documents", String.class).getBody();
      String documentId = JSON.readTree(documents).get(0).get("id").asText();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      ResponseEntity<String> launched = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/readThenReprice/runs",
            new HttpEntity<>("{\"inputs\": {\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}, \"timeout\": 10000}",
                  headers),
            String.class);
      assertEquals(201, launched.getStatusCode().value(), launched.getBody());
      return awaitFinalRun(JSON.readTree(launched.getBody()).get("id").asText());
   }

   @Test
   void listThenReadFirstFailsOnTheExampleTheMocksOfApiPastryLack() throws Exception {
      uploadArtifactFile(FIXTURES + "api-pastry-2.0.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry-scenarios.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo-metadata.yaml", false);
      String documents = restTemplate.getForEntity("/api/arazzo/documents", String.class).getBody();
      String documentId = JSON.readTree(documents).get(0).get("id").asText();

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      ResponseEntity<String> launched = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/listThenReadFirst/runs",
            new HttpEntity<>("{\"inputs\": {}, \"timeout\": 10000}", headers), String.class);
      assertEquals(201, launched.getStatusCode().value(), launched.getBody());

      JsonNode run = awaitFinalRun(JSON.readTree(launched.getBody()).get("id").asText());

      assertEquals("FAILURE", run.get("status").asText(), run.toPrettyString());
      JsonNode missing = run.get("missingExample");
      assertNotNull(missing, run.toPrettyString());
      assertEquals("1 readFirst GetPastryByName GET /pastry/{name}",
            missing.get("stepIndex").asInt() + " " + missing.get("stepId").asText() + " "
                  + missing.get("operationId").asText() + " " + missing.get("operationName").asText(),
            run.toPrettyString());
      assertEquals(JSON.readTree(
            "[{\"name\": \"name\", \"in\": \"path\", \"value\": \"Baba Rhum\", \"expression\": \"$steps.list.outputs.first\"}]"),
            missing.get("parameters"));
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
