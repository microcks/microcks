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
package io.microcks.minion.arazzo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The minion as Microcks sees it: it accepts the run request of the contract on POST /api/runs, calls the mocks of
 * Microcks, and posts the reports of the contract back to Microcks.
 * @author SebastienDegodez
 */
@QuarkusTest
@QuarkusTestResource(StubMicrocks.class)
class RunResourceQuarkusTest {

   private static final ObjectMapper JSON = new ObjectMapper();

   @Test
   void aRunRequestedByMicrocksIsAcceptedThenReportedToIt() throws Exception {
      JsonNode contract = resource("contract/arazzo-minion-contract.json");
      ObjectNode runRequest = (ObjectNode) contract.get("runRequest");
      runRequest.put("arazzoDocument", text("runner/workflow-runs.arazzo.yaml").split("\n---\n")[0]);
      ((ObjectNode) runRequest.get("sources").get("pastryApi")).put("openApiContract",
            text("runner/schema-validation.yaml").split("\n---\n")[1]);

      given().contentType(ContentType.JSON).body(runRequest.toString()).when().post("/api/runs").then().statusCode(202);

      awaitReports(3);
      String run = "/api/arazzo/runs/6719f0c2a1b2c3d4e5f60718";
      assertEquals(List.of(run + "/steps", run + "/steps", run + "/outcome"), StubMicrocks.REPORT_PATHS);
      ObjectNode readPastry = normalized(JSON.readTree(StubMicrocks.REPORTS.get(0)));
      assertEquals(contract.get("passedStep"), readPastry);
      ObjectNode outcome = (ObjectNode) JSON.readTree(StubMicrocks.REPORTS.get(2));
      outcome.put("elapsedTime", 16);
      assertEquals(contract.get("runOutcome"), outcome);
   }

   /** The parts of a report that depend on the time and on the stub are set to those of the contract. */
   private static ObjectNode normalized(JsonNode report) {
      ObjectNode step = (ObjectNode) report;
      step.put("elapsedTime", 8);
      ObjectNode request = (ObjectNode) step.get("request");
      request.put("url", request.get("url").asText().replace(StubMicrocks.url, "http://microcks:8080"));
      ObjectNode headers = JSON.createObjectNode();
      step.get("response").get("headers").properties().stream()
            .filter(header -> header.getKey().equalsIgnoreCase("Content-Type"))
            .forEach(header -> headers.set("Content-Type", (ArrayNode) header.getValue()));
      ((ObjectNode) step.get("response")).set("headers", headers);
      return step;
   }

   private static void awaitReports(int count) throws InterruptedException {
      for (int attempt = 0; attempt < 50 && StubMicrocks.REPORTS.size() < count; attempt++) {
         Thread.sleep(100);
      }
   }

   private static JsonNode resource(String path) throws IOException {
      return JSON.readTree(text(path));
   }

   private static String text(String path) throws IOException {
      try (InputStream stream = RunResourceQuarkusTest.class
            .getResourceAsStream("/io/microcks/minion/arazzo/" + path)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
