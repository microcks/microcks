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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance tests of S29: each call a run makes to the Microcks mocks carries the W3C baggage telling Live Traces
 * which run, workflow, step and attempt it comes from; a call to an implementation carries none. They enter through the
 * runner and observe the calls sent and the requests reported. The examples of their own are in
 * {@code live-traces.arazzo.yaml}.
 * @author SebastienDegodez
 */
class WorkflowRunnerLiveTracesTest {

   private static final Map<String, OperationTarget> ORDER_STATUS_OPERATIONS = Map.of("getOrderStatus",
         new OperationTarget("GET", "/order/{id}/status"), "getOrder", new OperationTarget("GET", "/order/{id}"));

   private final ManualClock clock = new ManualClock();
   private final RecordingReporter reporter = new RecordingReporter();

   /** AC-01: each call to the mocks says which run, workflow, step and attempt it comes from. */
   @Test
   void eachCallToTheMocksCarriesTheBaggageOfItsRunWorkflowStepAndAttempt() throws IOException {
      OrderStatusMocks mocks = new OrderStatusMocks(clock);
      Map<String, Object> inputs = new LinkedHashMap<>();
      inputs.put("orderId", "42");
      RunSpecification run = new RunSpecification("6719f0c2a1b2c3d4e5f60718", "waitUntilReady",
            exampleOf("step-actions.arazzo.yaml", "order-tracking"), inputs,
            Map.of("orderStatusApi",
                  SourceTarget.onMocks(OrderStatusMocks.BASE_URL, ORDER_STATUS_OPERATIONS)),
            RunFixtures.TIMEOUT);

      new WorkflowRunner(mocks, clock, duration -> clock.advance(duration.toMillis())).run(run, reporter);

      String marks = "microcks-arazzo-run=6719f0c2a1b2c3d4e5f60718,microcks-arazzo-workflow=waitUntilReady,";
      assertEquals(
            List.of(marks + "microcks-arazzo-step=pollStatus,microcks-arazzo-attempt=1",
                  marks + "microcks-arazzo-step=pollStatus,microcks-arazzo-attempt=2",
                  marks + "microcks-arazzo-step=pollStatus,microcks-arazzo-attempt=3",
                  marks + "microcks-arazzo-step=readOrder,microcks-arazzo-attempt=1"),
            mocks.calls().stream().map(call -> call.headers().get("baggage")).toList());
   }

   /** AC-05: the request a step reports is the one it sent, baggage included. */
   @Test
   void theRequestReportedForAStepShowsTheBaggageItSent() throws IOException {
      PastryMocks mocks = new PastryMocks(clock);

      new WorkflowRunner(mocks, clock).run(againstTheMocks("run-1", "pastry-scenarios", "readThenReprice",
            Map.of("pastry", "Eclair Cafe", "newPrice", 2.6)), reporter);

      assertEquals(Map.of("Accept", "application/json", "baggage",
            "microcks-arazzo-run=run-1,microcks-arazzo-workflow=readThenReprice,microcks-arazzo-step=readPastry,"
                  + "microcks-arazzo-attempt=1"),
            reporter.step("readPastry").request().headers());
      assertEquals(mocks.calls(), reporter.steps().stream().map(StepResult::request).toList());
   }

   /** AC-01: a run against implementations leaves their calls as the steps describe them. */
   @Test
   void noCallToAnImplementationCarriesTheBaggage() throws IOException {
      StagingImplementations implementations = new StagingImplementations();
      Map<String, SourceTarget> sources = new LinkedHashMap<>();
      sources.put("customerApi", new SourceTarget(URI.create(StagingImplementations.CUSTOMERS),
            Map.of("GetCustomer", new OperationTarget("GET", "/customers/{id}"))));
      sources.put("orderApi", new SourceTarget(URI.create(StagingImplementations.ORDERS),
            Map.of("CreateOrder", new OperationTarget("POST", "/orders"))));

      new WorkflowRunner(implementations, Clock.systemUTC()).run(new RunSpecification("run-1", "placeOrder",
            exampleOf("implementation-runs.arazzo.yaml", "order-fulfilment"), Map.of("customerId", "c-42"), sources,
            RunFixtures.TIMEOUT), reporter);

      assertEquals(List.of(Map.of(), Map.of("Content-Type", "application/json")),
            implementations.calls().stream().map(HttpCall::headers).toList());
   }

   /**
    * A step sending a baggage of its own keeps it, whatever the case of its name: the entries of the run are added to
    * it. Values a W3C baggage cannot carry as is are percent-encoded.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "run-1   | own-baggage  | readWithItsOwnBaggage | Baggage | tenant=bakery,microcks-arazzo-run=run-1,"
               + "microcks-arazzo-workflow=readWithItsOwnBaggage,microcks-arazzo-step=readPastry,"
               + "microcks-arazzo-attempt=1",
         "'run 1' | unusual-ids  | 'read pastry;fast'    | baggage | microcks-arazzo-run=run%201,"
               + "microcks-arazzo-workflow=read%20pastry%3Bfast,microcks-arazzo-step=read%2Conce,"
               + "microcks-arazzo-attempt=1" })
   void theBaggageOfTheRunIsAddedToTheOneOfTheStepAndEncoded(String runId, String example, String workflowId,
         String header, String baggage) throws IOException {
      PastryMocks mocks = new PastryMocks(clock);

      new WorkflowRunner(mocks, clock)
            .run(againstTheMocks(runId, example, workflowId, Map.of(), "live-traces.arazzo.yaml"), reporter);

      assertEquals(Map.of(header, baggage), mocks.calls().get(0).headers());
   }

   private static RunSpecification againstTheMocks(String runId, String example, String workflowId,
         Map<String, Object> inputs) throws IOException {
      return againstTheMocks(runId, example, workflowId, inputs, "workflow-runs.arazzo.yaml");
   }

   private static RunSpecification againstTheMocks(String runId, String example, String workflowId,
         Map<String, Object> inputs, String fixture) throws IOException {
      return new RunSpecification(runId, workflowId, exampleOf(fixture, example), inputs,
            Map.of("pastryApi",
                  SourceTarget.onMocks(PastryMocks.BASE_URL, RunFixtures.PASTRY_OPERATIONS)),
            RunFixtures.TIMEOUT);
   }

   private static String exampleOf(String fixture, String example) throws IOException {
      try (InputStream stream = WorkflowRunnerLiveTracesTest.class
            .getResourceAsStream("/io/microcks/minion/arazzo/runner/" + fixture)) {
         String marker = "# example: " + example + " ";
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.contains(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in the fixture"));
      }
   }
}
