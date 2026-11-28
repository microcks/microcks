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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads the workflows of an Arazzo document, step by step: what each step calls, sends, checks and gives. Shared by the
 * Microcks webapp, which shows them, and the components that run them.
 * @author SebastienDegodez
 */
public final class ArazzoWorkflowReader {

   private static final String DEFAULT_CRITERION_TYPE = "simple";

   private ArazzoWorkflowReader() {
   }

   /**
    * @param document   The Arazzo document, parsed from YAML or JSON, within the subset Microcks supports
    * @param workflowId The id of a workflow of the document
    * @return The workflow, empty if the document has no workflow with that id
    */
   public static Optional<ArazzoWorkflow> workflow(JsonNode document, String workflowId) {
      Map<String, String> sourceTypes = ArazzoSubset.sourceTypes(document);
      for (JsonNode workflow : document.path("workflows")) {
         if (workflow.path("workflowId").asText().equals(workflowId)) {
            return Optional.of(workflow(workflow, sourceTypes));
         }
      }
      return Optional.empty();
   }

   /**
    * @param workflow    A workflow of an Arazzo document, parsed from YAML or JSON
    * @param sourceTypes The type of each source description of the document, by name
    * @return The workflow, also read by {@link ArazzoSubset} to check the order of its steps
    */
   static ArazzoWorkflow workflow(JsonNode workflow, Map<String, String> sourceTypes) {
      List<ArazzoStep> steps = new ArrayList<>();
      for (JsonNode step : workflow.path("steps")) {
         steps.add(step(step, sourceTypes));
      }
      List<String> inputNames = new ArrayList<>();
      Map<String, String> inputTypes = new LinkedHashMap<>();
      workflow.path("inputs").path("properties").properties().forEach(input -> {
         inputNames.add(input.getKey());
         inputTypes.put(input.getKey(), input.getValue().path("type").asText(null));
      });
      return new ArazzoWorkflow(workflow.path("workflowId").asText(), optionalText(workflow, "summary"),
            optionalText(workflow, "description"), inputNames, steps, expressions(workflow.path("outputs")), inputTypes,
            texts(workflow.path("inputs").path("required")));
   }

   private static List<String> texts(JsonNode array) {
      List<String> texts = new ArrayList<>();
      array.forEach(item -> texts.add(item.asText()));
      return texts;
   }

   private static ArazzoStep step(JsonNode step, Map<String, String> sourceTypes) {
      ArazzoOperationCall call = ArazzoDocumentReader.call(step, List.copyOf(sourceTypes.keySet()));
      List<ArazzoParameter> parameters = new ArrayList<>();
      for (JsonNode parameter : step.path("parameters")) {
         parameters.add(new ArazzoParameter(parameter.path("name").asText(), parameter.path("in").asText(),
               text(parameter.path("value"))));
      }
      return new ArazzoStep(step.path("stepId").asText(), optionalText(step, "description"), call, parameters,
            requestBody(step.path("requestBody")), criteria(step.path("successCriteria")),
            expressions(step.path("outputs")), actions(step.path("onSuccess")), actions(step.path("onFailure")),
            messaging(step, sourceTypes.get(call.sourceName())),
            step.has("dependsOn") ? texts(step.path("dependsOn")) : null);
   }

   /** A step of an AsyncAPI source tells what it sends or receives; a step calling an HTTP operation does not. */
   private static ArazzoMessaging messaging(JsonNode step, String sourceType) {
      if (!ArazzoSubset.ASYNCAPI.equals(sourceType)) {
         return null;
      }
      Long timeout = step.has("timeout") ? step.path("timeout").asLong() : null;
      return new ArazzoMessaging(optionalText(step, "action"), optionalText(step, "correlationId"), timeout);
   }

   private static List<ArazzoCriterion> criteria(JsonNode criteria) {
      List<ArazzoCriterion> read = new ArrayList<>();
      for (JsonNode criterion : criteria) {
         read.add(new ArazzoCriterion(criterion.path("condition").asText(), optionalText(criterion, "context"),
               criterion.path("type").asText(DEFAULT_CRITERION_TYPE)));
      }
      return read;
   }

   /** A retry waits 0 second and is attempted once when the document does not tell, as Arazzo says. */
   private static List<ArazzoAction> actions(JsonNode actions) {
      List<ArazzoAction> read = new ArrayList<>();
      for (JsonNode action : actions) {
         String type = action.path("type").asText();
         boolean retry = type.equals(ArazzoAction.RETRY);
         read.add(new ArazzoAction(action.path("name").asText(), type,
               retry ? action.path("retryAfter").asDouble(0) : null, retry ? action.path("retryLimit").asInt(1) : null,
               criteria(action.path("criteria"))));
      }
      return read;
   }

   private static ArazzoRequestBody requestBody(JsonNode requestBody) {
      if (requestBody.isMissingNode()) {
         return null;
      }
      Map<String, String> replacements = new LinkedHashMap<>();
      for (JsonNode replacement : requestBody.path("replacements")) {
         replacements.put(replacement.path("target").asText(), text(replacement.path("value")));
      }
      return new ArazzoRequestBody(optionalText(requestBody, "contentType"), text(requestBody.path("payload")),
            replacements);
   }

   /** Outputs map their names to runtime expressions. */
   private static Map<String, String> expressions(JsonNode outputs) {
      Map<String, String> expressions = new LinkedHashMap<>();
      outputs.properties().forEach(output -> expressions.put(output.getKey(), output.getValue().asText()));
      return expressions;
   }

   private static String optionalText(JsonNode node, String field) {
      return node.path(field).asText(null);
   }

   /** A value is written as text, or as a number or a JSON structure that Microcks shows as JSON. */
   private static String text(JsonNode value) {
      return value.isTextual() ? value.asText() : value.toPrettyString();
   }
}
