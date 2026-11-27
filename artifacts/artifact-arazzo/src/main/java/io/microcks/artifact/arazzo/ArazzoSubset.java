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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The part of the Arazzo specification Microcks supports. A document outside of it is refused rather than partly
 * ignored.
 * @author SebastienDegodez
 */
final class ArazzoSubset {

   /** The runtime expression prefix of an operationId that names its source description. */
   static final String SOURCE_DESCRIPTIONS_EXPRESSION = "$sourceDescriptions.";

   private static final List<String> UNSUPPORTED_WORKFLOW_FIELDS = List.of("dependsOn", "successActions",
         "failureActions");
   /** The type of a source description whose steps send or receive messages, since Arazzo 1.1. */
   static final String ASYNCAPI = "asyncapi";

   private static final String OPENAPI = "openapi";
   private static final Set<String> SOURCE_TYPES = Set.of(OPENAPI, ASYNCAPI);
   private static final List<String> UNSUPPORTED_STEP_FIELDS = List.of("workflowId", "operationPath", "dependsOn");
   /** The fields of a step that only apply to a step of an AsyncAPI source. */
   private static final List<String> ASYNC_STEP_FIELDS = List.of("channelPath", "action", "correlationId");
   private static final Set<String> MESSAGING_ACTIONS = Set.of(ArazzoMessaging.SEND, ArazzoMessaging.RECEIVE);
   /**
    * A channelPath points to a channel of a source description: the runtime expression of the url of the source, then
    * the JSON pointer of the channel in it.
    */
   private static final Pattern CHANNEL_PATH = Pattern
         .compile("\\{\\$sourceDescriptions\\.([^.}]+)\\.url}#/channels/([^/]+)");
   private static final String INVALID = "Invalid Arazzo document: ";
   private static final Set<String> PARAMETER_LOCATIONS = Set.of("path", "query", "header");
   private static final Set<String> CRITERION_TYPES = Set.of("simple", "regex");
   private static final Set<String> ACTION_TYPES = Set.of(ArazzoAction.RETRY, ArazzoAction.END);

   private ArazzoSubset() {
   }

   /**
    * @param document The Arazzo document, parsed from YAML or JSON
    * @throws UnsupportedArazzoDocumentException if the document is outside of the subset
    */
   static void check(JsonNode document) throws UnsupportedArazzoDocumentException {
      String arazzoVersion = document.path("arazzo").asText();
      if (!arazzoVersion.startsWith("1.")) {
         throw new UnsupportedArazzoDocumentException("Unsupported Arazzo version: " + arazzoVersion);
      }
      checkInfo(document.path("info"));
      for (JsonNode sourceDescription : document.path("sourceDescriptions")) {
         checkSourceDescription(sourceDescription);
      }
      Map<String, String> sourceTypes = sourceTypes(document);
      for (JsonNode workflow : document.path("workflows")) {
         checkWorkflow(workflow, sourceTypes);
      }
   }

   /**
    * @param document The Arazzo document, parsed from YAML or JSON
    * @return The type of each source description, by name in the order of the document; openapi when not told
    */
   static Map<String, String> sourceTypes(JsonNode document) {
      Map<String, String> types = new LinkedHashMap<>();
      for (JsonNode sourceDescription : document.path("sourceDescriptions")) {
         types.put(sourceDescription.path("name").asText(), typeOf(sourceDescription));
      }
      return types;
   }

   private static String typeOf(JsonNode sourceDescription) {
      return sourceDescription.path("type").asText(OPENAPI);
   }

   /**
    * Arazzo requires an info with a title and a version: they identify the document in Microcks.
    */
   private static void checkInfo(JsonNode info) throws UnsupportedArazzoDocumentException {
      for (String field : List.of("title", "version")) {
         if (info.path(field).asText().isBlank()) {
            throw new UnsupportedArazzoDocumentException("Invalid Arazzo document: info." + field + " is required");
         }
      }
   }

   private static void checkSourceDescription(JsonNode sourceDescription) throws UnsupportedArazzoDocumentException {
      String type = typeOf(sourceDescription);
      if (!SOURCE_TYPES.contains(type)) {
         throw new UnsupportedArazzoDocumentException("Unsupported source description type: " + type);
      }
   }

   private static void checkWorkflow(JsonNode workflow, Map<String, String> sourceTypes)
         throws UnsupportedArazzoDocumentException {
      rejectFields(workflow, UNSUPPORTED_WORKFLOW_FIELDS, "workflow");
      for (JsonNode parameter : workflow.path("parameters")) {
         checkParameter(parameter);
      }
      for (JsonNode step : workflow.path("steps")) {
         checkStep(step, sourceTypes);
      }
   }

   private static void checkStep(JsonNode step, Map<String, String> sourceTypes)
         throws UnsupportedArazzoDocumentException {
      rejectFields(step, UNSUPPORTED_STEP_FIELDS, "step");
      for (JsonNode parameter : step.path("parameters")) {
         checkParameter(parameter);
      }
      for (JsonNode criterion : step.path("successCriteria")) {
         checkCriterion(criterion);
      }
      for (JsonNode action : step.path("onSuccess")) {
         checkAction(action);
         if (action.path("type").asText().equals(ArazzoAction.RETRY)) {
            throw new UnsupportedArazzoDocumentException("Invalid Arazzo document: a success action cannot retry");
         }
      }
      for (JsonNode action : step.path("onFailure")) {
         checkAction(action);
      }
      List<String> sourceNames = List.copyOf(sourceTypes.keySet());
      String stepId = step.path("stepId").asText();
      String sourceName;
      if (step.has("channelPath")) {
         if (step.has("operationId")) {
            throw new UnsupportedArazzoDocumentException(
                  INVALID + "a step names an operationId or a channelPath, not both: " + stepId);
         }
         sourceName = checkChannelPath(step.path("channelPath").asText(), stepId, sourceNames);
      } else {
         String operationId = step.path("operationId").asText();
         checkOperationId(operationId, sourceNames);
         sourceName = sourceNames.isEmpty() ? null : ArazzoDocumentReader.call(operationId, sourceNames).sourceName();
      }
      if (ASYNCAPI.equals(sourceTypes.get(sourceName))) {
         checkAsyncStep(step, stepId);
      } else {
         checkHttpStep(step, stepId);
      }
   }

   /**
    * Arazzo only lets a step of an AsyncAPI source send or receive messages. Arazzo lets an HTTP step tell its timeout,
    * but the runner gives every HTTP step the timeout of the run: it is refused until the runner applies it.
    */
   private static void checkHttpStep(JsonNode step, String stepId) throws UnsupportedArazzoDocumentException {
      for (String field : ASYNC_STEP_FIELDS) {
         if (step.has(field)) {
            throw new UnsupportedArazzoDocumentException(
                  INVALID + field + " only applies to a step of an AsyncAPI source: " + stepId);
         }
      }
      rejectFields(step, List.of("timeout"), "step");
   }

   /**
    * A step of an AsyncAPI source sends or receives, only receives a message by its correlationId, and waits a number
    * of milliseconds. A step on a channel tells its action, as a channel, unlike an operation, does not tell it.
    */
   private static void checkAsyncStep(JsonNode step, String stepId) throws UnsupportedArazzoDocumentException {
      String action = step.path("action").asText(null);
      if (action != null && !MESSAGING_ACTIONS.contains(action)) {
         throw new UnsupportedArazzoDocumentException(
               INVALID + "a step action is send or receive, not " + action + ": " + stepId);
      }
      if (action == null && step.has("channelPath")) {
         throw new UnsupportedArazzoDocumentException(
               INVALID + "a step on a channel tells its action, send or receive: " + stepId);
      }
      if (step.has("correlationId") && !ArazzoMessaging.RECEIVE.equals(action)) {
         throw new UnsupportedArazzoDocumentException(INVALID + "only a receive step has a correlationId: " + stepId);
      }
      JsonNode timeout = step.path("timeout");
      if (!timeout.isMissingNode()
            && !(timeout.isIntegralNumber() && timeout.canConvertToLong() && timeout.asLong() >= 0)) {
         throw new UnsupportedArazzoDocumentException(
               INVALID + "a step timeout is a number of milliseconds, not " + timeout.asText() + ": " + stepId);
      }
   }

   /**
    * @return The name of the source description the channelPath points into
    * @throws UnsupportedArazzoDocumentException if it does not point to a channel of a source the document declares
    */
   private static String checkChannelPath(String channelPath, String stepId, List<String> sourceNames)
         throws UnsupportedArazzoDocumentException {
      Matcher matcher = CHANNEL_PATH.matcher(channelPath);
      if (!matcher.matches()) {
         throw new UnsupportedArazzoDocumentException(
               INVALID + "channelPath does not point to a channel of a source description: " + stepId);
      }
      if (!sourceNames.contains(matcher.group(1))) {
         throw new UnsupportedArazzoDocumentException("Unknown source description: " + matcher.group(1));
      }
      return matcher.group(1);
   }

   /**
    * @param channelPath A channelPath within the subset
    * @return The channel it points to, in its source description: the name of the channel is unescaped from its JSON
    *         pointer, ~1 standing for a slash and ~0 for a tilde
    */
   static ArazzoOperationCall channelCall(String channelPath) {
      Matcher matcher = CHANNEL_PATH.matcher(channelPath);
      matcher.matches();
      return new ArazzoOperationCall(matcher.group(1), null, matcher.group(2).replace("~1", "/").replace("~0", "~"));
   }

   /**
    * The runner retries a step and ends a workflow; it does not go to another step or workflow, nor read reusable
    * actions of the components.
    */
   private static void checkAction(JsonNode action) throws UnsupportedArazzoDocumentException {
      if (action.has("reference")) {
         throw new UnsupportedArazzoDocumentException(
               "Unsupported reusable action: " + action.path("reference").asText());
      }
      String type = action.path("type").asText();
      if (!ACTION_TYPES.contains(type)) {
         throw new UnsupportedArazzoDocumentException("Unsupported action type: " + type);
      }
      for (JsonNode criterion : action.path("criteria")) {
         checkCriterion(criterion);
      }
   }

   /**
    * Arazzo requires an operationId to name a source description the document declares, and to name one when there are
    * several.
    */
   private static void checkOperationId(String operationId, List<String> sourceNames)
         throws UnsupportedArazzoDocumentException {
      if (operationId.startsWith(SOURCE_DESCRIPTIONS_EXPRESSION)) {
         String sourceName = sourceNameOf(operationId);
         if (!sourceNames.contains(sourceName)) {
            throw new UnsupportedArazzoDocumentException("Unknown source description: " + sourceName);
         }
      } else if (sourceNames.size() > 1) {
         throw new UnsupportedArazzoDocumentException(
               "Ambiguous operationId " + operationId + ": the document has several source descriptions");
      }
   }

   /**
    * @param operationId An operationId naming its source description
    * @return The name of that source description: a source name has no dot, an operationId may have some
    */
   static String sourceNameOf(String operationId) {
      return operationId.substring(SOURCE_DESCRIPTIONS_EXPRESSION.length(),
            operationId.indexOf('.', SOURCE_DESCRIPTIONS_EXPRESSION.length()));
   }

   private static void checkParameter(JsonNode parameter) throws UnsupportedArazzoDocumentException {
      String location = parameter.path("in").asText();
      if (!PARAMETER_LOCATIONS.contains(location)) {
         throw new UnsupportedArazzoDocumentException("Unsupported parameter location: " + location);
      }
   }

   private static void checkCriterion(JsonNode criterion) throws UnsupportedArazzoDocumentException {
      String type = criterion.path("type").asText("simple");
      if (!CRITERION_TYPES.contains(type)) {
         throw new UnsupportedArazzoDocumentException("Unsupported criterion type: " + type);
      }
   }

   private static void rejectFields(JsonNode node, List<String> fields, String owner)
         throws UnsupportedArazzoDocumentException {
      for (String field : fields) {
         if (node.has(field)) {
            throw new UnsupportedArazzoDocumentException("Unsupported " + owner + " field: " + field);
         }
      }
   }
}
