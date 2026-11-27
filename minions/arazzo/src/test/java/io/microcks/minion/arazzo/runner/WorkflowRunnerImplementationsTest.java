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

import io.microcks.minion.arazzo.client.dto.OperationDTO;
import io.microcks.minion.arazzo.client.dto.RunRequestDTO;
import io.microcks.minion.arazzo.client.dto.SecretDTO;
import io.microcks.minion.arazzo.client.dto.SourceDTO;
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
 * Acceptance tests of the minion running a workflow against implementations, entering through its runner with the
 * staging implementations of the Customer and Order APIs as the HTTP side: each step calls the base URL of its own
 * source, and every call carries the authorization of the secret of the run. The examples are in
 * {@code implementation-runs.arazzo.yaml}.
 * @author SebastienDegodez
 */
class WorkflowRunnerImplementationsTest {

   private static final Map<String, OperationTarget> CUSTOMER_OPERATIONS = Map.of("GetCustomer",
         new OperationTarget("GET", "/customers/{id}"));
   private static final Map<String, OperationTarget> ORDER_OPERATIONS = Map.of("CreateOrder",
         new OperationTarget("POST", "/orders"));

   private final StagingImplementations implementations = new StagingImplementations();
   private final RecordingReporter reporter = new RecordingReporter();
   private final WorkflowRunner runner = new WorkflowRunner(implementations, Clock.systemUTC());

   @Test
   void eachStepCallsTheBaseUrlSetForItsSource() throws IOException {
      runner.run(placeOrder(RunSecret.NONE), reporter);

      assertEquals(
            List.of("GET " + StagingImplementations.CUSTOMERS + "/customers/c-42",
                  "POST " + StagingImplementations.ORDERS + "/orders"),
            implementations.calls().stream().map(call -> call.method() + " " + call.uri()).toList());
      assertEquals(List.of("getCustomer PASSED 200", "createOrder PASSED 201"), reporter.steps().stream()
            .map(step -> step.stepId() + " " + step.status() + " " + step.statusCode()).toList());
      assertEquals(new RunOutcome(RunStatus.SUCCESS, Map.of("orderId", "o-7"), reporter.outcome().elapsedTime(), null),
            reporter.outcome());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = {
         "null  | null      | s3cr3t-t0k3n | X-Pastry-Token | X-Pastry-Token=s3cr3t-t0k3n",
         "null  | null      | s3cr3t-t0k3n | ' X-Pastry-Token ' | X-Pastry-Token=s3cr3t-t0k3n",
         "null  | null      | s3cr3t-t0k3n | null           | Authorization=Bearer s3cr3t-t0k3n",
         "null  | null      | s3cr3t-t0k3n | '  '           | Authorization=Bearer s3cr3t-t0k3n",
         "baker | croissant | null         | null           | Authorization=Basic YmFrZXI6Y3JvaXNzYW50",
         "baker | croissant | s3cr3t-t0k3n | null           | Authorization=Bearer s3cr3t-t0k3n",
         "baker | croissant | s3cr3t-t0k3n | X-Pastry-Token | Authorization=Basic YmFrZXI6Y3JvaXNzYW50, "
               + "X-Pastry-Token=s3cr3t-t0k3n",
         "baker | null      | null         | null           | ''", "null  | croissant | null | null | ''" })
   void aSelectedSecretAuthenticatesEveryCall(String username, String password, String token, String tokenHeader,
         String authorization) throws IOException {
      runner.run(placeOrder(new RunSecret(username, password, token, tokenHeader)), reporter);

      assertEquals(List.of(authorization, authorization),
            implementations.calls().stream().map(WorkflowRunnerImplementationsTest::authorizationOf).toList());
      assertEquals("application/json", implementations.calls().get(1).headers().get("Content-Type"));
   }

   @Test
   void theSecretAuthenticatesOnlyTheCallsToImplementationsOfAMixedRun() throws IOException {
      Map<String, SourceTarget> sources = new LinkedHashMap<>();
      sources.put("customerApi", new SourceTarget(URI.create(StagingImplementations.CUSTOMERS), CUSTOMER_OPERATIONS));
      sources.put("orderApi", SourceTarget.onMocks(URI.create(StagingImplementations.ORDERS), ORDER_OPERATIONS));

      runner.run(run("order-fulfilment", "placeOrder", Map.of("customerId", "c-42"), sources,
            new RunSecret(null, null, "s3cr3t-t0k3n", null)), reporter);

      assertEquals(List.of("Authorization=Bearer s3cr3t-t0k3n", ""),
            implementations.calls().stream().map(WorkflowRunnerImplementationsTest::authorizationOf).toList());
      assertEquals(List.of("Authorization=Bearer <value-from-secret>", ""),
            reporter.steps().stream().map(step -> authorizationOf(step.request())).toList());
   }

   @Test
   void aRunRequestOfMicrocksSendsTheSecretToItsImplementationsButNotToItsMocks() throws IOException {
      Map<String, SourceDTO> sources = new LinkedHashMap<>();
      sources.put("customerApi", new SourceDTO(StagingImplementations.CUSTOMERS,
            Map.of("GetCustomer", new OperationDTO("GET", "/customers/{id}"))));
      sources.put("orderApi",
            new SourceDTO("/rest/Order+API/1.0.0", Map.of("CreateOrder", new OperationDTO("POST", "/orders"))));
      RunRequestDTO request = new RunRequestDTO("run-1", "placeOrder", exampleOf("order-fulfilment"),
            Map.of("customerId", "c-42"), sources, 10000,
            new SecretDTO("customers-staging-token", null, null, "s3cr3t-t0k3n", null));

      runner.run(request.toSpecification(StagingImplementations.ORDERS), reporter);

      assertEquals(
            List.of("GET " + StagingImplementations.CUSTOMERS + "/customers/c-42 Authorization=Bearer s3cr3t-t0k3n",
                  "POST " + StagingImplementations.ORDERS + "/rest/Order+API/1.0.0/orders "),
            implementations.calls().stream()
                  .map(call -> call.method() + " " + call.uri() + " " + authorizationOf(call)).toList());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = {
         "null  | null      | s3cr3t-t0k3n | X-Pastry-Token | X-Pastry-Token=<value-from-secret>",
         "null  | null      | s3cr3t-t0k3n | null           | Authorization=Bearer <value-from-secret>",
         "baker | croissant | null         | null           | Authorization=Basic <value-from-secret>" })
   void theValuesOfTheSecretAreNotReportedButTracedAsContractTestsDo(String username, String password, String token,
         String tokenHeader, String traced) throws IOException {
      runner.run(placeOrder(new RunSecret(username, password, token, tokenHeader)), reporter);

      assertEquals(List.of(traced, traced),
            reporter.steps().stream().map(step -> authorizationOf(step.request())).toList());
   }

   @Test
   void theSecretReplacesTheAuthorizationAStepSetsItself() throws IOException {
      runner.run(run("own-authorization", "readCustomerWithItsOwnAuthorization", Map.of(),
            Map.of("customerApi", new SourceTarget(URI.create(StagingImplementations.CUSTOMERS), CUSTOMER_OPERATIONS)),
            new RunSecret(null, null, "s3cr3t-t0k3n", null)), reporter);

      assertEquals(Map.of("Authorization", "Bearer s3cr3t-t0k3n"), implementations.calls().get(0).headers());
      assertEquals(Map.of("Authorization", "Bearer <value-from-secret>"),
            reporter.step("getCustomer").request().headers());
   }

   @Test
   void aStepKeepsItsOwnAuthorizationWithoutSecret() throws IOException {
      runner.run(run("own-authorization", "readCustomerWithItsOwnAuthorization", Map.of(),
            Map.of("customerApi", new SourceTarget(URI.create(StagingImplementations.CUSTOMERS), CUSTOMER_OPERATIONS)),
            RunSecret.NONE), reporter);

      assertEquals(Map.of("authorization", "Basic c3RlcDpvd24="), implementations.calls().get(0).headers());
      assertEquals(Map.of("authorization", "Basic c3RlcDpvd24="), reporter.step("getCustomer").request().headers());
   }

   /** placeOrder of Order fulfilment, against the staging implementations of its two sources. */
   private static RunSpecification placeOrder(RunSecret secret) throws IOException {
      Map<String, SourceTarget> sources = new LinkedHashMap<>();
      sources.put("customerApi", new SourceTarget(URI.create(StagingImplementations.CUSTOMERS), CUSTOMER_OPERATIONS));
      sources.put("orderApi", new SourceTarget(URI.create(StagingImplementations.ORDERS), ORDER_OPERATIONS));
      return run("order-fulfilment", "placeOrder", Map.of("customerId", "c-42"), sources, secret);
   }

   private static RunSpecification run(String example, String workflowId, Map<String, Object> inputs,
         Map<String, SourceTarget> sources, RunSecret secret) throws IOException {
      return new RunSpecification("run-1", workflowId, exampleOf(example), inputs, sources, RunFixtures.TIMEOUT,
            secret);
   }

   /** The headers of a call that are not about its content, sorted by name. */
   private static String authorizationOf(HttpCall call) {
      return call.headers().entrySet().stream().filter(header -> !header.getKey().equals("Content-Type"))
            .map(header -> header.getKey() + "=" + header.getValue()).sorted().reduce((a, b) -> a + ", " + b)
            .orElse("");
   }

   private static String exampleOf(String example) throws IOException {
      try (InputStream stream = WorkflowRunnerImplementationsTest.class
            .getResourceAsStream("/io/microcks/minion/arazzo/runner/implementation-runs.arazzo.yaml")) {
         String marker = "# example: " + example + " ";
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.contains(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in the fixture"));
      }
   }
}
