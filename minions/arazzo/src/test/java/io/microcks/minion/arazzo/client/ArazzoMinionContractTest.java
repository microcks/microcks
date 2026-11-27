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
package io.microcks.minion.arazzo.client;

import io.microcks.minion.arazzo.client.dto.RunOutcomeDTO;
import io.microcks.minion.arazzo.client.dto.RunRequestDTO;
import io.microcks.minion.arazzo.client.dto.StepReportDTO;
import io.microcks.minion.arazzo.runner.CriterionResult;
import io.microcks.minion.arazzo.runner.ExpressionValue;
import io.microcks.minion.arazzo.runner.HttpCall;
import io.microcks.minion.arazzo.runner.HttpOutcome;
import io.microcks.minion.arazzo.runner.OperationTarget;
import io.microcks.minion.arazzo.runner.ParameterValue;
import io.microcks.minion.arazzo.runner.RunOutcome;
import io.microcks.minion.arazzo.runner.RunSecret;
import io.microcks.minion.arazzo.runner.RunSpecification;
import io.microcks.minion.arazzo.runner.RunStatus;
import io.microcks.minion.arazzo.runner.SourceTarget;
import io.microcks.minion.arazzo.runner.StepOperation;
import io.microcks.minion.arazzo.runner.StepResult;
import io.microcks.minion.arazzo.runner.StepStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Minion side of the contract with Microcks, held in {@code arazzo-minion-contract.json}: the run request it reads and
 * the reports it writes. The webapp holds the same file and checks the other side.
 * @author SebastienDegodez
 */
class ArazzoMinionContractTest {

   static final ObjectMapper JSON = new ObjectMapper();
   private static final StepOperation GET_PASTRY_BY_NAME = new StepOperation("pastryApi", "GetPastryByName", "GET",
         "/pastry/{name}");
   private static final String MOCKS = "http://microcks:8080/rest/API+Pastry+-+2.0/2.0.0";

   /** The parts of the contract, by name. */
   static JsonNode contract() throws IOException {
      try (InputStream stream = ArazzoMinionContractTest.class
            .getResourceAsStream("/io/microcks/minion/arazzo/contract/arazzo-minion-contract.json")) {
         return JSON.readTree(stream);
      }
   }

   @Test
   void theRunRequestOfMicrocksIsReadWithTheMocksRelativeToMicrocks() throws IOException {
      RunRequestDTO request = JSON.treeToValue(contract().get("runRequest"), RunRequestDTO.class);

      assertEquals(
            new RunSpecification("6719f0c2a1b2c3d4e5f60718", "readThenReprice", "arazzo: 1.0.1",
                  Map.of("pastry", "Eclair Cafe", "newPrice", 2.6),
                  Map.of("pastryApi",
                        SourceTarget.onMocks(URI.create(MOCKS),
                              Map.of("GetPastries", new OperationTarget("GET", "/pastry"), "GetPastryByName",
                                    new OperationTarget("GET", "/pastry/{name}"), "PatchPastry",
                                    new OperationTarget("PATCH", "/pastry/{name}")))),
                  Duration.ofSeconds(10)),
            request.toSpecification("http://microcks:8080"));
   }

   @Test
   void anAbsoluteBaseUrlIsKeptAsIs() throws IOException {
      ObjectNode runRequest = (ObjectNode) contract().get("runRequest");
      ((ObjectNode) runRequest.get("sources").get("pastryApi")).put("baseUrl", "http://pastry.example:8080");

      RunSpecification specification = JSON.treeToValue(runRequest, RunRequestDTO.class)
            .toSpecification("http://microcks:8080");

      assertEquals(URI.create("http://pastry.example:8080"), specification.sources().get("pastryApi").baseUrl());
      assertFalse(specification.sources().get("pastryApi").mocks());
   }

   @Test
   void aRunAgainstAnImplementationIsReadWithItsBaseUrlAndTheAuthorizationOfItsSecret() throws IOException {
      RunRequestDTO request = JSON.treeToValue(contract().get("implementationRunRequest"), RunRequestDTO.class);

      assertEquals(
            new RunSpecification("6719f0c2a1b2c3d4e5f60719", "readThenReprice", "arazzo: 1.0.1",
                  Map.of("pastry", "Eclair Cafe", "newPrice", 2.6),
                  Map.of("pastryApi",
                        new SourceTarget(URI.create("http://localhost:8080"),
                              Map.of("GetPastryByName", new OperationTarget("GET", "/pastry/{name}"), "PatchPastry",
                                    new OperationTarget("PATCH", "/pastry/{name}")))),
                  Duration.ofSeconds(10), new RunSecret(null, null, "s3cr3t-t0k3n", "X-Pastry-Token")),
            request.toSpecification("http://microcks:8080"));
   }

   @Test
   void eachKindOfStepIsReportedAsMicrocksReadsIt() throws IOException {
      JsonNode contract = contract();
      StepResult passed = new StepResult(0, "readPastry", GET_PASTRY_BY_NAME, StepStatus.PASSED, 200, 8,
            List.of(new CriterionResult("$statusCode == 200 && $response.body#/status == 'available'",
                  List.of(new ExpressionValue("$statusCode", 200),
                        new ExpressionValue("$response.body#/status", "available")),
                  true)),
            outputs("name", "Eclair Cafe", "price", 2.5),
            new HttpCall("GET", URI.create(MOCKS + "/pastry/Eclair%20Cafe"), Map.of("Accept", "application/json"),
                  null),
            new HttpOutcome(200, Map.of("Content-Type", List.of("application/json")),
                  "{\"name\":\"Eclair Cafe\",\"price\":2.5,\"status\":\"available\"}"),
            null,
            List.of(new ParameterValue("name", "path", "Eclair Cafe", "$inputs.pastry"),
                  new ParameterValue("Accept", "header", "application/json", null)),
            origins("name", "$response.body#/name", "price", "$response.body#/price"));
      StepResult failed = new StepResult(1, "readFirst", GET_PASTRY_BY_NAME, StepStatus.FAILED, 400, 5,
            List.of(new CriterionResult("$statusCode == 200", List.of(new ExpressionValue("$statusCode", 400)), false)),
            Map.of(), new HttpCall("GET", URI.create(MOCKS + "/pastry/Baba%20Rhum"), Map.of(), null),
            new HttpOutcome(400, Map.of("Content-Type", List.of("text/plain")),
                  "The response /name=Baba Rhum does not exist!"),
            null, List.of(new ParameterValue("name", "path", "Baba Rhum", "$steps.list.outputs.first")), Map.of());
      StepResult notRun = new StepResult(2, "reprice",
            new StepOperation("pastryApi", "PatchPastry", "PATCH", "/pastry/{name}"), StepStatus.NOT_RUN, null, 0,
            List.of(), Map.of(), null, null, null, List.of(), Map.of());

      assertEquals(contract.get("passedStep"), written(StepReportDTO.from(passed)));
      assertEquals(contract.get("failedStep"), written(StepReportDTO.from(failed)));
      assertEquals(contract.get("notRunStep"), written(StepReportDTO.from(notRun)));
   }

   @Test
   void theEndOfARunIsReportedAsMicrocksReadsIt() throws IOException {
      assertEquals(contract().get("runOutcome"), written(
            RunOutcomeDTO.from(new RunOutcome(RunStatus.SUCCESS, outputs("before", 2.5, "after", 2.6), 16, null))));
   }

   @Test
   void theReporterOfARunReportsToItsRunInMicrocks() {
      List<String> reports = new ArrayList<>();
      MicrocksRunReporter reporter = new MicrocksRunReporter(recording(reports), "6719f0c2a1b2c3d4e5f60718");
      StepResult notRun = new StepResult(2, "reprice", GET_PASTRY_BY_NAME, StepStatus.NOT_RUN, null, 0, List.of(),
            Map.of(), null, null, null, List.of(), Map.of());

      reporter.stepEnded(notRun);
      reporter.runEnded(new RunOutcome(RunStatus.FAILURE, Map.of(), 5, null));

      assertEquals(List.of("6719f0c2a1b2c3d4e5f60718 " + StepReportDTO.from(notRun),
            "6719f0c2a1b2c3d4e5f60718 " + new RunOutcomeDTO("FAILURE", Map.of(), 5, null)), reports);
   }

   private static Map<String, Object> outputs(String name, Object value, String otherName, Object otherValue) {
      Map<String, Object> outputs = new LinkedHashMap<>();
      outputs.put(name, value);
      outputs.put(otherName, otherValue);
      return outputs;
   }

   private static Map<String, String> origins(String name, String origin, String otherName, String otherOrigin) {
      Map<String, String> origins = new LinkedHashMap<>();
      origins.put(name, origin);
      origins.put(otherName, otherOrigin);
      return origins;
   }

   /** The JSON a report is written as, read back. */
   private static JsonNode written(Object report) throws IOException {
      return JSON.readTree(JSON.writeValueAsString(report));
   }

   /**
    * A connector keeping the reports posted to Microcks, each with the id of its run. A proxy rather than a class, so
    * that Quarkus does not take it for a REST resource.
    */
   private static MicrocksAPIConnector recording(List<String> reports) {
      return (MicrocksAPIConnector) Proxy.newProxyInstance(MicrocksAPIConnector.class.getClassLoader(),
            new Class<?>[] { MicrocksAPIConnector.class }, (proxy, method, arguments) -> {
               reports.add(arguments[0] + " " + arguments[1]);
               return null;
            });
   }
}
