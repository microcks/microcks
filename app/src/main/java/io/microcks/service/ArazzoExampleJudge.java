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
package io.microcks.service;

import io.microcks.domain.ArazzoStepExpectation;
import io.microcks.domain.ArazzoWorkflowExample;
import io.microcks.domain.WorkflowExampleCheck;
import io.microcks.domain.WorkflowExampleVerdict;
import io.microcks.domain.WorkflowResult;
import io.microcks.domain.WorkflowRunStatus;
import io.microcks.domain.WorkflowStepResult;
import io.microcks.domain.WorkflowStepStatus;
import io.microcks.util.ObjectMapperFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Judges whether an ended run matches the recorded example it played, as OpenAPI contract tests judge an API example:
 * the exact status of each listed step, then where the workflow stopped, then the listed outputs, compared as JSON
 * values. Outputs the example does not list are not checked.
 * @author SebastienDegodez
 */
final class ArazzoExampleJudge {

   private static final ObjectMapper JSON = ObjectMapperFactory.getJsonObjectMapper();
   private static final String ABSENT = "absent";

   /** JSON values are equal when they are, or when both are numbers of the same value however they are written. */
   private static final Comparator<JsonNode> SAME_VALUE = (expected, actual) -> {
      if (expected.equals(actual) || expected.isNumber() && actual.isNumber()
            && expected.decimalValue().compareTo(actual.decimalValue()) == 0) {
         return 0;
      }
      return 1;
   };

   private ArazzoExampleJudge() {
   }

   /**
    * @param example A recorded example of a workflow
    * @return What a run playing the example expects, not judged yet
    */
   static WorkflowExampleCheck expecting(ArazzoWorkflowExample example) {
      return new WorkflowExampleCheck(example.name(), example.outputs(), example.steps(), example.stopsAt(), null,
            List.of());
   }

   /**
    * @param example The example a run played, with what it expects
    * @param run     The run, ended
    * @return The example judged on the run
    */
   static WorkflowExampleCheck judge(WorkflowExampleCheck example, WorkflowResult run) {
      List<String> unmet = new ArrayList<>();
      for (ArazzoStepExpectation expectation : example.steps()) {
         unmet.addAll(unmetBy(expectation, lastCallOf(run, expectation.stepId())));
      }
      String stop = stopOf(run);
      if (example.stopsAt() == null && stop != null) {
         unmet.add("Expected to run to its end but " + stop);
      }
      if (example.stopsAt() != null && !("stopped at " + example.stopsAt()).equals(stop)) {
         unmet.add("Expected to stop at " + example.stopsAt() + " but "
               + (stop == null ? "the workflow ran to its end" : stop));
      }
      unmet.addAll(unmetOutputs("Output ", example.outputs(), run.getOutputs()));
      // As in OpenAPI contract tests, the run matches the example when it meets every expectation.
      return new WorkflowExampleCheck(example.name(), example.outputs(), example.steps(), example.stopsAt(),
            unmet.isEmpty() ? WorkflowExampleVerdict.MATCHES : WorkflowExampleVerdict.DOES_NOT_MATCH,
            List.copyOf(unmet));
   }

   /** A step called several times is judged on its last call. */
   private static Optional<WorkflowStepResult> lastCallOf(WorkflowResult run, String stepId) {
      return run.getSteps().stream()
            .filter(step -> step.stepId().equals(stepId) && step.status() != WorkflowStepStatus.NOT_RUN)
            .reduce((first, second) -> second);
   }

   private static List<String> unmetBy(ArazzoStepExpectation expectation, Optional<WorkflowStepResult> call) {
      String prefix = "Step " + expectation.stepId();
      if (call.isEmpty()) {
         return List.of(prefix + ": expected to run but was not run");
      }
      List<String> unmet = new ArrayList<>();
      Integer statusCode = call.get().statusCode();
      if (expectation.status() != null && !expectation.status().equals(statusCode)) {
         unmet.add(prefix + ": expecting " + expectation.status() + " but got "
               + (statusCode == null ? "no response" : statusCode));
      }
      unmet.addAll(unmetOutputs(prefix + " output ", expectation.outputs(), call.get().outputs()));
      return unmet;
   }

   /**
    * Where the run stopped before the end of its workflow, at its last failed step as the attempts of a retried step
    * before it fail too; null when it ran to its end.
    */
   private static String stopOf(WorkflowResult run) {
      if (run.getStatus() == WorkflowRunStatus.SUCCESS) {
         return null;
      }
      return run.getSteps().stream().filter(step -> step.status() == WorkflowStepStatus.FAILED)
            .map(step -> "stopped at " + step.stepId()).reduce((first, last) -> last)
            .orElse("the run failed: " + run.getMessage());
   }

   private static List<String> unmetOutputs(String prefix, Map<String, Object> expected, Map<String, Object> actual) {
      List<String> unmet = new ArrayList<>();
      Map<String, Object> produced = actual == null ? Map.of() : actual;
      expected.forEach((name, value) -> {
         JsonNode expectedValue = json(value);
         JsonNode actualValue = produced.containsKey(name) ? json(produced.get(name)) : null;
         if (actualValue == null || !expectedValue.equals(SAME_VALUE, actualValue)) {
            unmet.add(prefix + name + ": expected " + expectedValue + " but was "
                  + (actualValue == null ? ABSENT : actualValue));
         }
      });
      return unmet;
   }

   private static JsonNode json(Object value) {
      return value == null ? NullNode.getInstance() : JSON.valueToTree(value);
   }
}
