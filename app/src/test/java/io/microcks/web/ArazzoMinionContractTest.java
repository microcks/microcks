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

import io.microcks.domain.Header;
import io.microcks.domain.Request;
import io.microcks.domain.Response;
import io.microcks.domain.WorkflowCriterionResult;
import io.microcks.domain.WorkflowExpressionValue;
import io.microcks.domain.WorkflowParameterValue;
import io.microcks.domain.WorkflowRunStatus;
import io.microcks.domain.WorkflowStepOperation;
import io.microcks.domain.WorkflowStepResult;
import io.microcks.domain.WorkflowStepStatus;
import io.microcks.service.ArazzoMinionException;
import io.microcks.service.ArazzoRunRequest;
import io.microcks.service.HttpArazzoMinionClient;
import io.microcks.web.dto.ArazzoRunOutcomeDTO;
import io.microcks.web.dto.ArazzoStepReportDTO;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Webapp side of the contract with the Arazzo minion, held in {@code arazzo-minion-contract.json}: the run request it
 * posts and the reports it reads. The minion holds the same file and checks the other side.
 * @author SebastienDegodez
 */
class ArazzoMinionContractTest {

   private static final ObjectMapper JSON = new ObjectMapper();

   private final StubArazzoMinion minion = new StubArazzoMinion();

   static JsonNode contract() throws IOException {
      try (InputStream stream = ArazzoMinionContractTest.class
            .getResourceAsStream("/io/microcks/service/arazzo/arazzo-minion-contract.json")) {
         return JSON.readTree(stream);
      }
   }

   @AfterEach
   void stopTheMinion() {
      minion.stop();
   }

   @Test
   void aRunIsPostedToTheMinionAsItReadsIt() throws Exception {
      new HttpArazzoMinionClient(minion.url()).launch(new ArazzoRunRequest("6719f0c2a1b2c3d4e5f60718",
            "readThenReprice", "arazzo: 1.0.1", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6),
            Map.of("pastryApi",
                  new ArazzoRunRequest.Source("/rest/API+Pastry+-+2.0/2.0.0",
                        Map.of("GetPastries", new ArazzoRunRequest.Operation("GET", "/pastry"), "GetPastryByName",
                              new ArazzoRunRequest.Operation("GET", "/pastry/{name}"), "PatchPastry",
                              new ArazzoRunRequest.Operation("PATCH", "/pastry/{name}")))),
            10000));

      assertEquals(List.of(contract().get("runRequest")),
            minion.runRequests().stream().map(ArazzoMinionContractTest::read).toList());
   }

   @Test
   void aRunTheMinionDoesNotAcceptIsNotReceived() {
      minion.answer(500);

      ArazzoMinionException notReceived = assertThrows(ArazzoMinionException.class,
            () -> new HttpArazzoMinionClient(minion.url()).launch(new ArazzoRunRequest("6719f0c2a1b2c3d4e5f60718",
                  "readThenReprice", "arazzo: 1.0.1", Map.of(), Map.of(), 10000)));

      assertEquals("The Arazzo minion at " + minion.url() + " did not receive the run", notReceived.getMessage());
   }

   @Test
   void aPassedStepIsRecordedWithItsOperationCriteriaOutputsAndExchange() throws IOException {
      ArazzoStepReportDTO report = JSON.treeToValue(contract().get("passedStep"), ArazzoStepReportDTO.class);

      assertEquals(new WorkflowStepResult(0, "readPastry",
            new WorkflowStepOperation("pastryApi", "GetPastryByName", "GET", "/pastry/{name}"),
            WorkflowStepStatus.PASSED, 200, 8,
            List.of(new WorkflowCriterionResult("$statusCode == 200 && $response.body#/status == 'available'",
                  List.of(new WorkflowExpressionValue("$statusCode", 200),
                        new WorkflowExpressionValue("$response.body#/status", "available")),
                  true)),
            Map.of("name", "Eclair Cafe", "price", 2.5), null, null,
            "http://microcks:8080/rest/API+Pastry+-+2.0/2.0.0/pastry/Eclair%20Cafe",
            List.of(new WorkflowParameterValue("name", "path", "Eclair Cafe", "$inputs.pastry"),
                  new WorkflowParameterValue("Accept", "header", "application/json", null)),
            Map.of("name", "$response.body#/name", "price", "$response.body#/price")), report.toStepResult());
      Request request = report.toRequest();
      assertEquals("readPastry Accept=[application/json] null",
            request.getName() + " " + headers(request.getHeaders()) + " " + request.getContent());
      Response response = report.toResponse();
      assertEquals(
            "readPastry 200 application/json Content-Type=[application/json] "
                  + "{\"name\":\"Eclair Cafe\",\"price\":2.5,\"status\":\"available\"}",
            response.getName() + " " + response.getStatus() + " " + response.getMediaType() + " "
                  + headers(response.getHeaders()) + " " + response.getContent());
   }

   @Test
   void theBodyOfARequestIsRecordedAsItsContent() throws IOException {
      ObjectNode passed = (ObjectNode) contract().get("passedStep");
      ((ObjectNode) passed.get("request")).put("body", "{\"price\":2.6}");

      assertEquals("{\"price\":2.6}", JSON.treeToValue(passed, ArazzoStepReportDTO.class).toRequest().getContent());
   }

   @Test
   void aFailedStepIsRecordedWithTheCriterionThatDidNotHold() throws IOException {
      ArazzoStepReportDTO report = JSON.treeToValue(contract().get("failedStep"), ArazzoStepReportDTO.class);

      WorkflowStepResult step = report.toStepResult();
      assertEquals("1 readFirst FAILED 400",
            step.index() + " " + step.stepId() + " " + step.status() + " " + step.statusCode());
      assertEquals(List.of(new WorkflowCriterionResult("$statusCode == 200",
            List.of(new WorkflowExpressionValue("$statusCode", 400)), false)), step.criteria());
      assertEquals("http://microcks:8080/rest/API+Pastry+-+2.0/2.0.0/pastry/Baba%20Rhum", step.requestUrl());
      assertEquals(List.of(new WorkflowParameterValue("name", "path", "Baba Rhum", "$steps.list.outputs.first")),
            step.parameters());
      assertEquals(Map.of(), step.outputOrigins());
      assertEquals("400 text/plain The response /name=Baba Rhum does not exist!", report.toResponse().getStatus() + " "
            + report.toResponse().getMediaType() + " " + report.toResponse().getContent());
   }

   @Test
   void aStepNotRunIsRecordedWithoutExchange() throws IOException {
      ArazzoStepReportDTO report = JSON.treeToValue(contract().get("notRunStep"), ArazzoStepReportDTO.class);

      assertEquals(
            new WorkflowStepResult(2, "reprice",
                  new WorkflowStepOperation("pastryApi", "PatchPastry", "PATCH", "/pastry/{name}"),
                  WorkflowStepStatus.NOT_RUN, null, 0, List.of(), Map.of(), null, null, null, List.of(), Map.of()),
            report.toStepResult());
      assertNull(report.toRequest());
      assertNull(report.toResponse());
   }

   @Test
   void theEndOfARunIsRecordedWithItsOutputs() throws IOException {
      ArazzoRunOutcomeDTO outcome = JSON.treeToValue(contract().get("runOutcome"), ArazzoRunOutcomeDTO.class);

      assertEquals(WorkflowRunStatus.SUCCESS, outcome.runStatus());
      assertEquals(Map.of("before", 2.5, "after", 2.6), outcome.outputs());
      assertEquals(16, outcome.elapsedTime());
      assertNull(outcome.message());
   }

   @Test
   void aRunAgainstAnImplementationIsPostedWithItsBaseUrlAndSecret() throws Exception {
      new HttpArazzoMinionClient(minion.url()).launch(new ArazzoRunRequest("6719f0c2a1b2c3d4e5f60719",
            "readThenReprice", "arazzo: 1.0.1", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6),
            Map.of("pastryApi",
                  new ArazzoRunRequest.Source("http://localhost:8080",
                        Map.of("GetPastryByName", new ArazzoRunRequest.Operation("GET", "/pastry/{name}"),
                              "PatchPastry", new ArazzoRunRequest.Operation("PATCH", "/pastry/{name}")))),
            10000, new ArazzoRunRequest.Secret("pastry-staging-token", null, null, "s3cr3t-t0k3n", "X-Pastry-Token")));

      assertEquals(List.of(contract().get("implementationRunRequest")),
            minion.runRequests().stream().map(ArazzoMinionContractTest::read).toList());
   }

   private static String headers(java.util.Set<Header> headers) {
      return headers.stream().map(header -> header.getName() + "=" + header.getValues()).sorted()
            .collect(Collectors.joining(", "));
   }

   private static JsonNode read(String json) {
      try {
         return JSON.readTree(json);
      } catch (IOException ioe) {
         throw new IllegalStateException(ioe);
      }
   }
}
