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

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The media types an operation accepts for its request body, as the OpenAPI or Swagger contract of its API declares
 * them. Arazzo refers to them when a step does not give the contentType of its request body.
 * @author SebastienDegodez
 */
final class ArazzoRequestMediaTypes {

   private ArazzoRequestMediaTypes() {
   }

   /**
    * @param contract The OpenAPI or Swagger contract of the API; null when it has none
    * @param method   The HTTP method of the operation, such as PATCH
    * @param path     The path template of the operation
    * @return The media types the operation accepts for its request body, in the order of the contract; empty when it
    *         declares none
    */
   static List<String> of(JsonNode contract, String method, String path) {
      if (contract == null) {
         return List.of();
      }
      JsonNode operation = contract.path("paths").path(path).path(method.toLowerCase(Locale.ROOT));
      if (operation.isMissingNode()) {
         return List.of();
      }
      // Swagger 2 lists the media types apart from the body: the operation's own, else those of the contract.
      JsonNode consumes = operation.has("consumes") ? operation.get("consumes") : contract.path("consumes");
      List<String> mediaTypes = new ArrayList<>();
      if (consumes.isArray()) {
         consumes.forEach(mediaType -> mediaTypes.add(mediaType.asText()));
      } else {
         JsonNode requestBody = resolve(contract, operation.path("requestBody"));
         requestBody.path("content").fieldNames().forEachRemaining(mediaTypes::add);
      }
      return mediaTypes;
   }

   /** A request body may be a reference to the components of the contract. */
   private static JsonNode resolve(JsonNode contract, JsonNode node) {
      String ref = node.path("$ref").asText();
      return ref.startsWith("#/") ? contract.at(ref.substring(1)) : node;
   }
}
