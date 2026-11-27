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

/**
 * Reads an Arazzo document. Shared by the Microcks webapp, which imports documents, and the components that run their
 * workflows: it knows the Arazzo specification only, and no Microcks model.
 * @author SebastienDegodez
 */
public final class ArazzoDocumentReader {

   private ArazzoDocumentReader() {
   }

   /**
    * @param document A document parsed from YAML or JSON
    * @return Whether the document declares the version of Arazzo it uses, as every Arazzo document does
    */
   public static boolean isArazzoDocument(JsonNode document) {
      return document.has("arazzo");
   }

   /**
    * @param document The Arazzo document, parsed from YAML or JSON
    * @return What identifies the document
    * @throws UnsupportedArazzoDocumentException if the document is outside of the subset Microcks supports
    */
   public static ArazzoDescription read(JsonNode document) throws UnsupportedArazzoDocumentException {
      ArazzoSubset.check(document);
      JsonNode info = document.path("info");
      List<String> workflowIds = new ArrayList<>();
      for (JsonNode workflow : document.path("workflows")) {
         workflowIds.add(workflow.path("workflowId").asText());
      }
      return new ArazzoDescription(info.path("title").asText(), info.path("version").asText(), workflowIds);
   }

   /**
    * @param document The Arazzo document, parsed from YAML or JSON
    * @return The version of Arazzo the document uses
    */
   public static String arazzoVersion(JsonNode document) {
      return document.path("arazzo").asText();
   }

   /**
    * @param document The Arazzo document, parsed from YAML or JSON
    * @return The names of its source descriptions, in the order of the document
    */
   public static List<String> sourceDescriptionNames(JsonNode document) {
      List<String> names = new ArrayList<>();
      for (JsonNode sourceDescription : document.path("sourceDescriptions")) {
         names.add(sourceDescription.path("name").asText());
      }
      return names;
   }

   /**
    * @param document The Arazzo document, parsed from YAML or JSON
    * @return The url each source description declares for its contract, by name, in the order of the document
    */
   public static Map<String, String> sourceDescriptionUrls(JsonNode document) {
      Map<String, String> urls = new LinkedHashMap<>();
      for (JsonNode sourceDescription : document.path("sourceDescriptions")) {
         urls.put(sourceDescription.path("name").asText(), sourceDescription.path("url").asText());
      }
      return urls;
   }

   /**
    * @param document The Arazzo document, parsed from YAML or JSON
    * @return For each workflow id, its summary, or else its description; null when it has neither
    */
   public static Map<String, String> workflowDescriptions(JsonNode document) {
      Map<String, String> descriptions = new LinkedHashMap<>();
      for (JsonNode workflow : document.path("workflows")) {
         JsonNode description = workflow.has("summary") ? workflow.path("summary") : workflow.path("description");
         descriptions.put(workflow.path("workflowId").asText(), description.textValue());
      }
      return descriptions;
   }

   /**
    * @param document The Arazzo document, parsed from YAML or JSON
    * @return For each workflow id, the operations its steps call, in the order of the steps
    */
   public static Map<String, List<ArazzoOperationCall>> workflowCalls(JsonNode document) {
      List<String> sourceNames = sourceDescriptionNames(document);
      Map<String, List<ArazzoOperationCall>> workflowCalls = new LinkedHashMap<>();
      for (JsonNode workflow : document.path("workflows")) {
         List<ArazzoOperationCall> calls = new ArrayList<>();
         for (JsonNode step : workflow.path("steps")) {
            calls.add(call(step.path("operationId").asText(), sourceNames));
         }
         workflowCalls.put(workflow.path("workflowId").asText(), calls);
      }
      return workflowCalls;
   }

   /** The operation a step calls, also read by {@link ArazzoWorkflowReader}. */
   static ArazzoOperationCall call(String operationId, List<String> sourceNames) {
      if (operationId.startsWith(ArazzoSubset.SOURCE_DESCRIPTIONS_EXPRESSION)) {
         String sourceName = ArazzoSubset.sourceNameOf(operationId);
         return new ArazzoOperationCall(sourceName,
               operationId.substring(ArazzoSubset.SOURCE_DESCRIPTIONS_EXPRESSION.length() + sourceName.length() + 1));
      }
      // Arazzo requires an operationId to name its source when there are several: alone, it calls the only one.
      return new ArazzoOperationCall(sourceNames.get(0), operationId);
   }
}
