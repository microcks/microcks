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
 * The runner of the minion against the real mocks of API Pastry - 2.0, served by Microcks in a container from the
 * APIPastry-openapi.yaml sample.
 * @author SebastienDegodez
 */
@Testcontainers
class WorkflowRunnerIT {

   private static final Path API_PASTRY = Path.of("..", "..", "samples", "APIPastry-openapi.yaml");

   @Container
   private static final GenericContainer<?> MICROCKS = new GenericContainer<>(
         DockerImageName.parse("quay.io/microcks/microcks-uber:nightly")).withExposedPorts(8080)
               .waitingFor(Wait.forHttp("/api/health").forStatusCode(200));

   private final RecordingReporter reporter = new RecordingReporter();
   private final WorkflowRunner runner = new WorkflowRunner(new JdkHttpTransport(), Clock.systemUTC());

   @BeforeAll
   static void importApiPastry() throws Exception {
      String boundary = "arazzo-minion-" + System.nanoTime();
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
            + "filename=\"APIPastry-openapi.yaml\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                  .getBytes(StandardCharsets.UTF_8));
      body.writeBytes(Files.readAllBytes(API_PASTRY));
      body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      HttpResponse<String> response = HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(URI.create(microcksUrl() + "/api/artifact/upload?mainArtifact=true"))
                  .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                  HttpResponse.BodyHandlers.ofString());
      assertEquals(201, response.statusCode(), response.body());
   }

   @Test
   void aJourneyConsistentWithTheMocksPassesAndExposesItsOutputs() throws Exception {
      runner.run(runAgainstTheMocks("readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6)), reporter);

      assertEquals(List.of("readPastry PASSED 200", "reprice PASSED 200"), described(reporter));
      assertEquals(RunStatus.SUCCESS, reporter.outcome().status());
      assertEquals(Map.of("before", 2.5, "after", 2.6), reporter.outcome().outputs());
   }

   @Test
   void theRunStopsAtTheFirstFailingStepAndNamesTheUnmetCriterion() throws Exception {
      runner.run(runAgainstTheMocks("listThenReadFirst", Map.of()), reporter);

      assertEquals(List.of("list PASSED 200", "readFirst FAILED 400"), described(reporter));
      assertEquals(Map.of("first", "Baba Rhum"), reporter.step("list").outputs());
      assertEquals(
            List.of(new CriterionResult("$statusCode == 200", List.of(new ExpressionValue("$statusCode", 400)), false)),
            reporter.step("readFirst").criteria());
      assertEquals(RunStatus.FAILURE, reporter.outcome().status());
   }

   @Test
   void theFailingStepShowsWhatItSentAndReceivedAndWhereItsValuesCameFrom() throws Exception {
      runner.run(runAgainstTheMocks("listThenReadFirst", Map.of()), reporter);

      StepResult readFirst = reporter.step("readFirst");
      assertEquals(microcksUrl() + "/rest/API+Pastry+-+2.0/2.0.0/pastry/Baba%20Rhum",
            readFirst.request().uri().toString());
      assertEquals(400, readFirst.response().status());
      assertEquals(List.of(new ParameterValue("name", "path", "Baba Rhum", "$steps.list.outputs.first")),
            readFirst.parameters());
      assertEquals(Map.of("first", "$response.body#/0/name"), reporter.step("list").outputOrigins());
   }

   private static RunSpecification runAgainstTheMocks(String workflowId, Map<String, Object> inputs) throws Exception {
      return new RunRequestOf(RunFixtures.runOf("pastry-scenarios", workflowId, inputs))
            .against(URI.create(microcksUrl() + "/rest/API+Pastry+-+2.0/2.0.0"));
   }

   private static List<String> described(RecordingReporter reporter) {
      return reporter.steps().stream().map(step -> step.stepId() + " " + step.status() + " " + step.statusCode())
            .toList();
   }

   private static String microcksUrl() {
      return "http://" + MICROCKS.getHost() + ":" + MICROCKS.getMappedPort(8080);
   }

   /** A run of the fixtures, against other mocks of API Pastry. */
   private record RunRequestOf(RunSpecification specification) {

      RunSpecification against(URI baseUrl) {
         return new RunSpecification(specification.runId(), specification.workflowId(), specification.arazzoDocument(),
               specification.inputs(), Map.of("pastryApi", new SourceTarget(baseUrl, RunFixtures.PASTRY_OPERATIONS)),
               specification.timeout());
      }
   }
}
