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

import io.microcks.service.SpanStorageService;
import io.microcks.web.AbstractBaseIT;
import io.microcks.util.tracing.CommonAttributes;
import io.microcks.util.tracing.CommonEvents;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.service.arazzo.ArazzoMinionClient;
import io.microcks.service.arazzo.HttpArazzoMinionClient;
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * S29 end to end: the calls an Arazzo run makes to the Microcks mocks are stored for Live Traces with the run,
 * workflow, step and attempt they come from; the calls of any other client are stored as before. Runs go through the
 * Arazzo minion, packaged by its module and started as a process.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoLiveTracesIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final String API_PASTRY = "API Pastry - 2.0";
   private static final ArazzoMinionProcess MINION = new ArazzoMinionProcess();
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private SpanStorageService spanStorageService;

   @BeforeEach
   void startTheMinionAgainstThisWebapp() throws Exception {
      QuarkusMock.installMockForType(new HttpArazzoMinionClient(MINION.url()), ArazzoMinionClient.class);
      if (!MINION.isStarted()) {
         MINION.start(getServerUrl());
      }
      uploadArtifactFile(FIXTURES + "api-pastry-2.0.openapi.yaml", true);
      spanStorageService.clearAll();
   }

   @AfterAll
   static void stopTheMinion() {
      MINION.stop();
   }

   @Test
   void eachCallOfARunAgainstTheMocksIsStoredWithItsRunWorkflowStepAndAttempt() throws Exception {
      uploadArtifactFile(FIXTURES + "pastry-scenarios.arazzo.yaml", true);
      uploadArtifactFile(FIXTURES + "pastry.arazzo-metadata.yaml", false);
      String documentId = JSON.readTree(restTemplate.getForEntity("/api/arazzo/documents", String.class).getBody())
            .get(0).get("id").asText();
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      ResponseEntity<String> launched = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/readThenReprice/runs",
            new HttpEntity<>("{\"inputs\": {\"pastry\": \"Eclair Cafe\", \"newPrice\": 2.6}, \"timeout\": 10000}",
                  headers),
            String.class);
      assertEquals(201, launched.getStatusCode().value(), launched.getBody());
      String runId = JSON.readTree(launched.getBody()).get("id").asText();
      JsonNode run = awaitFinalRun(runId);
      assertEquals("SUCCESS", run.get("status").asText(), run.toPrettyString());

      List<String> expected = List.of(
            "GET arazzo.attempt=1, arazzo.run.id=" + runId
                  + ", arazzo.step.id=readPastry, arazzo.via=run, arazzo.workflow.id=readThenReprice",
            "PATCH arazzo.attempt=1, arazzo.run.id=" + runId
                  + ", arazzo.step.id=reprice, arazzo.via=run, arazzo.workflow.id=readThenReprice");
      assertEquals(expected, awaitInvocationsOf(API_PASTRY, 2, false));
      assertEquals(expected, awaitInvocationsOf(API_PASTRY, 2, true));
   }

   @Test
   void theCallsOfAnyOtherClientAreStoredWithoutArazzoAttributes() {
      for (String baggage : Arrays.asList(null, "tenant=bakery", "microcks-arazzo-run")) {
         RequestSpecification request = given().urlEncodingEnabled(false);
         if (baggage != null) {
            request.header("baggage", baggage);
         }
         request.when().get("/rest/API+Pastry+-+2.0/2.0.0/pastry/Eclair%20Cafe").then().statusCode(200);
      }

      assertEquals(List.of("GET ", "GET ", "GET "), awaitInvocationsOf(API_PASTRY, 3, false));
      assertEquals(List.of("GET ", "GET ", "GET "), awaitInvocationsOf(API_PASTRY, 3, true));
   }

   /**
    * The invocations of the mocks of a service Live Traces have, in the order they were received: the method of each
    * and its Arazzo attributes, those of its span or those of its invocation_received event.
    */
   private List<String> awaitInvocationsOf(String serviceName, int count, boolean ofTheEvent) {
      List<String> invocations = List.of();
      for (int attempt = 0; attempt < 50 && invocations.size() < count; attempt++) {
         invocations = spanStorageService.getAllTraceIds().stream()
               .flatMap(traceId -> spanStorageService.getSpansForTrace(traceId).stream())
               .filter(span -> serviceName.equals(span.getAttributes().get(CommonAttributes.SERVICE_NAME)))
               .sorted((a, b) -> Long.compare(a.getStartEpochNanos(), b.getStartEpochNanos()))
               .map(span -> describe(span, ofTheEvent)).toList();
         if (invocations.size() < count) {
            sleep();
         }
      }
      return invocations;
   }

   private static String describe(SpanData span, boolean ofTheEvent) {
      Attributes received = span.getEvents().stream()
            .filter(event -> event.getName().equals(CommonEvents.INVOCATION_RECEIVED.getEventName())).findFirst()
            .orElseThrow().getAttributes();
      Attributes attributes = ofTheEvent ? received : span.getAttributes();
      return received.get(CommonAttributes.HTTP_METHOD) + " "
            + attributes.asMap().entrySet().stream().filter(entry -> entry.getKey().getKey().startsWith("arazzo."))
                  .map(entry -> entry.getKey().getKey() + "=" + entry.getValue()).sorted()
                  .collect(Collectors.joining(", "));
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

   private static void sleep() {
      try {
         Thread.sleep(100);
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
      }
   }
}
