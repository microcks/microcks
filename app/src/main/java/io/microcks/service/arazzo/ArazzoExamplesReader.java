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

import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoStepExpectation;
import io.microcks.domain.arazzo.ArazzoWorkflowExample;
import io.microcks.artifact.MockRepositoryImportException;
import io.microcks.util.ObjectMapperFactory;
import io.microcks.artifact.arazzo.ArazzoDataFlow;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.artifact.arazzo.ArazzoWorkflowReader;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the examples section of an ArazzoSources overlay, checking each example against the workflow it plays: an
 * example may only give the inputs the workflow declares, and expect the outputs and steps the workflow has.
 * @author SebastienDegodez
 */
final class ArazzoExamplesReader {

   private static final String INVALID_OVERLAY = "Invalid ArazzoSources overlay: ";
   private static final int LOWEST_STATUS = 100;
   private static final int HIGHEST_STATUS = 599;

   private ArazzoExamplesReader() {
   }

   /**
    * @param examples The examples section of an overlay: examples by name, by workflow id
    * @param document The Arazzo document the overlay names; null when it is not imported
    * @param name     The title of the Arazzo document the overlay names
    * @param version  The version of the Arazzo document the overlay names
    * @return The examples, in the order of the overlay
    * @throws MockRepositoryImportException if an example does not fit its workflow, or the document is not imported
    */
   static List<ArazzoWorkflowExample> read(JsonNode examples, ArazzoDocument document, String name, String version)
         throws MockRepositoryImportException {
      if (examples.isEmpty()) {
         return List.of();
      }
      if (document == null) {
         throw new MockRepositoryImportException(INVALID_OVERLAY + "its examples need the Arazzo document " + name + " "
               + version + ", which is not imported");
      }
      JsonNode parsed = ArazzoWorkflowService.parse(document.getContent());
      List<ArazzoWorkflowExample> read = new ArrayList<>();
      for (Map.Entry<String, JsonNode> workflowExamples : examples.properties()) {
         Optional<ArazzoWorkflow> workflow = ArazzoWorkflowReader.workflow(parsed, workflowExamples.getKey());
         for (Map.Entry<String, JsonNode> example : workflowExamples.getValue().properties()) {
            if (workflow.isEmpty()) {
               throw new MockRepositoryImportException(INVALID_OVERLAY + "example " + example.getKey()
                     + " names the unknown workflow " + workflowExamples.getKey());
            }
            read.add(example(workflow.get(), example.getKey(), example.getValue()));
         }
      }
      return read;
   }

   private static ArazzoWorkflowExample example(ArazzoWorkflow workflow, String name, JsonNode example)
         throws MockRepositoryImportException {
      String refusal = INVALID_OVERLAY + "example " + name + " of " + workflow.workflowId() + " ";
      Set<String> inputNames = new LinkedHashSet<>(ArazzoDataFlow.inputUses(workflow).keySet());
      inputNames.addAll(ArazzoDataFlow.neededInputs(workflow));
      Map<String, Object> inputs = values(example.path("inputs"), inputNames, refusal + "gives the unknown input ", "");
      Map<String, Object> outputs = values(example.path("outputs"), workflow.outputs().keySet(),
            refusal + "expects the unknown output ", "");
      Map<String, ArazzoStep> steps = new LinkedHashMap<>();
      workflow.steps().forEach(step -> steps.put(step.stepId(), step));
      List<ArazzoStepExpectation> expectations = new ArrayList<>();
      for (Map.Entry<String, JsonNode> expectation : example.path("steps").properties()) {
         ArazzoStep step = steps.get(expectation.getKey());
         if (step == null) {
            throw new MockRepositoryImportException(refusal + "expects the unknown step " + expectation.getKey());
         }
         expectations.add(new ArazzoStepExpectation(step.stepId(),
               status(expectation.getValue().path("status"), refusal, step.stepId()),
               values(expectation.getValue().path("outputs"), step.outputs().keySet(),
                     refusal + "expects the unknown output ", " of step " + step.stepId())));
      }
      String stopsAt = example.path("stopsAt").isMissingNode() ? null : example.path("stopsAt").asText();
      if (stopsAt != null && !steps.containsKey(stopsAt)) {
         throw new MockRepositoryImportException(refusal + "stops at the unknown step " + stopsAt);
      }
      return new ArazzoWorkflowExample(workflow.workflowId(), name, inputs, outputs, expectations, stopsAt);
   }

   /** The values of a map of the example, each name being one of the known names. */
   private static Map<String, Object> values(JsonNode values, Set<String> knownNames, String unknownPrefix,
         String unknownSuffix) throws MockRepositoryImportException {
      Map<String, Object> read = new LinkedHashMap<>();
      for (Map.Entry<String, JsonNode> value : values.properties()) {
         if (!knownNames.contains(value.getKey())) {
            throw new MockRepositoryImportException(unknownPrefix + value.getKey() + unknownSuffix);
         }
         read.put(value.getKey(),
               ObjectMapperFactory.getYamlObjectMapper().convertValue(value.getValue(), Object.class));
      }
      return read;
   }

   /** As OpenAPI contract tests compare it, an expected status is an exact HTTP status code: no range, no default. */
   private static Integer status(JsonNode status, String refusal, String stepId) throws MockRepositoryImportException {
      if (status.isMissingNode()) {
         return null;
      }
      if (!status.isInt() || status.asInt() < LOWEST_STATUS || status.asInt() > HIGHEST_STATUS) {
         throw new MockRepositoryImportException(refusal + "expects status " + status.asText() + " at step " + stepId
               + ", not an exact HTTP status code");
      }
      return status.asInt();
   }
}
