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
package io.github.microcks.util.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Helper class for validating Json objects against their Swagger schema. Supported version of Swagger schema is
 * https://swagger.io/specification/v2/.
 * @author laurent
 */
public class SwaggerSchemaValidator {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(SwaggerSchemaValidator.class);


   /** Private constructor to hide the implicit one. */
   private SwaggerSchemaValidator() {
   }

   /**
    * Validate a Json object representing a Swagger message (response or request) against a node representing a full
    * OpenAPI specification (and not just a schema node). Specify the message by providing a valid JSON pointer for
    * <code>messagePathPointer</code> within specification. Validation is a deep one: its pursue checking children nodes
    * on a failed parent. Validation is respectful of Swagger schema spec semantics regarding additional or unknown
    * attributes: schema must explicitly set <code>additionalProperties</code> to false if you want to consider unknown
    * attributes as validation errors. It returns a list of validation error messages.
    * @param specificationNode  The Swagger full specification as a Jackson node
    * @param jsonNode           The Json object representing actual message as a Jackson node
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJsonMessage(JsonNode specificationNode, JsonNode jsonNode,
         String messagePathPointer) {
      return validateJsonMessage(specificationNode, jsonNode, messagePathPointer, null);
   }

   /**
    * Validate a Json object representing a Swagger message (response or request) against a node representing a full
    * Swagger specification (and not just a schema node). Specify the message by providing a valid JSON pointer for
    * <code>messagePathPointer</code> within specification. Validation is a deep one: its pursue checking children nodes
    * on a failed parent. Validation is respectful of Swagger schema spec semantics regarding additional or unknown
    * attributes: schema must explicitly set <code>additionalProperties</code> to false if you want to consider unknown
    * attributes as validation errors. It returns a list of validation error messages.
    * @param specificationNode  The Swagger full specification as a Jackson node
    * @param jsonNode           The Json object representing actual message as a Jackson node
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @param namespace          Namespace definition to resolve relative dependencies in Swagger schema
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJsonMessage(JsonNode specificationNode, JsonNode jsonNode,
         String messagePathPointer, String namespace) {
      // Extract specific content type node for message node.
      JsonNode messageNode = specificationNode.at(messagePathPointer);
      if (messageNode == null || messageNode.isMissingNode()) {
         log.debug("messagePathPointer {} is not a valid JSON Pointer", messagePathPointer);
         return List.of("messagePathPointer does not represent a valid JSON Pointer in OpenAPI specification");
      }
      // Message node can be just a reference.
      if (messageNode.has("$ref")) {
         String ref = messageNode.path("$ref").asText();
         messageNode = specificationNode.at(ref.substring(1));
      }
      if (messageNode == null || messageNode.isMissingNode()) {
         log.debug("Schema node for message cannot be found into Swagger specification");
         return List.of("messagePathPointer does not represent an existing JSON Pointer in OpenAPI specification");
      }

      // Build a schema object with responseNode schema as root and by importing
      // all the definitions parts that may be referenced by references.
      JsonNode schemaNode = messageNode.path("schema").deepCopy();
      ((ObjectNode) schemaNode).set("definitions", specificationNode.path("definitions").deepCopy());

      return OpenAPISchemaValidator.validateJson(schemaNode, jsonNode, namespace);
   }
}
