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
package io.microcks.artifact.arazzo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of reading the steps of an Arazzo workflow: the operation each step calls, its parameters, request body,
 * success criteria, outputs and actions. Two multi-document fixtures hold the examples:
 * {@code workflow-steps.arazzo.yaml} and, for the actions of the steps, {@code step-actions.arazzo.yaml}.
 * @author SebastienDegodez
 */
class ArazzoWorkflowReaderTest {

   private static final String FIXTURE = "workflow-steps.arazzo.yaml";
   private static final String ACTIONS_FIXTURE = "step-actions.arazzo.yaml";

   @Test
   void theStepsOfAWorkflowAreReadWithWhatTheyCallSendCheckAndGive() throws IOException {
      ArazzoWorkflow workflow = workflow("read-then-reprice", "readThenReprice").orElseThrow();

      assertEquals(List.of(
            "readPastry calls pastryApi GetPastryByName | parameter name in path = $inputs.pastry | "
                  + "parameter Accept in header = application/json | "
                  + "criterion simple $statusCode == 200 && $response.body#/status == 'available' | "
                  + "output name = $response.body#/name | output price = $response.body#/price",
            "reprice calls pastryApi PatchPastry | parameter name in path = $steps.readPastry.outputs.name | "
                  + "body application/json {\"price\":0} with {/price=$inputs.newPrice} | "
                  + "criterion simple $statusCode == 200 | criterion simple $response.body#/price == $inputs.newPrice | "
                  + "output price = $response.body#/price"),
            workflow.steps().stream().map(ArazzoWorkflowReaderTest::describe).toList());
   }

   @Test
   void aWorkflowIsReadWithItsSummaryDescriptionInputsAndOutputs() throws IOException {
      ArazzoWorkflow workflow = workflow("read-then-reprice", "readThenReprice").orElseThrow();

      assertEquals("readThenReprice", workflow.workflowId());
      assertEquals("Reads a pastry, then changes its price using the name returned by the first call.",
            workflow.summary());
      assertEquals("The price is changed on the pastry the first call returns.", workflow.description());
      assertEquals(List.of("pastry", "newPrice"), workflow.inputNames());
      assertEquals("{pastry=string, newPrice=number}", workflow.inputTypes().toString());
      assertEquals("{before=$steps.readPastry.outputs.price, after=$steps.reprice.outputs.price}",
            workflow.outputs().toString());
   }

   @Test
   void eachWorkflowOfADocumentIsReadByItsId() throws IOException {
      assertEquals(Optional.of("listThenReadFirst"),
            workflow("read-then-reprice", "listThenReadFirst").map(ArazzoWorkflow::workflowId));
   }

   @Test
   void aWorkflowTheDocumentDoesNotHaveIsNotRead() throws IOException {
      assertEquals(Optional.empty(), workflow("read-then-reprice", "unknown"));
   }

   @Test
   void aWorkflowWithNothingButItsStepsIsReadWithoutSummaryDescriptionInputsNorOutputs() throws IOException {
      ArazzoWorkflow workflow = workflow("bare-step", "bare").orElseThrow();

      assertEquals("bare null null [] {}", workflow.workflowId() + " " + workflow.summary() + " "
            + workflow.description() + " " + workflow.inputNames() + " " + workflow.outputs());
   }

   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "bare-step; bare; list calls pastryApi GetPastries",
         "described-step; described; list (Lists the pastries.) calls pastryApi GetPastries",
         "regex-criterion; regex; read calls pastryApi GetPastryByName | criterion regex ^[A-Z].* on $response.body#/name",
         "text-payload; textPayload; reprice calls pastryApi PatchPastry | "
               + "body null <pastry><price>2.6</price></pastry> with {}",
         "non-text-values; nonTextValues; reprice calls pastryApi PatchPastry | parameter page in query = 2 | "
               + "body null {\"price\":0} with {/price=2.6}" })
   void aStepIsReadWithWhatItDeclaresOnly(String example, String workflowId, String step) throws IOException {
      assertEquals(List.of(step), workflow(example, workflowId).orElseThrow().steps().stream()
            .map(ArazzoWorkflowReaderTest::describe).toList());
   }

   /**
    * S17: a step tells what it does when it fails or succeeds, in the order of the document: retry after a delay up to
    * a limit, as Arazzo defaults them, or end the workflow; each action applies when its criteria hold.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
         "retry-until-ready; waitUntilReady; pollStatus | onFailure retry notReadyYet after 1.0 s up to 5 times",
         "retry-defaults; retryOnce; pollStatus | onFailure retry again after 0.0 s up to 1 times",
         "end-with-criteria; stopWhilePreparing; checkStatus | onSuccess end stillPreparing when simple "
               + "$response.body#/status == 'preparing', regex ^application/json on $response.header.Content-Type | "
               + "onFailure end giveUp, readOrder",
         "several-actions; retryUnlessUnknown; pollStatus | onFailure end unknownOrder when simple $statusCode == 404 "
               + "| onFailure retry notReadyYet after 0.5 s up to 3 times" })
   void eachStepIsReadWithTheActionsItTakesOnSuccessAndOnFailure(String example, String workflowId, String steps)
         throws IOException {
      ArazzoWorkflow workflow = ArazzoWorkflowReader
            .workflow(ArazzoFixtures.exampleOf(ACTIONS_FIXTURE, example), workflowId).orElseThrow();

      assertEquals(steps, String.join(", ", workflow.steps().stream().map(ArazzoWorkflowReaderTest::actions).toList()));
   }

   private static String actions(ArazzoStep step) {
      List<String> parts = new ArrayList<>();
      parts.add(step.stepId());
      step.onSuccess().forEach(action -> parts.add("onSuccess " + describe(action)));
      step.onFailure().forEach(action -> parts.add("onFailure " + describe(action)));
      return String.join(" | ", parts);
   }

   private static String describe(ArazzoAction action) {
      String retry = action.retryAfter() == null && action.retryLimit() == null ? ""
            : " after " + action.retryAfter() + " s up to " + action.retryLimit() + " times";
      List<String> criteria = action.criteria().stream().map(ArazzoWorkflowReaderTest::describe).toList();
      return action.type() + " " + action.name() + retry
            + (criteria.isEmpty() ? "" : " when " + String.join(", ", criteria));
   }

   private static String describe(ArazzoCriterion criterion) {
      return criterion.type() + " " + criterion.condition()
            + (criterion.context() == null ? "" : " on " + criterion.context());
   }

   private static Optional<ArazzoWorkflow> workflow(String example, String workflowId) throws IOException {
      return ArazzoWorkflowReader.workflow(ArazzoFixtures.exampleOf(FIXTURE, example), workflowId);
   }

   private static String describe(ArazzoStep step) {
      List<String> parts = new ArrayList<>();
      String description = step.description() == null ? "" : " (" + step.description() + ")";
      parts.add(step.stepId() + description + " calls " + step.call().sourceName() + " " + step.call().operationId());
      step.parameters().forEach(parameter -> parts
            .add("parameter " + parameter.name() + " in " + parameter.in() + " = " + parameter.value()));
      if (step.requestBody() != null) {
         parts.add("body " + step.requestBody().contentType() + " " + step.requestBody().payload().replaceAll("\\s", "")
               + " with " + step.requestBody().replacements());
      }
      step.successCriteria().forEach(criterion -> parts.add("criterion " + criterion.type() + " "
            + criterion.condition() + (criterion.context() == null ? "" : " on " + criterion.context())));
      step.outputs().forEach((name, expression) -> parts.add("output " + name + " = " + expression));
      return String.join(" | ", parts);
   }
}
