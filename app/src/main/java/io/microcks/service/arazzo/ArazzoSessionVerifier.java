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
package io.microcks.service.arazzo;

import io.microcks.artifact.arazzo.ArazzoExpressionContext;
import io.microcks.artifact.arazzo.ArazzoExpressionEvaluator;
import io.microcks.artifact.arazzo.ArazzoExpressionException;
import io.microcks.artifact.arazzo.ArazzoParameter;
import io.microcks.artifact.arazzo.ArazzoReceivedRequest;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.artifact.arazzo.ArazzoStepOrder;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.domain.Header;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.domain.arazzo.ArazzoStepOperation;
import io.microcks.domain.arazzo.WorkflowParameterValue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Verifies the calls an application made to the mocks in a session against the steps of a workflow: a step is matched
 * to the first call, in the order of receipt, that no earlier step took and that calls its operation, its method and
 * path template, on the mocks of the API its source is linked to, whatever its place. A step without call is missing.
 * Then each call is checked: it must be received after the calls of the prerequisites of its step, as "dependsOn
 * relaxes" the order of the workflow; the values it sends from runtime expressions must be those the outputs of the
 * steps before it give, read from the responses of the mocks; and its request must conform to the contract of the API
 * it called.
 * @author SebastienDegodez
 */
final class ArazzoSessionVerifier {

   private static final ObjectMapper JSON = new ObjectMapper();
   private static final ArazzoExpressionEvaluator EXPRESSIONS = new ArazzoExpressionEvaluator();

   private ArazzoSessionVerifier() {
   }

   /**
    * A step of the workflow, with the operation it calls and the Microcks API its source is linked to.
    * @param index      The place of the step in the workflow, from 0
    * @param step       The step
    * @param operation  The operation it calls in the linked API
    * @param apiName    The name of the linked API
    * @param apiVersion The version of the linked API
    * @param contract   The OpenAPI contract of the linked API, which the calls of the step must conform to; null when
    *                   it has none
    */
   record ExpectedStep(int index, ArazzoStep step, ArazzoStepOperation operation, String apiName, String apiVersion,
         JsonNode contract) {
   }

   /**
    * How a step was verified.
    * @param expected   The step
    * @param call       The call matched to it; null when none was
    * @param failures   Why the step fails, in the order they were found; empty when it passes
    * @param outputs    Its outputs, read from the response of the mocks to its call, by name
    * @param parameters The value its call carries for each of its parameters, with the expression it should come from
    */
   record StepVerdict(ExpectedStep expected, ArazzoSessionCall call, List<String> failures, Map<String, Object> outputs,
         List<WorkflowParameterValue> parameters) {
   }

   /**
    * @param workflow    The workflow, which tells the prerequisites of each step: "dependsOn relaxes" the order
    * @param steps       The steps of the workflow, in its order
    * @param calls       The calls of the session, in their order of receipt
    * @param inputs      The inputs of the workflow the session was opened with, by name; empty for none
    * @param resourceUrl The URL the relative references of the contracts resolve against; null for none
    * @return The verdict of each step, in the order of the workflow
    */
   static List<StepVerdict> verify(ArazzoWorkflow workflow, List<ExpectedStep> steps, List<ArazzoSessionCall> calls,
         Map<String, Object> inputs, String resourceUrl) {
      Map<ExpectedStep, ArazzoSessionCall> matched = match(steps, calls);
      Map<String, ArazzoSessionCall> callOfStep = new LinkedHashMap<>();
      matched.forEach((step, call) -> callOfStep.put(step.step().stepId(), call));
      Map<String, Map<String, Object>> stepOutputs = new LinkedHashMap<>();
      List<StepVerdict> verdicts = new ArrayList<>();
      for (ExpectedStep step : steps) {
         ArazzoSessionCall call = matched.get(step);
         if (call == null) {
            verdicts.add(new StepVerdict(step, null, List.of("step `" + step.step().stepId() + "` missing"), Map.of(),
                  List.of()));
            continue;
         }
         List<String> failures = new ArrayList<>();
         outOfOrder(step, call, ArazzoStepOrder.prerequisitesOf(workflow, step.step().stepId()), callOfStep, calls)
               .ifPresent(failures::add);
         // The values the call carries are checked against the outputs of the steps before it in the workflow.
         ReceivedCall before = new ReceivedCall(inputs, Map.copyOf(stepOutputs), null);
         ArazzoReceivedRequest request = requestOf(step, call);
         List<WorkflowParameterValue> parameters = new ArrayList<>();
         for (ArazzoParameter parameter : step.step().parameters()) {
            Object value = request.parameter(parameter.in(), parameter.name()).orElse(null);
            String expression = expressionOf(parameter.value());
            parameters.add(new WorkflowParameterValue(parameter.name(), parameter.in(), value, expression));
            checkData(step, parameter.in() + " `" + parameter.name() + "`", expression, value, before)
                  .ifPresent(failures::add);
         }
         if (step.step().requestBody() != null) {
            step.step().requestBody().replacements().forEach((target, value) -> checkData(step, "body `" + target + "`",
                  expressionOf(value), request.bodyValue(target).orElse(null), before).ifPresent(failures::add));
         }
         String contentType = request.parameter("header", "Content-Type").map(String::valueOf).orElse(null);
         ArazzoRequestConformance
               .violations(step.contract(), step.operation().method(), step.operation().path(), request, contentType,
                     resourceUrl)
               .forEach(violation -> failures.add("`" + step.step().stepId() + "` request does not conform to "
                     + step.apiName() + " " + step.apiVersion() + ": " + violation));
         Map<String, Object> outputs = evaluated(step.step().outputs(),
               new ReceivedCall(inputs, Map.copyOf(stepOutputs), call));
         stepOutputs.put(step.step().stepId(), outputs);
         verdicts.add(new StepVerdict(step, call, failures, outputs, parameters));
      }
      return verdicts;
   }

   /**
    * @param expressions The outputs of the workflow, by name, with their runtime expressions
    * @param inputs      The inputs of the workflow the session was opened with, by name; empty for none
    * @param verdicts    The verdicts of its steps
    * @return The outputs of the workflow that have a value, by name
    */
   static Map<String, Object> workflowOutputs(Map<String, String> expressions, Map<String, Object> inputs,
         List<StepVerdict> verdicts) {
      Map<String, Map<String, Object>> stepOutputs = new LinkedHashMap<>();
      // A missing step has no outputs: the outputs of the workflow coming from it have no value.
      verdicts.forEach(verdict -> stepOutputs.put(verdict.expected().step().stepId(), verdict.outputs()));
      return evaluated(expressions, new ReceivedCall(inputs, stepOutputs, null));
   }

   /** A value of the workflow is a runtime expression when it starts with $; null for a constant. */
   private static String expressionOf(String value) {
      return ArazzoExpressionEvaluator.isExpression(value) ? value : null;
   }

   /**
    * A value a step sends must be the value of the runtime expression it comes from, as the outputs of the steps before
    * it give it, or the inputs the session was opened with. A constant is not checked, nor an expression without value,
    * such as an input the session was not given or the output of a missing step.
    */
   private static Optional<String> checkData(ExpectedStep step, String place, String expression, Object received,
         ReceivedCall context) {
      if (expression == null) {
         return Optional.empty();
      }
      Object expected;
      try {
         expected = EXPRESSIONS.evaluate(expression, context);
      } catch (ArazzoExpressionException noValue) {
         return Optional.empty();
      }
      if (sameValue(expected, received)) {
         return Optional.empty();
      }
      return Optional.of("`" + step.step().stepId() + "` " + place + " should come from `" + expression + "` ("
            + expected + "), got " + (received == null ? "nothing" : received));
   }

   /** Numbers are equal by value, whatever their type; any other value by its text. */
   private static boolean sameValue(Object expected, Object received) {
      if (expected instanceof Number number && received instanceof Number other) {
         return new BigDecimal(number.toString()).compareTo(new BigDecimal(other.toString())) == 0;
      }
      return received != null && String.valueOf(expected).equals(String.valueOf(received));
   }

   /** The outputs whose expressions have a value, by name; those without value are left out. */
   private static Map<String, Object> evaluated(Map<String, String> expressions, ReceivedCall context) {
      Map<String, Object> values = new LinkedHashMap<>();
      expressions.forEach((name, expression) -> {
         try {
            values.put(name, EXPRESSIONS.resolve(expression, context));
         } catch (ArazzoExpressionException noValue) {
            // The output comes from a response the mocks did not give, or a step without call.
         }
      });
      return values;
   }

   static ArazzoReceivedRequest requestOf(ExpectedStep step, ArazzoSessionCall call) {
      return new ArazzoReceivedRequest(step.operation().path(), call.getPath(), call.getQueryString(),
            headersOf(call.getRequestHeaders()), call.getRequestBody());
   }

   private static Map<String, List<String>> headersOf(Set<Header> headers) {
      Map<String, List<String>> byName = new LinkedHashMap<>();
      headers.forEach(header -> byName.put(header.getName(), List.copyOf(header.getValues())));
      return byName;
   }

   /**
    * What the runtime expressions of a step see in a session: the inputs it was opened with, the outputs of the steps
    * before it and, once the mocks answered, the response to its call.
    */
   record ReceivedCall(Map<String, Object> inputs, Map<String, Map<String, Object>> steps, ArazzoSessionCall call)
         implements ArazzoExpressionContext {

      @Override
      public int statusCode(String expression) {
         return answered(expression).getResponseStatus();
      }

      @Override
      public Map<String, List<String>> responseHeaders(String expression) {
         return headersOf(answered(expression).getResponseHeaders());
      }

      @Override
      public Object responseBody(String expression) {
         String body = answered(expression).getResponseBody();
         try {
            return JSON.readValue(body, Object.class);
         } catch (IllegalArgumentException | JsonProcessingException notJson) {
            return body;
         }
      }

      private ArazzoSessionCall answered(String expression) {
         if (call == null) {
            throw new ArazzoExpressionException("No response yet for " + expression);
         }
         return call;
      }
   }

   /**
    * A call is in order when the calls of the prerequisites of its step were all received before it; else it names the
    * first prerequisite, in the order of the workflow, whose call was not. The prerequisites follow the rule of the
    * shared kernel: the steps a step names in its dependsOn and those whose outputs it uses, or every step listed
    * before a step without dependsOn. A prerequisite without call is missing on its own and does not count here.
    */
   private static Optional<String> outOfOrder(ExpectedStep step, ArazzoSessionCall call, Set<String> prerequisites,
         Map<String, ArazzoSessionCall> callOfStep, List<ArazzoSessionCall> calls) {
      List<ArazzoSessionCall> receivedBefore = calls.subList(0, calls.indexOf(call));
      return prerequisites.stream()
            .filter(prerequisite -> callOfStep.containsKey(prerequisite)
                  && !receivedBefore.contains(callOfStep.get(prerequisite)))
            .map(prerequisite -> "step `" + prerequisite + "` expected before `" + step.step().stepId() + "`")
            .findFirst();
   }

   /** The call matched to each step that has one, in the order of the workflow. */
   private static Map<ExpectedStep, ArazzoSessionCall> match(List<ExpectedStep> steps, List<ArazzoSessionCall> calls) {
      Set<Long> taken = new HashSet<>();
      Map<ExpectedStep, ArazzoSessionCall> matched = new LinkedHashMap<>();
      for (ExpectedStep step : steps) {
         Optional<ArazzoSessionCall> call = calls.stream()
               .filter(candidate -> !taken.contains(candidate.getIndex()) && calls(candidate, step)).findFirst();
         call.ifPresent(found -> {
            taken.add(found.getIndex());
            matched.put(step, found);
         });
      }
      return matched;
   }

   /** Microcks names an operation of an OpenAPI contract by its method and path template. */
   static boolean calls(ArazzoSessionCall call, ExpectedStep step) {
      return step.apiName().equals(call.getServiceName()) && step.apiVersion().equals(call.getServiceVersion())
            && (step.operation().method() + " " + step.operation().path()).equals(call.getOperationName());
   }
}
