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
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The runner of the minion playing the demo of S17, the Order tracking document, against the real mocks of the Order
 * status API, served by Microcks in a container: its SCRIPT dispatcher keeps its state in the store, answering
 * preparing, preparing, then ready for order 42.
 * @author SebastienDegodez
 */
@Testcontainers
class WorkflowRunnerActionsIT {

   private static final Path DEMO = Path.of("..", "..", "webapp", "src", "test", "resources", "io", "github",
         "microcks", "service", "arazzo");
   private static final Map<String, OperationTarget> ORDER_STATUS_OPERATIONS = Map.of("getOrderStatus",
         new OperationTarget("GET", "/order/{id}/status"), "getOrder", new OperationTarget("GET", "/order/{id}"));

   @Container
   private static final GenericContainer<?> MICROCKS = new GenericContainer<>(
         DockerImageName.parse("quay.io/microcks/microcks-uber:nightly")).withExposedPorts(8080)
               .waitingFor(Wait.forHttp("/api/health").forStatusCode(200));

   private final RecordingReporter reporter = new RecordingReporter();
   private final WorkflowRunner runner = new WorkflowRunner(new JdkHttpTransport(), Clock.systemUTC());

   @BeforeAll
   static void importOrderStatus() throws Exception {
      String boundary = "arazzo-minion-" + System.nanoTime();
      ByteArrayOutputStream body = new ByteArrayOutputStream();
      body.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; "
            + "filename=\"order-status.openapi.yaml\"\r\nContent-Type: application/octet-stream\r\n\r\n")
                  .getBytes(StandardCharsets.UTF_8));
      body.writeBytes(Files.readAllBytes(DEMO.resolve("order-status.openapi.yaml")));
      body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      HttpResponse<String> response = HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(URI.create(microcksUrl() + "/api/artifact/upload?mainArtifact=true"))
                  .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build(),
                  HttpResponse.BodyHandlers.ofString());
      assertEquals(201, response.statusCode(), response.body());
   }

   /** AC-02: pollStatus is retried every second until the order is ready, each attempt in its own slot. */
   @Test
   void aStepIsRetriedUntilTheStatefulMockAnswersReady() throws Exception {
      long start = System.nanoTime();

      runner.run(runOf("waitUntilReady", "42"), reporter);

      assertEquals(List.of("0 pollStatus FAILED preparing", "1 pollStatus FAILED preparing",
            "2 pollStatus PASSED ready", "3 readOrder PASSED Eclair Cafe"), described());
      assertEquals(new RunOutcome(RunStatus.SUCCESS, Map.of("status", "ready", "item", "Eclair Cafe"), 0, null),
            withoutTime(reporter.outcome()));
      assertEquals(true, System.nanoTime() - start >= Duration.ofSeconds(2).toNanos());
   }

   /** AC-03: with one retry only, the run fails at pollStatus after two attempts. */
   @Test
   void aStepWhoseRetriesAreExhaustedFailsTheRun() throws Exception {
      runner.run(runOf("giveUpQuickly", "7"), reporter);

      assertEquals(List.of("0 pollStatus FAILED preparing", "1 pollStatus FAILED preparing"), described());
      assertEquals(RunStatus.FAILURE, reporter.outcome().status());
   }

   /** AC-04: the end action ends the workflow while the order is preparing; readOrder is not run. */
   @Test
   void anEndActionEndsTheWorkflowAtItsStep() throws Exception {
      runner.run(runOf("readUnlessPreparing", "7"), reporter);

      assertEquals(List.of("0 checkStatus PASSED preparing", "1 readOrder NOT_RUN null"), described());
      assertEquals(new RunOutcome(RunStatus.SUCCESS, Map.of("status", "preparing"), 0, null),
            withoutTime(reporter.outcome()));
   }

   private List<String> described() {
      return reporter
            .steps().stream().map(
                  step -> step.index() + " " + step.stepId() + " " + step.status() + " "
                        + (step.response() == null ? null
                              : step.response().body().replaceAll(".*\"(status|item)\":\"([^\"]+)\".*", "$2")))
            .toList();
   }

   private static RunOutcome withoutTime(RunOutcome outcome) {
      return new RunOutcome(outcome.status(), outcome.outputs(), 0, outcome.message());
   }

   private static RunSpecification runOf(String workflowId, String orderId) throws Exception {
      return new RunSpecification("run-1", workflowId, Files.readString(DEMO.resolve("order-tracking.arazzo.yaml")),
            Map.of("orderId", orderId),
            Map.of("orderStatusApi",
                  new SourceTarget(URI.create(microcksUrl() + "/rest/Order+status/1.0.0"), ORDER_STATUS_OPERATIONS)),
            RunFixtures.TIMEOUT);
   }

   private static String microcksUrl() {
      return "http://" + MICROCKS.getHost() + ":" + MICROCKS.getMappedPort(8080);
   }
}
