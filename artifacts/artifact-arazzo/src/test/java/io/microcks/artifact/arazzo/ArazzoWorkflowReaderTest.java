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
 * success criteria and outputs. One multi-document fixture, {@code workflow-steps.arazzo.yaml}, holds the examples.
 * @author SebastienDegodez
 */
class ArazzoWorkflowReaderTest {

   private static final String FIXTURE = "workflow-steps.arazzo.yaml";

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
