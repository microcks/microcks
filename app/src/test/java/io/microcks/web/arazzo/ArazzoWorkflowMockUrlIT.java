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

import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import io.microcks.service.arazzo.ArazzoMinionClient;
import io.microcks.service.arazzo.HttpArazzoMinionClient;
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.HttpMethod;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;
import io.microcks.web.AbstractBaseIT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;


import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of the Mock URL of an Arazzo workflow, end to end: an application posts the inputs of a workflow,
 * the webapp has the Arazzo minion play it on the Microcks mocks it serves, and answers with its outputs or why it
 * failed; the run is stored with the Mock URL as its origin.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoWorkflowMockUrlIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final String PASTRY_SCENARIOS = "/arazzo/API+Pastry+scenarios/1.0.0/";
   private static final ArazzoMinionProcess MINION = new ArazzoMinionProcess();
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoMetadataRepository arazzoMetadataRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   @BeforeEach
   void pastryScenariosAreUploadedAndLinked() throws Exception {
      QuarkusMock.installMockForType(new HttpArazzoMinionClient(MINION.url()), ArazzoMinionClient.class);
      if (!MINION.isStarted()) {
         MINION.start(getServerUrl());
      }
      uploadArtifactFile(FIXTURES + "api-pastry-2.0.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry-scenarios.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo-metadata.yaml", false);
   }

   @AfterEach
   void deleteDocumentsAndRuns() {
      arazzoDocumentRepository.deleteAll();
      arazzoMetadataRepository.deleteAll();
      workflowResultRepository.deleteAll();
   }

   @AfterAll
   static void stopTheMinion() {
      MINION.stop();
   }

   /**
    * AC-02 and AC-05, domain example 1: the workflow is played on the mocks, the answer is exactly its outputs, and the
    * run is stored and listed with the Mock URL as its origin.
    */
   @Test
   void theWorkflowIsPlayedOnTheMocksAndAnswersExactlyItsOutputs() throws Exception {
      ResponseEntity<String> answer = post(PASTRY_SCENARIOS + "readThenReprice",
            "{\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}");

      assertEquals(200, answer.getStatusCode().value(), answer.getBody());
      assertEquals(MediaType.APPLICATION_JSON_VALUE,
            answer.getHeaders().getFirst("Content-Type").replace(";charset=UTF-8", ""));
      assertEquals(JSON.readTree("{\"before\": 2.5, \"after\": 2.6}"), JSON.readTree(answer.getBody()));
      String runId = answer.getHeaders().getFirst("X-Microcks-Arazzo-Run-Id");
      JsonNode run = JSON.readTree(restTemplate.getForEntity("/api/arazzo/runs/" + runId, String.class).getBody());
      assertEquals("SUCCESS MOCKS MOCK_URL",
            run.get("status").asText() + " " + run.get("target").asText() + " " + run.get("origin").asText());
      JsonNode history = JSON.readTree(restTemplate.getForEntity("/api/arazzo/documents/" + run.get("documentId").asText() + "/runs", String.class).getBody());
      assertEquals(runId + " MOCK_URL",
            history.get(0).get("id").asText() + " " + history.get(0).get("origin").asText());
   }

   /**
    * AC-03, domain example 3: a step whose mock answers an error status fails the call with that status (the mocks of
    * API Pastry answer 400 to readFirst, as they lack the example of Baba Rhum), any other failing step with 502,
    * naming the run and the step.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "listThenReadFirst; ''; 400; readFirst; Step readFirst failed: unmet success criteria $statusCode == 200",
         "readThenReprice; {\"pastry\": \"Eclair Cafe\", \"newPrice\": 3.1}; 502; reprice; Step reprice failed: unmet "
               + "success criteria $response.body#/price == $inputs.newPrice" })
   void aFailingStepFailsTheCallWithTheStatusOfItsMockOr502(String workflowId, String inputs, int status,
         String failedStep, String reason) throws Exception {
      ResponseEntity<String> answer = post(PASTRY_SCENARIOS + workflowId, inputs);

      JsonNode failure = JSON.readTree(answer.getBody());
      assertEquals(status + " " + failedStep + " " + reason, answer.getStatusCode().value() + " "
            + failure.path("failedStep").asText() + " " + failure.path("reason").asText(), answer.getBody());
      assertEquals(answer.getHeaders().getFirst("X-Microcks-Arazzo-Run-Id"), failure.path("runId").asText());
      assertEquals("FAILURE",
            JSON.readTree(restTemplate.getForEntity("/api/arazzo/runs/" + failure.path("runId").asText(), String.class).getBody())
                  .get("status").asText());
   }

   /**
    * AC-03 and AC-04, domain examples 2 and 4: missing or invalid inputs are refused with 400, an unknown document or
    * workflow with 404, with the reason, and nothing runs.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "/arazzo/API+Pastry+scenarios/1.0.0/readThenReprice; {\"pastry\": \"Eclair Cafe\"}; 400; Missing input: "
               + "newPrice",
         "/arazzo/API+Pastry+scenarios/1.0.0/readThenReprice; [1, 2]; 400; The inputs must be a JSON object",
         "/arazzo/API+Pastry+scenarios/1.0.0/deleteAll; {}; 404; Unknown workflow: deleteAll",
         "/arazzo/API%20Pastry%20scenarios/9.9.9/readThenReprice; {}; 404; Unknown Arazzo document: API Pastry "
               + "scenarios 9.9.9" })
   void aRefusedCallAnswersTheReasonAndNothingRuns(String path, String inputs, int status, String reason)
         throws Exception {
      ResponseEntity<String> answer = post(path, inputs);

      assertEquals(status + " {\"reason\":\"" + reason + "\"}",
            answer.getStatusCode().value() + " " + answer.getBody());
      assertEquals(0, workflowResultRepository.count());
   }

   /** Same CORS policy as the other mock endpoints: a browser application may post to the Mock URL. */
   @Test
   void aBrowserMayPostToTheMockUrl() {
      given().header("Origin", "http://underwriting-ui").header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "content-type").when()
            .options(PASTRY_SCENARIOS + "readThenReprice").then().statusCode(200)
            .header("Access-Control-Allow-Origin", "http://underwriting-ui");
   }

   private ResponseEntity<String> post(String path, String inputs) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      return restTemplate.exchange(path, HttpMethod.POST, new HttpEntity<>(inputs, headers), String.class);
   }
}
