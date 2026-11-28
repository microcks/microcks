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
import java.net.ConnectException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Acceptance tests of the minion running a workflow, entering through its runner with the mocks of API Pastry - 2.0 as
 * the HTTP side, and observing what the run reports. The examples are in {@code workflow-runs.arazzo.yaml}.
 * @author SebastienDegodez
 */
class WorkflowRunnerTest {

   private final ManualClock clock = new ManualClock();
   private final PastryMocks mocks = new PastryMocks(clock);
   private final RecordingReporter reporter = new RecordingReporter();
   private final WorkflowRunner runner = new WorkflowRunner(mocks, clock);

   @Test
   void aJourneyConsistentWithTheMocksPassesAndExposesItsOutputs() throws IOException {
      runner.run(
            RunFixtures.runOf("pastry-scenarios", "readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6)),
            reporter);

      assertEquals(List.of("0 readPastry PASSED 200 {name=Eclair Cafe, price=2.5}", "1 reprice PASSED 200 {price=2.6}"),
            reporter.steps().stream().map(WorkflowRunnerTest::describe).toList());
      assertEquals(new RunOutcome(RunStatus.SUCCESS, Map.of("before", 2.5, "after", 2.6), 2 * PastryMocks.DELAY, null),
            reporter.outcome());
   }

   @Test
   void theRunStopsAtTheFirstFailingStepAndNamesTheUnmetCriterion() throws IOException {
      runner.run(RunFixtures.runOf("pastry-scenarios", "listThenReadFirst", Map.of()), reporter);

      assertEquals(List.of("0 list PASSED 200 {first=Baba Rhum}", "1 readFirst FAILED 400 {}"),
            reporter.steps().stream().map(WorkflowRunnerTest::describe).toList());
      assertEquals(
            List.of(new CriterionResult("$statusCode == 200", List.of(new ExpressionValue("$statusCode", 400)), false)),
            reporter.step("readFirst").criteria());
      assertNull(reporter.step("readFirst").message());
      assertEquals(new RunOutcome(RunStatus.FAILURE, Map.of(), 2 * PastryMocks.DELAY, null), reporter.outcome());
   }

   /**
    * A server error fails its step on the status criterion it does not meet, though a later criterion of the step reads
    * a body member the error does not have: that criterion does not hold, and the step tells why.
    */
   @Test
   void aServerErrorFailsTheStepOnItsUnmetStatusCriterionAndEveryCriterionIsReported() throws IOException {
      mocks.failReprice();

      runner.run(
            RunFixtures.runOf("pastry-scenarios", "readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6)),
            reporter);

      assertEquals(List.of("0 readPastry PASSED 200 {name=Eclair Cafe, price=2.5}", "1 reprice FAILED 500 {}"),
            reporter.steps().stream().map(WorkflowRunnerTest::describe).toList());
      assertEquals(
            List.of(new CriterionResult("$statusCode == 200", List.of(new ExpressionValue("$statusCode", 500)), false),
                  new CriterionResult("$response.body#/price == $inputs.newPrice", List.of(), false)),
            reporter.step("reprice").criteria());
      assertEquals("No value at JSON pointer /price in $response.body", reporter.step("reprice").message());
      assertEquals(500, reporter.step("reprice").response().status());
      assertEquals(RunStatus.FAILURE, reporter.outcome().status());
   }

   @Test
   void eachStepCallsTheOperationOfItsSourceWithItsParametersAndBody() throws IOException {
      runner.run(
            RunFixtures.runOf("pastry-scenarios", "readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6)),
            reporter);
      runner.run(RunFixtures.runOf("pastry-scenarios", "listThenReadFirst", Map.of()), new RecordingReporter());

      String base = PastryMocks.BASE_URL.toString();
      assertEquals(
            List.of("GET " + base + "/pastry/Eclair%20Cafe {Accept=application/json} null",
                  "PATCH " + base + "/pastry/Eclair%20Cafe {Accept=application/json, Content-Type=application/json} "
                        + "{\"price\":2.6}",
                  "GET " + base + "/pastry?size=S {} null", "GET " + base + "/pastry/Baba%20Rhum {} null"),
            mocks.calls().stream()
                  .map(call -> call.method() + " " + call.uri() + " " + call.headers() + " " + call.body()).toList());
      assertEquals(List.of(RunFixtures.TIMEOUT, RunFixtures.TIMEOUT, RunFixtures.TIMEOUT, RunFixtures.TIMEOUT),
            mocks.timeouts());
   }

   @Test
   void eachStepIsReportedWithItsOperationCriteriaExchangeAndTiming() throws IOException {
      runner.run(
            RunFixtures.runOf("pastry-scenarios", "readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6)),
            reporter);

      StepResult readPastry = reporter.step("readPastry");
      assertEquals(new StepOperation("pastryApi", "GetPastryByName", "GET", "/pastry/{name}"), readPastry.operation());
      assertEquals(
            List.of(
                  new CriterionResult("$statusCode == 200 && $response.body#/status == 'available'",
                        List.of(new ExpressionValue("$statusCode", 200),
                              new ExpressionValue("$response.body#/status", "available")),
                        true)),
            readPastry.criteria());
      assertEquals(mocks.calls().get(0), readPastry.request());
      assertEquals(200, readPastry.response().status());
      assertEquals(PastryMocks.DELAY, readPastry.elapsedTime());
      assertEquals(new StepOperation("pastryApi", "PatchPastry", "PATCH", "/pastry/{name}"),
            reporter.step("reprice").operation());
   }

   /**
    * S30, AC-04: steps declaring the steps they depend on (dependsOn, Arazzo 1.1) are played in the order of the list,
    * which satisfies their dependsOn, and the run passes.
    */
   @Test
   void stepsDeclaringDependsOnArePlayedInTheOrderOfTheListAndPass() throws IOException {
      runner.run(RunFixtures.runOf("depends-on", "tasteThenReprice", Map.of()), reporter);

      assertEquals(
            List.of("0 readMenu PASSED 200 {}", "1 tasteEclair PASSED 200 {name=Eclair Cafe}",
                  "2 tasteAgain PASSED 200 {}", "3 readMenuAgain PASSED 200 {}", "4 reprice PASSED 200 {price=2.6}"),
            reporter.steps().stream().map(WorkflowRunnerTest::describe).toList());
      assertEquals(RunStatus.SUCCESS, reporter.outcome().status());
   }

   @Test
   void aStepAfterTheFailingOneKeepsItsSlotAsNotRun() throws IOException {
      runner.run(RunFixtures.runOf("not-run-after-failure", "listReadFirstThenReprice", Map.of()), reporter);

      assertEquals(
            List.of("0 list PASSED 200 {first=Baba Rhum}", "1 readFirst FAILED 400 {}", "2 reprice NOT_RUN null {}"),
            reporter.steps().stream().map(WorkflowRunnerTest::describe).toList());
      StepResult reprice = reporter.step("reprice");
      assertEquals(
            new StepResult(2, "reprice", new StepOperation("pastryApi", "PatchPastry", "PATCH", "/pastry/{name}"),
                  StepStatus.NOT_RUN, null, 0, List.of(), Map.of(), null, null, null, List.of(), Map.of()),
            reprice);
      assertEquals(2, mocks.calls().size());
   }

   @Test
   void theFailingStepReportsWhatItSentAndReceived() throws IOException {
      runner.run(RunFixtures.runOf("pastry-scenarios", "listThenReadFirst", Map.of()), reporter);

      StepResult readFirst = reporter.step("readFirst");
      assertEquals("GET " + PastryMocks.BASE_URL + "/pastry/Baba%20Rhum",
            readFirst.request().method() + " " + readFirst.request().uri());
      assertEquals("400 The response /pastry/Baba%20Rhum does not exist!",
            readFirst.response().status() + " " + readFirst.response().body());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "readThenReprice | readPastry | name in path = Eclair Cafe from $inputs.pastry, Accept in header = "
               + "application/json from null | {name=$response.body#/name, price=$response.body#/price}",
         "readThenReprice | reprice | name in path = Eclair Cafe from $steps.readPastry.outputs.name, Accept in "
               + "header = application/json from null | {price=$response.body#/price}",
         "listThenReadFirst | list | size in query = S from null | {first=$response.body#/0/name}" })
   void eachStepReportsTheParametersItSentAndWhereItsOutputsCameFrom(String workflowId, String stepId,
         String parameters, String outputOrigins) throws IOException {
      runner.run(RunFixtures.runOf("pastry-scenarios", workflowId, Map.of("pastry", "Eclair Cafe", "newPrice", 2.6)),
            reporter);

      StepResult step = reporter.step(stepId);
      assertEquals(parameters, String.join(", ", step.parameters().stream().map(parameter -> parameter.name() + " in "
            + parameter.in() + " = " + parameter.value() + " from " + parameter.expression()).toList()));
      assertEquals(outputOrigins, step.outputOrigins().toString());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "missingInput | 0 readPastry FAILED null {} | Unknown input: pastry | 0 | FAILURE | ",
         "textBodyCriterion | 0 readMissing FAILED 400 {} | No value at JSON pointer /status in $response.body | 1 "
               + "| FAILURE | ",
         "textPayload | 0 reprice PASSED 200 {} | | 1 | SUCCESS | ",
         "unknownOperation | 0 deletePastry FAILED null {} | Operation DeletePastry not found in source pastryApi | 0 "
               + "| FAILURE | ",
         "missingOutput | 0 list PASSED 200 {} | | 1 | FAILURE | Unknown output of step list: first" })
   void aStepOrOutputWhoseExpressionsHaveNoValueFailsWithTheReason(String workflowId, String step, String stepMessage,
         int calls, RunStatus status, String runMessage) throws IOException {
      runner.run(RunFixtures.runOf("unexecutable", workflowId, Map.of()), reporter);

      assertEquals(List.of(step), reporter.steps().stream().map(WorkflowRunnerTest::describe).toList());
      assertEquals(stepMessage, reporter.steps().get(0).message());
      assertEquals(calls, mocks.calls().size());
      assertEquals(status, reporter.outcome().status());
      assertEquals(runMessage, reporter.outcome().message());
   }

   @Test
   void aTextPayloadIsSentAsIs() throws IOException {
      runner.run(RunFixtures.runOf("unexecutable", "textPayload", Map.of()), reporter);

      assertEquals("price is 2.6", mocks.calls().get(0).body());
      assertEquals(Map.of("Content-Type", "text/plain"), mocks.calls().get(0).headers());
   }

   @Test
   void aStepWithoutResponseFailsWithTheReason() throws IOException {
      mocks.failWith(new ConnectException("Connection refused"));

      runner.run(RunFixtures.runOf("pastry-scenarios", "listThenReadFirst", Map.of()), reporter);

      assertEquals(List.of("0 list FAILED null {}", "1 readFirst NOT_RUN null {}"),
            reporter.steps().stream().map(WorkflowRunnerTest::describe).toList());
      assertEquals("No response: java.net.ConnectException: Connection refused", reporter.step("list").message());
      assertEquals(PastryMocks.DELAY, reporter.step("list").elapsedTime());
      assertNull(reporter.step("list").response());
   }

   @Test
   void aRegexCriterionMatchesItsContext() throws IOException {
      runner.run(RunFixtures.runOf("regex-criterion", "readJson", Map.of()), reporter);

      assertEquals(
            List.of(
                  new CriterionResult("^application/json",
                        List.of(new ExpressionValue("$response.header.content-type", "application/json")), true),
                  new CriterionResult("xml",
                        List.of(new ExpressionValue("$response.header.Content-Type", "application/json")), false)),
            reporter.step("readPastry").criteria());
      assertEquals(StepStatus.FAILED, reporter.step("readPastry").status());
   }

   @Test
   void aWorkflowAbsentFromTheDocumentDoesNotRun() throws IOException {
      runner.run(RunFixtures.runOf("pastry-scenarios", "deletePastry", Map.of()), reporter);

      assertEquals(List.of(), reporter.steps());
      assertEquals(new RunOutcome(RunStatus.FAILURE, Map.of(), 0, "Workflow deletePastry not found"),
            reporter.outcome());
   }

   @Test
   void anUnreadableDocumentDoesNotRun() {
      runner.run(
            new RunSpecification("run-1", "readThenReprice", "arazzo: [1.0.1", Map.of(), Map.of(), RunFixtures.TIMEOUT),
            reporter);

      assertEquals(new RunOutcome(RunStatus.FAILURE, Map.of(), 0, "Workflow readThenReprice not found"),
            reporter.outcome());
   }

   private static String describe(StepResult step) {
      return step.index() + " " + step.stepId() + " " + step.status() + " " + step.statusCode() + " " + step.outputs();
   }
}
