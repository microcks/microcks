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

import io.microcks.artifact.arazzo.ArazzoAction;
import io.microcks.artifact.arazzo.ArazzoCriterion;
import io.microcks.artifact.arazzo.ArazzoExpressionEvaluator;
import io.microcks.artifact.arazzo.ArazzoExpressionException;
import io.microcks.artifact.arazzo.ArazzoJsonPointer;
import io.microcks.artifact.arazzo.ArazzoParameter;
import io.microcks.artifact.arazzo.ArazzoRequestBody;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.artifact.arazzo.ArazzoWorkflowReader;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Runs a workflow of an Arazzo document: its steps run in order, each one calling its operation, checking its success
 * criteria and giving its outputs to the next steps. When a step ends, the first of its success or failure actions
 * whose criteria hold applies: a retry calls the failing step again after its delay, up to its limit; an end ends the
 * workflow there. A failing step that is not retried ends the run. Each call of a step is reported in its own slot, in
 * execution order; the steps the run did not reach keep their slot after them, as not run.
 * @author SebastienDegodez
 */
public class WorkflowRunner {

   private static final ObjectMapper JSON = new ObjectMapper();
   private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());

   private final HttpTransport transport;
   private final Clock clock;
   private final Pause pause;
   private final ArazzoExpressionEvaluator expressions = new ArazzoExpressionEvaluator();
   private final ConditionEvaluator conditions = new ConditionEvaluator();

   /**
    * @param transport Sends the requests of the steps
    * @param clock     Times the steps and the run
    */
   public WorkflowRunner(HttpTransport transport, Clock clock) {
      this(transport, clock, Pause.SLEEP);
   }

   /**
    * @param transport Sends the requests of the steps
    * @param clock     Times the steps and the run
    * @param pause     Waits before a step is retried
    */
   public WorkflowRunner(HttpTransport transport, Clock clock, Pause pause) {
      this.transport = transport;
      this.clock = clock;
      this.pause = pause;
   }

   /**
    * Run a workflow, reporting each step as it ends, then the end of the run.
    * @param specification What to run
    * @param reporter      Where to report
    */
   public void run(RunSpecification specification, RunReporter reporter) {
      Instant start = clock.instant();
      Optional<ArazzoWorkflow> workflow = ArazzoWorkflowReader.workflow(parse(specification.arazzoDocument()),
            specification.workflowId());
      if (workflow.isEmpty()) {
         reporter.runEnded(new RunOutcome(RunStatus.FAILURE, Map.of(), elapsedSince(start),
               "Workflow " + specification.workflowId() + " not found"));
         return;
      }
      Map<String, Map<String, Object>> stepOutputs = new LinkedHashMap<>();
      ResponseSchemaValidator schemas = new ResponseSchemaValidator();
      List<ArazzoStep> steps = workflow.get().steps();
      int slot = 0;
      for (int position = 0; position < steps.size(); position++) {
         ArazzoStep step = steps.get(position);
         Attempt attempt = attempt(slot++, 1, step, specification, stepOutputs, schemas);
         reporter.stepEnded(attempt.result());
         Optional<ArazzoAction> action = applyingAction(step, attempt);
         for (int retries = 0; isRetry(action) && retries < action.get().retryLimit(); retries++) {
            pause.pause(delayOf(action.get(), specification));
            attempt = attempt(slot++, retries + 2, step, specification, stepOutputs, schemas);
            reporter.stepEnded(attempt.result());
            action = applyingAction(step, attempt);
         }
         boolean passed = attempt.passed();
         if (passed) {
            stepOutputs.put(step.stepId(), attempt.result().outputs());
         }
         if (!passed || action.isPresent()) {
            // The run ends at this step: it failed, or an end action applies.
            for (int next = position + 1; next < steps.size(); next++) {
               reporter.stepEnded(notRun(slot++, steps.get(next), specification));
            }
            reporter.runEnded(passed
                  ? endedEarly(workflow.get(), new EvaluationContext(specification.inputs(), stepOutputs, null), start)
                  : new RunOutcome(RunStatus.FAILURE, Map.of(), elapsedSince(start), null));
            return;
         }
      }
      reporter
            .runEnded(outcome(workflow.get(), new EvaluationContext(specification.inputs(), stepOutputs, null), start));
   }

   /** The first success or failure action of the step, as the attempt ended, whose criteria hold; empty if none. */
   private Optional<ArazzoAction> applyingAction(ArazzoStep step, Attempt attempt) {
      List<ArazzoAction> actions = attempt.passed() ? step.onSuccess() : step.onFailure();
      return actions.stream().filter(action -> holds(action.criteria(), attempt.context())).findFirst();
   }

   /** Criteria that cannot be evaluated, such as those about a response the step did not get, do not hold. */
   private boolean holds(List<ArazzoCriterion> criteria, EvaluationContext context) {
      try {
         return criteria.stream().allMatch(criterion -> conditions.evaluate(criterion, context).held());
      } catch (ArazzoExpressionException are) {
         return false;
      }
   }

   private static boolean isRetry(Optional<ArazzoAction> action) {
      return action.isPresent() && action.get().type().equals(ArazzoAction.RETRY);
   }

   /** A retry waits its delay, but never longer than a step waits for its response. */
   private static Duration delayOf(ArazzoAction retry, RunSpecification specification) {
      Duration delay = Duration.ofMillis(Math.max(0, Math.round(retry.retryAfter() * 1000)));
      return Collections.min(List.of(delay, specification.timeout()));
   }

   /**
    * A workflow an end action ended passes with the outputs it has at that step: those of the steps that did not run
    * have no value, and are left out.
    */
   private RunOutcome endedEarly(ArazzoWorkflow workflow, EvaluationContext context, Instant start) {
      Map<String, Object> outputs = new LinkedHashMap<>();
      workflow.outputs().forEach((name, expression) -> {
         try {
            outputs.put(name, expressions.resolve(expression, context));
         } catch (ArazzoExpressionException notRun) {
            // The output comes from a step the run did not reach.
         }
      });
      return new RunOutcome(RunStatus.SUCCESS, outputs, elapsedSince(start), null);
   }

   private RunOutcome outcome(ArazzoWorkflow workflow, EvaluationContext context, Instant start) {
      try {
         return new RunOutcome(RunStatus.SUCCESS, evaluateAll(workflow.outputs(), context), elapsedSince(start), null);
      } catch (ArazzoExpressionException are) {
         return new RunOutcome(RunStatus.FAILURE, Map.of(), elapsedSince(start), are.getMessage());
      }
   }

   /** A call of a step: how it ended, and what the criteria of its actions can see. */
   private record Attempt(StepResult result, EvaluationContext context) {

      boolean passed() {
         return result.status() == StepStatus.PASSED;
      }
   }

   private Attempt attempt(int index, int number, ArazzoStep step, RunSpecification specification,
         Map<String, Map<String, Object>> stepOutputs, ResponseSchemaValidator schemas) {
      StepResult result = execute(index, number, step, specification, stepOutputs, schemas);
      return new Attempt(result, new EvaluationContext(specification.inputs(), stepOutputs, result.response()));
   }

   private StepResult execute(int index, int attempt, ArazzoStep step, RunSpecification specification,
         Map<String, Map<String, Object>> stepOutputs, ResponseSchemaValidator schemas) {
      StepOperation operation = operationOf(step, specification);
      Instant start = clock.instant();
      EvaluationContext context = new EvaluationContext(specification.inputs(), stepOutputs, null);
      List<ParameterValue> parameters = List.of();
      HttpCall call = null;
      HttpOutcome response = null;
      try {
         requireCallable(operation);
         parameters = resolve(step.parameters(), context);
         HttpCall built = call(step, operation, parameters, specification, context, attempt);
         // The values of the secret are sent, never reported; the calls to the Microcks mocks never carry it.
         RunSecret secret = specification.sources().get(operation.sourceName()).mocks() ? RunSecret.NONE
               : specification.secret();
         call = secret.trace(built);
         response = transport.send(secret.authorize(built), specification.timeout());
         EvaluationContext answered = context.with(response);
         List<String> unevaluable = new ArrayList<>();
         List<CriterionResult> criteria = step.successCriteria().stream()
               .map(criterion -> evaluate(criterion, answered, unevaluable)).toList();
         // A response that does not conform to its schema fails the step, even when its criteria hold.
         Optional<String> nonConformance = specification.validateSchema()
               ? schemas.nonConformance(operation, specification.sources().get(operation.sourceName()), response)
               : Optional.empty();
         boolean passed = criteria.stream().allMatch(CriterionResult::held) && nonConformance.isEmpty();
         return new StepResult(index, step.stepId(), operation, passed ? StepStatus.PASSED : StepStatus.FAILED,
               response.status(), elapsedSince(start), criteria,
               passed ? evaluateAll(step.outputs(), answered) : Map.of(), call, response,
               unevaluable.stream().findFirst().orElse(nonConformance.orElse(null)), parameters,
               passed ? step.outputs() : Map.of());
      } catch (IOException ioe) {
         return failed(index, step, operation, start, new Exchange(parameters, call, null), "No response: " + ioe);
      } catch (ArazzoExpressionException are) {
         return failed(index, step, operation, start, new Exchange(parameters, call, response), are.getMessage());
      }
   }

   /**
    * A criterion whose expressions have no value in the response, such as a member a server error does not have, does
    * not hold; why is kept, so that every criterion of the step is reported with the reason of the first such one.
    */
   private CriterionResult evaluate(ArazzoCriterion criterion, EvaluationContext context, List<String> unevaluable) {
      try {
         return conditions.evaluate(criterion, context);
      } catch (ArazzoExpressionException are) {
         unevaluable.add(are.getMessage());
         return new CriterionResult(criterion.condition(), List.of(), false);
      }
   }

   private StepResult failed(int index, ArazzoStep step, StepOperation operation, Instant start, Exchange exchange,
         String message) {
      return new StepResult(index, step.stepId(), operation, StepStatus.FAILED,
            exchange.response() == null ? null : exchange.response().status(), elapsedSince(start), List.of(), Map.of(),
            exchange.request(), exchange.response(), message, exchange.parameters(), Map.of());
   }

   /**
    * What a step sent and received: the parameters it resolved, empty when it resolved none, and its request and
    * response, each null when it did not happen.
    */
   private record Exchange(List<ParameterValue> parameters, HttpCall request, HttpOutcome response) {
   }

   private static StepResult notRun(int index, ArazzoStep step, RunSpecification specification) {
      return new StepResult(index, step.stepId(), operationOf(step, specification), StepStatus.NOT_RUN, null, 0,
            List.of(), Map.of(), null, null, null, List.of(), Map.of());
   }

   /** The operation a step calls, with its method and path when its source gives them. */
   private static StepOperation operationOf(ArazzoStep step, RunSpecification specification) {
      String sourceName = step.call().sourceName();
      String operationId = step.call().operationId();
      SourceTarget source = specification.sources().get(sourceName);
      OperationTarget target = source == null ? null : source.operations().get(operationId);
      return target == null ? new StepOperation(sourceName, operationId, null, null)
            : new StepOperation(sourceName, operationId, target.method(), target.path());
   }

   private static void requireCallable(StepOperation operation) {
      if (operation.method() == null) {
         throw new ArazzoRunException(
               "Operation " + operation.operationId() + " not found in source " + operation.sourceName());
      }
   }

   /** The parameters of a step with their values, each keeping the runtime expression it came from. */
   private List<ParameterValue> resolve(List<ArazzoParameter> parameters, EvaluationContext context) {
      return parameters.stream()
            .map(parameter -> new ParameterValue(parameter.name(), parameter.in(),
                  expressions.resolve(parameter.value(), context),
                  ArazzoExpressionEvaluator.isExpression(parameter.value()) ? parameter.value() : null))
            .toList();
   }

   private HttpCall call(ArazzoStep step, StepOperation operation, List<ParameterValue> parameters,
         RunSpecification specification, EvaluationContext context, int attempt) {
      String path = operation.path();
      StringJoiner query = new StringJoiner("&", "?", "").setEmptyValue("");
      Map<String, String> headers = new LinkedHashMap<>();
      for (ParameterValue parameter : parameters) {
         String value = String.valueOf(parameter.value());
         switch (parameter.in()) {
            case "path" -> path = path.replace("{" + parameter.name() + "}", encode(value).replace("+", "%20"));
            case "query" -> query.add(encode(parameter.name()) + "=" + encode(value));
            default -> headers.put(parameter.name(), value);
         }
      }
      String body = null;
      ArazzoRequestBody requestBody = step.requestBody();
      if (requestBody != null) {
         body = body(requestBody, context);
         headers.put("Content-Type", requestBody.contentType());
      }
      SourceTarget source = specification.sources().get(operation.sourceName());
      if (source.mocks()) {
         // The calls to the mocks tell Live Traces which run, step and attempt they come from.
         RunBaggage.mark(headers, specification.runId(), specification.workflowId(), step.stepId(), attempt);
      }
      URI baseUrl = source.baseUrl();
      return new HttpCall(operation.method(), URI.create(baseUrl + path + query), headers, body);
   }

   /** A JSON payload gets its replacements; any other payload is sent as is. */
   private String body(ArazzoRequestBody requestBody, EvaluationContext context) {
      Object payload;
      try {
         payload = JSON.readValue(requestBody.payload(), Object.class);
      } catch (JsonProcessingException notJson) {
         return requestBody.payload();
      }
      for (Map.Entry<String, String> replacement : requestBody.replacements().entrySet()) {
         payload = ArazzoJsonPointer.set(payload, replacement.getKey(),
               expressions.resolve(replacement.getValue(), context));
      }
      return JSON.valueToTree(payload).toString();
   }

   private Map<String, Object> evaluateAll(Map<String, String> expressionsByName, EvaluationContext context) {
      Map<String, Object> values = new LinkedHashMap<>();
      expressionsByName.forEach((name, expression) -> values.put(name, expressions.resolve(expression, context)));
      return values;
   }

   private long elapsedSince(Instant start) {
      return Duration.between(start, clock.instant()).toMillis();
   }

   private static String encode(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
   }

   private static JsonNode parse(String document) {
      try {
         return YAML.readTree(document);
      } catch (JsonProcessingException jpe) {
         // A document that cannot be read has no workflow.
         return MissingNode.getInstance();
      }
   }
}
