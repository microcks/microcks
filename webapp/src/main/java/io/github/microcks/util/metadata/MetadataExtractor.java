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
package io.github.microcks.util.metadata;

import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Operation;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Util methods for extracting Metadata or operation properties from JsonNode.
 * @author laurent
 */
public class MetadataExtractor {

   /**
    * Complete a Metadata object with extracted metadata from JsonNode.
    * @param metadata The object to complete
    * @param node     Node representing a metadata node
    */
   public static void completeMetadata(Metadata metadata, JsonNode node) {
      JsonNode annotationsNode = node.get("annotations");
      if (annotationsNode != null) {
         annotationsNode.fields()
               .forEachRemaining(entry -> metadata.setAnnotation(entry.getKey(), entry.getValue().asText()));
      }
      JsonNode labelsNode = node.get("labels");
      if (labelsNode != null) {
         labelsNode.fields().forEachRemaining(entry -> metadata.setLabel(entry.getKey(), entry.getValue().asText()));
      }
   }

   /**
    * Complete an Operation object with extracted properties from JsonNode.
    * @param operation The object to complete
    * @param node      Node representing an operation node
    */
   public static void completeOperationProperties(Operation operation, JsonNode node) {
      if (node.has("delay")) {
         operation.setDefaultDelay(node.path("delay").asLong(0));
      }
      if (node.has("frequency")) {
         operation.setDefaultDelay(node.path("frequency").asLong());
      }
      if (node.has("dispatcher")) {
         operation.setDispatcher(node.path("dispatcher").asText());
      }
      if (node.has("dispatcherRules")) {
         operation.setDispatcherRules(node.path("dispatcherRules").asText());
      }
   }
}
