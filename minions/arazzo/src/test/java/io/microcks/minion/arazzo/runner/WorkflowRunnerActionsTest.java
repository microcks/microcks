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
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance tests of S17: the minion retrying a step until it succeeds, or ending the workflow early, as the actions
 * of the steps say. They enter through the runner, with the stateful mocks of the Order status API as the HTTP side and
 * a pause that only moves the clock. The examples are in {@code step-actions.arazzo.yaml}.
 * @author SebastienDegodez
 */
class WorkflowRunnerActionsTest {

   private static final Map<String, OperationTarget> ORDER_STATUS_OPERATIONS = Map.of("getOrderStatus",
         new OperationTarget("GET", "/order/{id}/status"), "getOrder", new OperationTarget("GET", "/order/{id}"));
   private static final Duration ONE_SECOND = Duration.ofSeconds(1);

   private final ManualClock clock = new ManualClock();
   private final OrderStatusMocks mocks = new OrderStatusMocks(clock);
   private final List<Duration> pauses = new ArrayList<>();
   private final RecordingReporter reporter = new RecordingReporter();
   private final WorkflowRunner runner = new WorkflowRunner(mocks, clock, duration -> {
      pauses.add(duration);
      clock.advance(duration.toMillis());
   });

   /** AC-02: each attempt is its own slot, in execution order, one second after the previous one failed. */
   @Test
   void aStepIsRetriedAfterItsDelayUntilItsCriteriaHoldEachAttemptInItsOwnSlot() throws IOException {
      runner.run(runOf("order-tracking", "waitUntilReady", "42"), reporter);

      assertEquals(List.of("0 pollStatus FAILED 200 {}", "1 pollStatus FAILED 200 {}",
            "2 pollStatus PASSED 200 {status=ready}", "3 readOrder PASSED 200 {item=Eclair Cafe}"), described());
      assertEquals(List.of(ONE_SECOND, ONE_SECOND), pauses);
      assertEquals(new RunOutcome(RunStatus.SUCCESS, Map.of("status", "ready", "item", "Eclair Cafe"),
            4 * OrderStatusMocks.DELAY + 2000, null), reporter.outcome());
   }

   /** AC-02 (S6): each attempt reports what it sent and received. */
   @Test
   void eachAttemptReportsItsOwnExchangeAndCriteria() throws IOException {
      runner.run(runOf("order-tracking", "waitUntilReady", "42"), reporter);

      assertEquals(
            List.of("{\"status\":\"preparing\"} [true, false]", "{\"status\":\"preparing\"} [true, false]",
                  "{\"status\":\"ready\"} [true, true]", "{\"id\":\"42\",\"item\":\"Eclair Cafe\"} [true]"),
            reporter.steps().stream().map(
                  step -> step.response().body() + " " + step.criteria().stream().map(CriterionResult::held).toList())
                  .toList());
      assertEquals(mocks.calls(), reporter.steps().stream().map(StepResult::request).toList());
   }

   /**
    * AC-03: once its retries are exhausted, the step fails the run, its last attempt naming the unmet criterion; the
    * steps after it keep their slot, after the attempts, as not run.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "giveUpQuickly | 42 | 0 pollStatus FAILED 200 {}, 1 pollStatus FAILED 200 {} | 1",
         "waitUntilReady | 7 | 0 pollStatus FAILED 200 {}, 1 pollStatus FAILED 200 {}, 2 pollStatus FAILED 200 {}, "
               + "3 pollStatus FAILED 200 {}, 4 pollStatus FAILED 200 {}, 5 pollStatus FAILED 200 {}, "
               + "6 readOrder NOT_RUN null {} | 5" })
   void aStepWhoseRetriesAreExhaustedFailsTheRunNamingItsUnmetCriterion(String workflowId, String orderId, String steps,
         int retries) throws IOException {
      runner.run(runOf("order-tracking", workflowId, orderId), reporter);

      assertEquals(steps, String.join(", ", described()));
      assertEquals(retries, pauses.size());
      StepResult lastAttempt = reporter.steps().get(retries);
      assertEquals(
            new CriterionResult("$response.body#/status == 'ready'",
                  List.of(new ExpressionValue("$response.body#/status", "preparing")), false),
            lastAttempt.criteria().get(1));
      assertEquals(
            new RunOutcome(RunStatus.FAILURE, Map.of(), (retries + 1) * (OrderStatusMocks.DELAY + 1000) - 1000, null),
            reporter.outcome());
   }

   /**
    * AC-04: an end action whose criteria hold ends the workflow at its step: the later steps are not run, and the run
    * gives the outputs it has at that step. When its criteria do not hold, the workflow goes on.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "7 | 0 checkStatus PASSED 200 {status=preparing}, 1 readOrder NOT_RUN null {} | {status=preparing}",
         "1 | 0 checkStatus PASSED 200 {status=ready}, 1 readOrder PASSED 200 {item=Baba Rhum} | "
               + "{status=ready, item=Baba Rhum}" })
   void anEndActionWhoseCriteriaHoldEndsTheWorkflowAtItsStep(String orderId, String steps, String outputs)
         throws IOException {
      runner.run(runOf("order-tracking", "readUnlessPreparing", orderId), reporter);

      assertEquals(steps, String.join(", ", described()));
      assertEquals(RunStatus.SUCCESS, reporter.outcome().status());
      assertEquals(outputs, reporter.outcome().outputs().toString());
   }

   /** AC-04: a failure action ending the workflow fails the run at its step, without retrying it. */
   @Test
   void aFailureActionEndingTheWorkflowFailsTheRunAtItsStep() throws IOException {
      runner.run(runOf("order-tracking", "readUnlessPreparing", "999"), reporter);

      assertEquals(List.of("0 checkStatus FAILED 404 {}", "1 readOrder NOT_RUN null {}"), described());
      assertEquals(new RunOutcome(RunStatus.FAILURE, Map.of(), OrderStatusMocks.DELAY, null), reporter.outcome());
   }

   /**
    * The first failure action whose criteria hold applies: an unknown order ends the run, a preparing one is retried at
    * once, as the retry gives no delay, up to its limit.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "999 | 0 pollStatus FAILED 404 {}, 1 readOrder NOT_RUN null {}, 2 checkAgain NOT_RUN null {} | 0",
         "7 | 0 pollStatus FAILED 200 {}, 1 pollStatus FAILED 200 {}, 2 pollStatus FAILED 200 {}, "
               + "3 readOrder NOT_RUN null {}, 4 checkAgain NOT_RUN null {} | 2",
         "42 | 0 pollStatus FAILED 200 {}, 1 pollStatus FAILED 200 {}, 2 pollStatus PASSED 200 {}, "
               + "3 readOrder PASSED 200 {}, 4 checkAgain PASSED 200 {} | 2" })
   void theFirstFailureActionWhoseCriteriaHoldApplies(String orderId, String steps, int retries) throws IOException {
      runner.run(runOf("action-criteria", "retryWhilePreparing", orderId), reporter);

      assertEquals(steps, String.join(", ", described()));
      assertEquals(Collections.nCopies(retries, Duration.ZERO), pauses);
   }

   /** An action whose criteria cannot be evaluated, as they read a field the response does not have, does not apply. */
   @Test
   void anActionWhoseCriteriaCannotBeEvaluatedDoesNotApply() throws IOException {
      runner.run(runOf("action-criteria", "retryUnlessGone", "7"), reporter);

      assertEquals(List.of("0 pollStatus FAILED 200 {}", "1 pollStatus FAILED 200 {}"), described());
      assertEquals(List.of(Duration.ZERO), pauses);
   }

   /**
    * A step without response is retried by a retry action without criteria; an action whose criteria need the response
    * does not apply.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "order-tracking | waitUntilReady | 6 | 5",
         "action-criteria | retryWhilePreparing | 1 | 0" })
   void aStepWithoutResponseIsRetriedOnlyByAnActionWithoutCriteria(String example, String workflowId, int attempts,
         int retries) throws IOException {
      mocks.failWith(new ConnectException("Connection refused"));

      runner.run(runOf(example, workflowId, "42"), reporter);

      assertEquals(attempts, mocks.calls().size());
      assertEquals(retries, pauses.size());
      assertEquals("No response: java.net.ConnectException: Connection refused", reporter.steps().get(0).message());
      assertEquals(RunStatus.FAILURE, reporter.outcome().status());
   }

   /** A retry waits as long as it says, but not longer than a step waits for its response, nor less than nothing. */
   @ParameterizedTest
   @CsvSource({ "2.5, PT2.5S", "0, PT0S", "30, PT10S", "-1, PT0S" })
   void aRetryWaitsItsDelayBoundedByTheTimeoutOfTheSteps(String retryAfter, Duration pause) throws IOException {
      String document = exampleOf("retry-delays").replace("$retryAfter", retryAfter);

      runner.run(new RunSpecification("run-1", "pollEvery", document, Map.of("orderId", "7"), sources(),
            RunFixtures.TIMEOUT), reporter);

      assertEquals(List.of(pause), pauses);
      assertEquals(2, mocks.calls().size());
   }

   /** The minion pauses its thread between the attempts of a step. */
   @Test
   void theDefaultPauseSleepsForTheDelay() {
      long start = System.nanoTime();

      Pause.SLEEP.pause(Duration.ofMillis(30));

      assertEquals(true, System.nanoTime() - start >= Duration.ofMillis(30).toNanos());
   }

   /** An interrupted pause ends at once, and the thread stays interrupted so that its owner knows. */
   @Test
   void anInterruptedPauseEndsAtOnceAndKeepsTheThreadInterrupted() {
      Thread.currentThread().interrupt();
      long start = System.nanoTime();

      Pause.SLEEP.pause(Duration.ofSeconds(5));

      assertEquals(true, Thread.interrupted());
      assertEquals(true, System.nanoTime() - start < Duration.ofSeconds(5).toNanos());
   }

   private List<String> described() {
      return reporter.steps().stream().map(step -> step.index() + " " + step.stepId() + " " + step.status() + " "
            + step.statusCode() + " " + step.outputs()).toList();
   }

   private static RunSpecification runOf(String example, String workflowId, String orderId) throws IOException {
      Map<String, Object> inputs = new LinkedHashMap<>();
      inputs.put("orderId", orderId);
      return new RunSpecification("run-1", workflowId, exampleOf(example), inputs, sources(), RunFixtures.TIMEOUT);
   }

   private static Map<String, SourceTarget> sources() {
      return Map.of("orderStatusApi", new SourceTarget(OrderStatusMocks.BASE_URL, ORDER_STATUS_OPERATIONS));
   }

   private static String exampleOf(String example) throws IOException {
      try (InputStream stream = WorkflowRunnerActionsTest.class
            .getResourceAsStream("/io/microcks/minion/arazzo/runner/step-actions.arazzo.yaml")) {
         String marker = "# example: " + example + " ";
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.contains(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in the fixture"));
      }
   }
}
