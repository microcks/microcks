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
import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.microcks.service.arazzo.HttpArazzoMinionClient;
import io.microcks.service.arazzo.ArazzoMinionClient;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusMock;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.microcks.domain.Secret;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.repository.SecretRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of the run endpoints: launching a run hands it to the Arazzo minion, which reports it back as the
 * contract tells, and the run is read as reported.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoRunControllerIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final StubArazzoMinion MINION = new StubArazzoMinion();
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoMetadataRepository arazzoMetadataRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   @Inject
   private SecretRepository secretRepository;

   /** The runs are handed to the minion at its URL, as configured with arazzo-minion.url. */
   @BeforeEach
   void handTheRunsToTheMinionAtItsUrl() {
      QuarkusMock.installMockForType(new HttpArazzoMinionClient(MINION.url()), ArazzoMinionClient.class);
   }

   @AfterEach
   void deleteDocumentsAndRuns() {
      arazzoDocumentRepository.deleteAll();
      arazzoMetadataRepository.deleteAll();
      workflowResultRepository.deleteAll();
      MINION.runRequests().clear();
   }

   @AfterAll
   static void stopTheMinion() {
      MINION.stop();
   }

   @Test
   void aLaunchedRunIsHandedToTheMinionThenReadAsItReportsIt() throws Exception {
      String documentId = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      ResponseEntity<String> launched = launch(documentId, "listThenReadFirst");

      assertEquals(201, launched.getStatusCode().value(), launched.getBody());
      String runId = JSON.readTree(launched.getBody()).get("id").asText();
      assertEquals("/api/arazzo/runs/" + runId, java.net.URI.create(launched.getHeaders().getFirst("Location")).getPath());
      ObjectNode runRequest = (ObjectNode) JSON.readTree(MINION.runRequests().get(0));
      assertEquals(List.of(runId, "listThenReadFirst", "/rest/API+Pastry+-+2.0/2.0.0"),
            List.of(runRequest.get("runId").asText(), runRequest.get("workflowId").asText(),
                  runRequest.get("sources").get("pastryApi").get("baseUrl").asText()));
      assertEquals("IN_PROGRESS", run(runId).get("status").asText());

      JsonNode contract = ArazzoMinionContractTest.contract();
      assertEquals(204, report(runId, "steps", contract.get("notRunStep")));
      assertEquals(204, report(runId, "steps", contract.get("passedStep")));
      assertEquals(204, report(runId, "outcome", contract.get("runOutcome")));

      JsonNode run = run(runId);
      assertEquals("SUCCESS", run.get("status").asText());
      assertEquals(List.of("0 readPastry PASSED " + runId + "-0", "2 reprice NOT_RUN none"),
            List.of(describe(run.get("steps").get(0)), describe(run.get("steps").get(1))));
      assertEquals(contract.get("runOutcome").get("outputs"), run.get("outputs"));
      JsonNode readPastry = run.get("steps").get(0);
      assertEquals(contract.get("passedStep").get("request").get("url"), readPastry.get("requestUrl"));
      assertEquals(List.of("name path Eclair Cafe $inputs.pastry", "Accept header application/json constant"),
            List.of(describeParameter(readPastry.get("parameters").get(0)),
                  describeParameter(readPastry.get("parameters").get(1))));
      assertEquals(contract.get("passedStep").get("outputOrigins"), readPastry.get("outputOrigins"));
      assertEquals(200, restTemplate.getForEntity("/api/tests/" + runId + "/messages/" + runId + "-0", String.class)
            .getStatusCode().value());
   }

   @Test
   void aWorkflowThatCannotRunIsRefusedWithItsReason() throws Exception {
      String documentId = pastryScenariosAreImported("pastry-delete.arazzo.yaml");

      ResponseEntity<String> refused = launch(documentId, "readThenReprice");

      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Workflow readThenReprice cannot run: Operation DeletePastry not found in API Pastry - 2.0 2.0.0",
            refused.getBody());
      assertEquals(List.of(), MINION.runRequests());
   }

   /** S8 AC-01: a run missing an input is refused with a 400 naming it, and nothing reaches the minion. */
   @Test
   void aRunMissingAnInputIsRefusedNamingItAndNotHandedToTheMinion() throws Exception {
      String documentId = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      ResponseEntity<String> refused = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/readThenReprice/runs",
            json("{\"inputs\": {\"pastry\": \"Eclair Cafe\"}, \"timeout\": 10000}"), String.class);

      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Missing input: newPrice", refused.getBody());
      assertEquals(List.of(), MINION.runRequests());
      assertEquals(0, workflowResultRepository.count());
   }

   @Test
   void anUnknownDocumentOrRunIsNotFoundAndAnUnknownWorkflowIsABadRequest() throws Exception {
      String documentId = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      assertEquals(404, launch("unknown", "readThenReprice").getStatusCode().value());
      ResponseEntity<String> refused = launch(documentId, "unknown");
      assertEquals("400 Unknown workflow: unknown", refused.getStatusCode().value() + " " + refused.getBody());
      assertEquals(404, restTemplate.getForEntity("/api/arazzo/runs/unknown", String.class).getStatusCode().value());
      assertEquals(404, report("unknown", "steps", ArazzoMinionContractTest.contract().get("passedStep")));
      assertEquals(404, report("unknown", "outcome", ArazzoMinionContractTest.contract().get("runOutcome")));
   }

   @Test
   void aRunAgainstAnImplementationIsHandedToTheMinionWithItsBaseUrlAndSecret() throws Exception {
      String documentId = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");
      Secret secret = new Secret();
      secret.setName("pastry-staging-token");
      secret.setToken("s3cr3t-t0k3n");
      secret.setTokenHeader("X-Pastry-Token");
      String secretId = secretRepository.save(secret).getId();

      try {
         ResponseEntity<String> launched = restTemplate.postForEntity(
               "/api/arazzo/documents/" + documentId + "/workflows/readThenReprice/runs",
               json("{\"inputs\": {\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}, \"timeout\": 10000, "
                     + "\"implementations\": {\"pastryApi\": \"http://localhost:8080\"}, "
                     + "\"secretName\": \"pastry-staging-token\"}"),
               String.class);

         assertEquals(201, launched.getStatusCode().value(), launched.getBody());
         JsonNode runRequest = JSON.readTree(MINION.runRequests().get(0));
         assertEquals("http://localhost:8080", runRequest.get("sources").get("pastryApi").get("baseUrl").asText());
         assertEquals(ArazzoMinionContractTest.contract().get("implementationRunRequest").get("secret"),
               runRequest.get("secret"));
         JsonNode run = run(JSON.readTree(launched.getBody()).get("id").asText());
         assertEquals(List.of("IMPLEMENTATIONS", "http://localhost:8080", secretId, "pastry-staging-token"),
               List.of(run.get("target").asText(), run.get("sources").get(0).get("baseUrl").asText(),
                     run.get("secretRef").get("secretId").asText(), run.get("secretRef").get("name").asText()));
      } finally {
         secretRepository.deleteById(secretId);
      }
   }

   @Test
   void aMixedRunSaysWhereEachSourceGoesAsTheMockupLaunchesIt() throws Exception {
      uploadArtifactFile(FIXTURES + "customer-api.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "order-api.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "order-fulfilment.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "order-fulfilment.arazzo-metadata.yaml", false);
      String documentId = JSON.readTree(restTemplate.getForEntity("/api/arazzo/documents", String.class).getBody())
            .get(0).get("id").asText();

      ResponseEntity<String> launched = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/placeOrder/runs",
            json("{\"inputs\": {\"customerId\": \"c-42\"}, \"timeout\": 10000, "
                  + "\"implementations\": {\"customerApi\": \"https://customers.staging.example\"}, "
                  + "\"target\": {\"kind\": \"mixed\", "
                  + "\"perSource\": {\"customerApi\": \"implementation\", \"orderApi\": \"mock\"}}}"),
            String.class);

      assertEquals(201, launched.getStatusCode().value(), launched.getBody());
      JsonNode sources = JSON.readTree(MINION.runRequests().get(0)).get("sources");
      assertEquals(List.of("https://customers.staging.example", "/rest/Order+API/1.0.0"),
            List.of(sources.get("customerApi").get("baseUrl").asText(), sources.get("orderApi").get("baseUrl").asText()));
      String runId = JSON.readTree(launched.getBody()).get("id").asText();
      assertEquals("MIXED", run(runId).get("target").asText());
      JsonNode mixedRuns = JSON.readTree(restTemplate
            .getForEntity("/api/arazzo/documents/" + documentId + "/runs?target=MIXED", String.class).getBody());
      assertEquals(List.of(runId), List.of(mixedRuns.get(0).get("id").asText()));
   }

   @Test
   void aMixedRunToAnUnknownTargetIsABadRequest() throws Exception {
      String documentId = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      ResponseEntity<String> refused = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/readThenReprice/runs",
            json("{\"inputs\": {\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}, "
                  + "\"target\": {\"kind\": \"mixed\", \"perSource\": {\"pastryApi\": \"staging\"}}}"),
            String.class);

      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Workflow readThenReprice cannot run: source pastryApi is sent to staging, not to mock or "
            + "implementation", refused.getBody());
      assertEquals(List.of(), MINION.runRequests());
   }

   @ParameterizedTest
   @ValueSource(strings = { "implementations", "Mixed", "" })
   void aTargetOfAnotherKindThanMixedIsABadRequest(String kind) throws Exception {
      String documentId = pastryScenariosAreImported("pastry-scenarios.arazzo.yaml");

      ResponseEntity<String> refused = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/readThenReprice/runs",
            json("{\"inputs\": {\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}, \"target\": {\"kind\": \"" + kind
                  + "\", \"perSource\": {\"pastryApi\": \"mock\"}}}"),
            String.class);

      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Unknown run target kind '" + kind + "': a run target is mixed, or left out to run on the mocks or "
            + "on the implementations", refused.getBody());
      assertEquals(List.of(), MINION.runRequests());
   }

   private String pastryScenariosAreImported(String fixture) throws Exception {
      uploadArtifactFile(FIXTURES + "api-pastry-2.0.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + fixture, true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo-metadata.yaml", false);
      return JSON.readTree(restTemplate.getForEntity("/api/arazzo/documents", String.class).getBody()).get(0).get("id").asText();
   }

   private ResponseEntity<String> launch(String documentId, String workflowId) {
      return restTemplate.postForEntity("/api/arazzo/documents/" + documentId + "/workflows/" + workflowId + "/runs",
            json("{\"inputs\": {}, \"timeout\": 10000}"), String.class);
   }

   private int report(String runId, String kind, JsonNode report) {
      return restTemplate.postForEntity("/api/arazzo/runs/" + runId + "/" + kind, json(report.toString()), String.class)
            .getStatusCode().value();
   }

   private JsonNode run(String runId) throws Exception {
      return JSON.readTree(restTemplate.getForEntity("/api/arazzo/runs/" + runId, String.class).getBody());
   }

   private static String describe(JsonNode step) {
      return step.get("index").asInt() + " " + step.get("stepId").asText() + " " + step.get("status").asText() + " "
            + step.path("testCaseId").asText("none");
   }

   private static String describeParameter(JsonNode parameter) {
      return parameter.get("name").asText() + " " + parameter.get("in").asText() + " " + parameter.get("value").asText()
            + " " + parameter.path("expression").asText("constant");
   }

   private static HttpEntity<String> json(String body) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      return new HttpEntity<>(body, headers);
   }
}
