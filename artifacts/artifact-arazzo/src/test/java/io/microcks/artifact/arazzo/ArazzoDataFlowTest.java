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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the data flow of an Arazzo workflow: where its steps use the outputs of earlier steps. One multi-document
 * fixture, {@code data-flow.arazzo.yaml}, holds the examples.
 * @author SebastienDegodez
 */
class ArazzoDataFlowTest {

   private static final String FIXTURE = "data-flow.arazzo.yaml";

   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "parameters; list.outputs.first -> read path name, list.outputs.size -> read query size",
         "body; read.outputs.price -> reprice body /price",
         "criteria; read.outputs.price -> reprice criterion, read.outputs.name -> reprice criterion",
         "embedded; login.outputs.scheme -> read header Authorization, login.outputs.token -> read header "
               + "Authorization, login.outputs.token -> read criterion",
         "constants; ''" })
   void aStepOutputUsedByALaterStepIsPassedToWhereItIsUsed(String example, String passings) throws IOException {
      assertEquals(passings, String.join(", ", ArazzoDataFlow.stepOutputPassings(workflow(example)).stream().map(
            passing -> passing.fromStepId() + ".outputs." + passing.outputName() + " -> " + describe(passing.use()))
            .toList()));
   }

   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "parameters; pastry: read header X-Pastry | size: list query size",
         "body; pastry: reprice body /name", "criteria; newPrice: reprice criterion",
         "embedded; user-name: read header Authorization, read criterion", "constants; 'unused: '" })
   void anInputIsListedWithWhereStepsUseItDeclaredInputsFirst(String example, String inputs) throws IOException {
      assertEquals(inputs,
            String.join(" | ",
                  ArazzoDataFlow.inputUses(workflow(example)).entrySet().stream()
                        .map(input -> input.getKey() + ": "
                              + String.join(", ", input.getValue().stream().map(ArazzoDataFlowTest::describe).toList()))
                        .toList()));
   }

   private static ArazzoWorkflow workflow(String example) throws IOException {
      return ArazzoWorkflowReader.workflow(ArazzoFixtures.exampleOf(FIXTURE, example), "flow").orElseThrow();
   }

   private static String describe(ArazzoUse use) {
      return use.stepId() + " " + use.place() + (use.target() == null ? "" : " " + use.target());
   }
}
