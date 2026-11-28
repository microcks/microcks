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
package io.microcks.minion.arazzo.runner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Acceptance tests of the minion validating each step response against the OpenAPI schema of its operation, entering
 * through its runner with an implementation of API Pastry as the HTTP side. The examples are in
 * {@code schema-validation.yaml}.
 * @author SebastienDegodez
 */
class ResponseSchemaValidationTest {

   private static final String JSON = "application/json";
   private static final String ECLAIR_CAFE = "{\"name\":\"Eclair Cafe\",\"size\":\"M\",\"price\":2.5,"
         + "\"status\":\"available\"}";
   private static final boolean VALIDATION_ON = true;
   private static final boolean VALIDATION_OFF = false;
   private static final String PASTRY_SCHEMA = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"},"
         + "\"price\":{\"type\":\"number\"}}}";

   private final PastryImplementation implementation = new PastryImplementation()
         .answer("GET /pastry/Eclair%20Cafe", 200, JSON, ECLAIR_CAFE)
         .answer("PATCH /pastry/Eclair%20Cafe", 200, JSON, "{\"price\": \"2.6\"}");
   private final RecordingReporter reporter = new RecordingReporter();
   private final WorkflowRunner runner = new WorkflowRunner(implementation, new ManualClock());

   @Test
   void aNonConformantResponseFailsTheStepEvenWhenItsCriteriaHold() throws IOException {
      runner.run(repriceRun(VALIDATION_ON), reporter);

      assertEquals(List.of("0 readPastry PASSED 200", "1 reprice FAILED 200"), described(reporter));
      StepResult reprice = reporter.step("reprice");
      assertEquals(List.of("$statusCode == 200 true"),
            reprice.criteria().stream().map(criterion -> criterion.condition() + " " + criterion.held()).toList());
      assertEquals("/price does not match the Pastry schema: string found, number expected", reprice.message());
      assertEquals(Map.of(), reprice.outputs());
      assertEquals(new RunOutcome(RunStatus.FAILURE, Map.of(), 0, null), reporter.outcome());
   }

   @Test
   void withoutValidationOnlyTheCriteriaDecide() throws IOException {
      runner.run(repriceRun(VALIDATION_OFF), reporter);

      assertEquals(List.of("0 readPastry PASSED 200", "1 reprice PASSED 200"), described(reporter));
      assertNull(reporter.step("reprice").message());
      assertEquals(new RunOutcome(RunStatus.SUCCESS, Map.of("after", "2.6"), 0, null), reporter.outcome());
   }

   @Test
   void aConformantResponsePasses() throws IOException {
      runner.run(run("readOnly", Map.of("pastry", "Eclair Cafe"), contract(), VALIDATION_ON), reporter);

      assertEquals(List.of("0 readPastry PASSED 200"), described(reporter));
      assertNull(reporter.step("readPastry").message());
      assertEquals(RunStatus.SUCCESS, reporter.outcome().status());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
         "200 | application/json | {\"name\":\"Eclair Cafe\",\"size\":3,\"price\":\"2.5\"} | FAILED | "
               + "/price does not match the Pastry schema: string found, number expected; "
               + "/size does not match the Pastry schema: integer found, string expected",
         "200 | application/json;charset=UTF-8 | \"Eclair Cafe\" | FAILED | "
               + "The response body does not match the Pastry schema: string found, object expected",
         "404 | application/json | {} | FAILED | "
               + "`The response body does not match the Error schema: required property 'message' not found`",
         "500 | application/json | {\"message\":\"boom\"} | FAILED | "
               + "Status 500 of GetPastryByName is not described in the OpenAPI contract of pastryApi",
         "200 | application/problem+json | {} | FAILED | "
               + "Content type application/problem+json of status 200 of GetPastryByName is not described in the "
               + "OpenAPI contract of pastryApi",
         "200 | application/json | price is 2.5 | FAILED | The response body is not JSON",
         "202 | application/json | {\"any\":true} | FAILED | Content type application/json of status 202 of "
               + "GetPastryByName has no schema in the OpenAPI contract of pastryApi",
         "200 | text/plain | price is 2.5 | PASSED | ", "200 | application/xml | <pastry/> | PASSED | ",
         "204 | application/json | | PASSED | " })
   void eachResponseIsCheckedLikeOpenApiContractTestsCheckIt(int status, String contentType, String body,
         StepStatus stepStatus, String message) throws IOException {
      implementation.answer("GET /pastry/Eclair%20Cafe", status, contentType, body);

      runner.run(run("readOnly", Map.of("pastry", "Eclair Cafe"), contract(), VALIDATION_ON), reporter);

      assertEquals(stepStatus, reporter.step("readPastry").status());
      assertEquals(message, reporter.step("readPastry").message());
   }

   @Test
   void aResponseWithoutContentTypeIsNotValidated() throws IOException {
      implementation.answer("GET /pastry/Eclair%20Cafe",
            new HttpOutcome(200, Map.of("x-original-content-type", List.of(JSON)), "price is 2.5"));

      runner.run(run("readOnly", Map.of("pastry", "Eclair Cafe"), contract(), VALIDATION_ON), reporter);

      assertEquals(List.of("0 readPastry PASSED 200"), described(reporter));
   }

   @Test
   void aSchemaWithoutNameIsTheResponseSchema() throws IOException {
      implementation.answer("GET /pastry", 200, "application/json;charset=UTF-8",
            "[" + ECLAIR_CAFE + ",{\"price\":\"2.6\"}]");

      runner.run(run("listOnly", Map.of(), contract(), VALIDATION_ON), reporter);

      assertEquals("/1/price does not match the response schema: string found, number expected",
            reporter.step("list").message());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
         "200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":2.5} | PASSED | ",
         "200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":\"2.5\"} | FAILED | "
               + "/price does not match the Pastry schema: string found, number expected",
         "404 | application/problem+json | {} | FAILED | "
               + "`The response body does not match the Error schema: required property 'message' not found`",
         "500 | application/json | {} | FAILED | "
               + "Status 500 of GetPastryByName is not described in the OpenAPI contract of pastryApi",
         "200 | text/plain | price is 2.5 | PASSED | ",
         "202 | application/json | {\"any\":true} | FAILED | Content type application/json of status 202 of "
               + "GetPastryByName has no schema in the OpenAPI contract of pastryApi" })
   void aSwaggerContractIsCheckedLikeSwaggerContractTestsCheckIt(int status, String contentType, String body,
         StepStatus stepStatus, String message) throws IOException {
      implementation.answer("GET /pastry/Eclair%20Cafe", status, contentType, body);

      runner.run(
            run("readOnly", Map.of("pastry", "Eclair Cafe"), exampleOf("api-pastry-swagger-contract"), VALIDATION_ON),
            reporter);

      assertEquals(stepStatus, reporter.step("readPastry").status());
      assertEquals(message, reporter.step("readPastry").message());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "              | No OpenAPI contract of source pastryApi to validate the response against",
         "unreadable-contract | The OpenAPI contract of source pastryApi cannot be read" })
   void aResponseCannotBeValidatedWithoutAReadableContract(String contractExample, String message) throws IOException {
      String contract = contractExample == null ? null : exampleOf(contractExample);

      runner.run(run("readOnly", Map.of("pastry", "Eclair Cafe"), contract, VALIDATION_ON), reporter);

      assertEquals("0 readPastry FAILED 200", described(reporter).get(0));
      assertEquals(message, reporter.step("readPastry").message());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "{\"name\":\"Eclair Cafe\",\"price\":2.5} | PASSED | ",
         "{\"name\":\"Eclair Cafe\",\"price\":\"2.5\"} | FAILED | "
               + "/price does not match the pastry-schema.json schema: string found, number expected" })
   void theRelativeReferencesOfAContractResolveAgainstItsBaseUrl(String body, StepStatus stepStatus, String message)
         throws IOException {
      HttpServer resources = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      resources.createContext("/api/resources/pastry-schema.json", exchange -> {
         byte[] schema = PASTRY_SCHEMA.getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", JSON);
         exchange.sendResponseHeaders(200, schema.length);
         exchange.getResponseBody().write(schema);
         exchange.close();
      });
      resources.start();
      try {
         implementation.answer("GET /pastry/Eclair%20Cafe", 200, JSON, body);
         String contractBaseUrl = "http://localhost:" + resources.getAddress().getPort() + "/api/resources/";

         runner.run(
               new RunSpecification("run-1", "readOnly", exampleOf("pastry-journeys"), Map.of("pastry", "Eclair Cafe"),
                     Map.of("pastryApi",
                           new SourceTarget(PastryImplementation.BASE_URL, RunFixtures.PASTRY_OPERATIONS,
                                 exampleOf("api-pastry-split-contract"), contractBaseUrl)),
                     RunFixtures.TIMEOUT, VALIDATION_ON),
               reporter);
      } finally {
         resources.stop(0);
      }

      assertEquals(stepStatus, reporter.step("readPastry").status());
      assertEquals(message, reporter.step("readPastry").message());
   }

   private static RunSpecification repriceRun(boolean validateSchema) throws IOException {
      return run("readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6), contract(), validateSchema);
   }

   private static RunSpecification run(String workflowId, Map<String, Object> inputs, String contract,
         boolean validateSchema) throws IOException {
      return new RunSpecification("run-1", workflowId, exampleOf("pastry-journeys"), inputs,
            Map.of("pastryApi",
                  new SourceTarget(PastryImplementation.BASE_URL, RunFixtures.PASTRY_OPERATIONS, contract)),
            RunFixtures.TIMEOUT, validateSchema);
   }

   private static String contract() throws IOException {
      return exampleOf("api-pastry-contract");
   }

   private static List<String> described(RecordingReporter reporter) {
      return reporter.steps().stream()
            .map(step -> step.index() + " " + step.stepId() + " " + step.status() + " " + step.statusCode()).toList();
   }

   /** An example of {@code schema-validation.yaml}, by its name. */
   static String exampleOf(String example) throws IOException {
      try (InputStream stream = ResponseSchemaValidationTest.class
            .getResourceAsStream("/io/microcks/minion/arazzo/runner/schema-validation.yaml")) {
         String marker = "# example: " + example + " ";
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.contains(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in the fixture"));
      }
   }
}
