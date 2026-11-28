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

import io.microcks.artifact.arazzo.ArazzoReceivedRequest;
import io.microcks.artifact.jsonschema.JsonSchemaViolation;
import io.microcks.artifact.openapi.OpenAPISchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Checks a request an application sent to the mocks against the OpenAPI contract of the API it called: the parameters
 * the operation or its path requires in query or header must be there, and a JSON body must follow the schema of the
 * request body, which must be there when it is required. The body is validated as Microcks validates the requests of
 * its mocks.
 * @author SebastienDegodez
 */
final class ArazzoRequestConformance {

   private ArazzoRequestConformance() {
   }

   /**
    * @param contract     The OpenAPI contract of the API; null when it has none
    * @param method       The method of the operation, such as POST
    * @param pathTemplate The path template of the operation
    * @param request      The request the application sent
    * @param contentType  The content type of the request; null when it gives none
    * @param resourceUrl  The URL the relative references of the contract resolve against; null for none
    * @return Why the request does not conform to the contract, in the order of the checks; empty when it conforms or
    *         when the contract does not describe the operation
    */
   static List<String> violations(JsonNode contract, String method, String pathTemplate, ArazzoReceivedRequest request,
         String contentType, String resourceUrl) {
      if (contract == null) {
         return List.of();
      }
      String operationPointer = "/paths/" + pathTemplate.replace("~", "~0").replace("/", "~1") + "/"
            + method.toLowerCase(Locale.ROOT);
      JsonNode operation = contract.at(operationPointer);
      if (operation.isMissingNode()) {
         return List.of();
      }
      List<String> violations = new ArrayList<>();
      List<JsonNode> parameters = new ArrayList<>();
      contract.at(operationPointer.substring(0, operationPointer.lastIndexOf('/')) + "/parameters")
            .forEach(parameters::add);
      operation.path("parameters").forEach(parameters::add);
      for (JsonNode parameter : parameters) {
         JsonNode resolved = resolve(contract, parameter);
         String in = resolved.path("in").asText();
         String name = resolved.path("name").asText();
         if (resolved.path("required").asBoolean() && !"path".equals(in) && request.parameter(in, name).isEmpty()) {
            violations.add("required " + in + " parameter `" + name + "` is missing");
         }
      }
      JsonNode requestBody = resolve(contract, operation.path("requestBody"));
      String body = request.body();
      if (body == null || body.isBlank()) {
         if (requestBody.path("required").asBoolean()) {
            violations.add("the request body is required");
         }
         return violations;
      }
      String mediaType = contentType == null ? "" : contentType.split(";")[0].strip();
      if (mediaType.contains("json") && requestBody.path("content").has(mediaType)) {
         violations.addAll(bodyViolations(contract, operationPointer, body, mediaType, resourceUrl));
      }
      return violations;
   }

   private static List<String> bodyViolations(JsonNode contract, String operationPointer, String body, String mediaType,
         String resourceUrl) {
      JsonNode json;
      try {
         json = OpenAPISchemaValidator.getJsonNode(body);
      } catch (IOException notJson) {
         return List.of("the request body is not JSON");
      }
      List<JsonSchemaViolation> violations = OpenAPISchemaValidator.validateJsonMessageWithPointers(contract, json,
            operationPointer + "/requestBody", mediaType, resourceUrl);
      // A violation without pointer is about the contract, such as a content without schema: there is nothing to check.
      return violations.stream().filter(violation -> violation.pointer() != null)
            .map(violation -> "the request body does not follow its schema at " + violation.pointer() + ": "
                  + violation.message())
            .toList();
   }

   /** A parameter or a request body may be a reference to the components of the contract. */
   private static JsonNode resolve(JsonNode contract, JsonNode node) {
      String ref = node.path("$ref").asText();
      return ref.startsWith("#/") ? contract.at(ref.substring(1)) : node;
   }
}
