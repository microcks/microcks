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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The runner of the minion validating the responses of the real Microcks mocks against their OpenAPI contract: the
 * mocks of API Pastry - 2.0, from the APIPastry-openapi.yaml sample, conform to it; those of API Pastry -
 * non-conformant, from {@code schema-validation.yaml}, answer PatchPastry with a price as a string.
 * @author SebastienDegodez
 */
@Testcontainers
class ResponseSchemaValidationIT {

   private static final Path API_PASTRY = Path.of("..", "..", "samples", "APIPastry-openapi.yaml");

   @Container
   private static final GenericContainer<?> MICROCKS = new GenericContainer<>(
         DockerImageName.parse("quay.io/microcks/microcks-uber:nightly")).withExposedPorts(8080)
               .waitingFor(Wait.forHttp("/api/health").forStatusCode(200));

   private final RecordingReporter reporter = new RecordingReporter();
   private final WorkflowRunner runner = new WorkflowRunner(new JdkHttpTransport(), Clock.systemUTC());

   @BeforeAll
   static void importTheContracts() throws Exception {
      upload("APIPastry-openapi.yaml", Files.readString(API_PASTRY));
      upload("api-pastry-non-conformant.yaml", ResponseSchemaValidationTest.exampleOf("non-conformant-mocks"));
   }

   @Test
   void aNonConformantResponseOfTheMocksFailsTheStepEvenWhenItsCriteriaHold() throws Exception {
      runner.run(run("/rest/API+Pastry+-+non-conformant/2.0.0",
            ResponseSchemaValidationTest.exampleOf("non-conformant-mocks")), reporter);

      assertEquals(List.of("readPastry PASSED 200", "reprice FAILED 200"), described(reporter));
      assertEquals("/price does not match the Pastry schema: string found, number expected",
            reporter.step("reprice").message());
      assertEquals(RunStatus.FAILURE, reporter.outcome().status());
   }

   @Test
   void conformantResponsesOfTheMocksPass() throws Exception {
      runner.run(run("/rest/API+Pastry+-+2.0/2.0.0", Files.readString(API_PASTRY)), reporter);

      assertEquals(List.of("readPastry PASSED 200", "reprice PASSED 200"), described(reporter),
            () -> reporter.step("reprice").message() + " " + reporter.step("reprice").response());
      assertEquals(RunStatus.SUCCESS, reporter.outcome().status());
   }

   private static RunSpecification run(String mocks, String contract) throws IOException {
      return new RunSpecification("run-1", "readThenReprice", ResponseSchemaValidationTest.exampleOf("pastry-journeys"),
            Map.of("pastry", "Eclair Cafe", "newPrice", 2.6),
            Map.of("pastryApi",
                  new SourceTarget(URI.create(microcksUrl() + mocks), RunFixtures.PASTRY_OPERATIONS, contract)),
            RunFixtures.TIMEOUT, true);
   }

   private static List<String> described(RecordingReporter reporter) {
      return reporter.steps().stream().map(step -> step.stepId() + " " + step.status() + " " + step.statusCode())
            .toList();
   }

   private static void upload(String fileName, String content) throws Exception {
      String boundary = "arazzo-minion-" + System.nanoTime();
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
            + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
      body.writeBytes(content.getBytes(StandardCharsets.UTF_8));
      body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      HttpResponse<String> response = HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(URI.create(microcksUrl() + "/api/artifact/upload?mainArtifact=true"))
                  .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                  HttpResponse.BodyHandlers.ofString());
      assertEquals(201, response.statusCode(), response.body());
   }

   private static String microcksUrl() {
      return "http://" + MICROCKS.getHost() + ":" + MICROCKS.getMappedPort(8080);
   }
}
