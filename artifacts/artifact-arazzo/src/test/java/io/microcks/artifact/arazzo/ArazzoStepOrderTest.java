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

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests of the order of the steps of an Arazzo workflow (S30). Arazzo 1.1 lets a step declare the steps it depends on
 * (dependsOn), and makes the outputs a step uses implicit dependencies. The maintainer decided that dependsOn relaxes
 * the order of the list: a step without dependsOn comes after every step listed before it, as before; a step with
 * dependsOn only comes after the steps it names and those whose outputs it uses. The examples are in
 * {@code step-order.arazzo.yaml}.
 * @author SebastienDegodez
 */
class ArazzoStepOrderTest {

   private static final String FIXTURE = "step-order.arazzo.yaml";

   /** AC-05: the prerequisites of a step, in the order of the workflow, under the decided rule. */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "parallel; checkCompany; checkAccount; []",
         "parallel; checkCompany; sirene; [checkAccount]", "parallel; checkCompany; bodacc; [checkAccount]",
         "parallel; checkCompany; financials; [checkAccount]",
         "parallel; checkCompany; synthesis; [checkAccount, sirene, bodacc, financials]",
         "implicit; checkCompany; financials; [checkAccount, sirene]",
         "every-place; checkCompany; inPayload; [checkAccount]",
         "every-place; checkCompany; inReplacement; [checkAccount]",
         "every-place; checkCompany; inCriterion; [checkAccount]",
         "every-place; checkCompany; inContext; [checkAccount]",
         "every-place; checkCompany; inActionCriterion; [checkAccount]",
         "every-place; checkCompany; inOutput; [checkAccount]", "every-place; checkCompany; alone; []",
         "in-correlation-id; listen; registered; [checkAccount]", "sequential; checkCompany; checkAccount; []",
         "sequential; checkCompany; synthesis; [checkAccount, sirene]",
         "forward-use-without-depends-on; checkCompany; checkAccount; []",
         "forward-use-without-depends-on; checkCompany; sirene; [checkAccount]" })
   void aStepComesAfterWhatItDependsOnOrElseAfterAllThePreviousSteps(String example, String workflowId, String stepId,
         String prerequisites) throws Exception {
      JsonNode document = ArazzoFixtures.exampleOf(FIXTURE, example);
      ArazzoDocumentReader.read(document);

      ArazzoWorkflow workflow = ArazzoWorkflowReader.workflow(document, workflowId).orElseThrow();

      assertEquals(prerequisites, ArazzoStepOrder.prerequisitesOf(workflow, stepId).toString());
   }

   /** A step tells the steps it declares it depends on, as the document lists them; null when it declares none. */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "parallel; sirene; [checkAccount]", "implicit; financials; [sirene]",
         "every-place; alone; []", "sequential; sirene; null" })
   void aStepTellsTheStepsItDeclaresItDependsOn(String example, String stepId, String dependsOn) throws IOException {
      ArazzoWorkflow workflow = ArazzoWorkflowReader
            .workflow(ArazzoFixtures.exampleOf(FIXTURE, example), "checkCompany").orElseThrow();

      assertEquals(dependsOn, String.valueOf(workflow.steps().stream().filter(step -> step.stepId().equals(stepId))
            .findFirst().orElseThrow().dependsOn()));
   }

   /**
    * Arazzo 1.1 (5.8.5.2) intends dependsOn to coordinate asynchronous steps, and recommends the order of the list for
    * synchronous ones without forbidding dependsOn there. The maintainer decided to accept such a document and to warn:
    * the steps declaring dependsOn when neither they nor any step they name send or receive messages.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = { "parallel; checkCompany; [sirene, bodacc, financials]",
         "every-place; checkCompany; [inPayload, inReplacement, inCriterion, inContext, inActionCriterion, inOutput, "
               + "alone]",
         "in-correlation-id; listen; []", "joins-async; listen; []", "sequential; checkCompany; []" })
   void theStepsDeclaringDependsOnBetweenSynchronousStepsAreTold(String example, String workflowId, String stepIds)
         throws IOException {
      ArazzoWorkflow workflow = ArazzoWorkflowReader.workflow(ArazzoFixtures.exampleOf(FIXTURE, example), workflowId)
            .orElseThrow();

      assertEquals(stepIds, ArazzoStepOrder.dependsOnBetweenSynchronousSteps(workflow).toString());
   }

   /**
    * AC-02: Arazzo requires a dependsOn to name steps that exist, and only defines it for a step since 1.1; a step that
    * depends on itself or on steps depending on it cannot run. These documents are refused permanently. Depending on a
    * step of another workflow, or on a step listed later, is refused temporarily: the runner plays one workflow, and
    * its steps in the order of the list.
    */
   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "unknown-step; Invalid Arazzo document: sirene depends on an unknown step: checkAcount",
         "itself; Invalid Arazzo document: a step cannot depend on itself: sirene",
         "cycle; Invalid Arazzo document: steps depend on each other in a cycle: checkAccount, sirene, bodacc",
         "cycle-through-a-step-without-depends-on; Invalid Arazzo document: steps depend on each other in a cycle: "
               + "checkAccount, sirene",
         "later; Unsupported step order: checkAccount depends on sirene, listed after it",
         "implicit-later; Unsupported step order: checkAccount uses the outputs of sirene, listed after it",
         "arazzo-1-0; Invalid Arazzo document: a step dependsOn needs Arazzo 1.1, not 1.0.1: sirene",
         "other-workflow; Unsupported step dependency on another workflow: $workflows.openAccount.steps.createAccount",
         "other-document; Unsupported step dependency on another workflow: "
               + "$sourceDescriptions.accounts.openAccount.steps.createAccount",
         "not-a-list; Invalid Arazzo document: a step dependsOn is a list of step ids: sirene",
         "not-a-step-id; Invalid Arazzo document: a step dependsOn is a list of step ids: sirene" })
   void aDependsOnThatCannotBeSatisfiedIsRefusedWithItsReason(String example, String reason) throws IOException {
      JsonNode document = ArazzoFixtures.exampleOf(FIXTURE, example);

      UnsupportedArazzoDocumentException refusal = assertThrows(UnsupportedArazzoDocumentException.class,
            () -> ArazzoDocumentReader.read(document));

      assertEquals(reason, refusal.getMessage());
   }

   @Test
   void theStepsOfAStepTheWorkflowDoesNotHaveCannotBeTold() throws IOException {
      ArazzoWorkflow workflow = ArazzoWorkflowReader
            .workflow(ArazzoFixtures.exampleOf(FIXTURE, "parallel"), "checkCompany").orElseThrow();

      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
            () -> ArazzoStepOrder.prerequisitesOf(workflow, "unknown"));

      assertEquals("checkCompany has no step unknown", refusal.getMessage());
   }
}
