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
package io.microcks.artifact.openapi;

import io.microcks.artifact.jsonschema.JsonSchemaViolation;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validates the responses of the operations of an API against the schemas of its OpenAPI 3 or Swagger 2 contract, the
 * way OpenAPI contract tests do: a response with content and a JSON content type is validated against the schema the
 * contract gives for its operation, its status and, for OpenAPI 3, its content type; any other response is not
 * validated.
 * @author SebastienDegodez
 */
public class OpenAPIResponseValidator {

   /** Content-types for JSON that are valid response types validated using JSON Schemas. */
   public static final Pattern APPLICATION_JSON_TYPES_PATTERN = Pattern.compile("application/.*json");

   private static final String CHARSET = "charset=";

   private final JsonNode contract;
   private final boolean swagger;
   private final String namespace;

   /**
    * Build a validator of the responses described by a contract.
    * @param contract  The contract, as {@link OpenAPISchemaValidator#getJsonNodeForSchema(String)} reads it
    * @param swagger   Whether the contract is a Swagger 2 one rather than an OpenAPI 3 one
    * @param namespace Where the relative references of the contract are resolved from; null for none
    */
   public OpenAPIResponseValidator(JsonNode contract, boolean swagger, String namespace) {
      this.contract = contract;
      this.swagger = swagger;
      this.namespace = namespace;
   }

   /**
    * Build a validator of the responses described by a contract, which tells whether it is a Swagger 2 one.
    * @param contract  The contract, as {@link OpenAPISchemaValidator#getJsonNodeForSchema(String)} reads it
    * @param namespace Where the relative references of the contract are resolved from; null for none
    * @return The validator, of a Swagger 2 contract when it has a swagger field
    */
   public static OpenAPIResponseValidator of(JsonNode contract, String namespace) {
      return new OpenAPIResponseValidator(contract, contract.has("swagger"), namespace);
   }

   /**
    * Tell whether a response is validated: one with content (any status but 204) and a JSON content type, such as
    * application/json or application/problem+json, with or without charset.
    * @param status      The status of the response
    * @param contentType The content type of the response, with its parameters; null when it has none
    * @return Whether the response is validated against its schema
    */
   public static boolean isValidated(int status, String contentType) {
      String shortContentType = shortContentTypeOf(contentType);
      return status != 204 && shortContentType != null
            && APPLICATION_JSON_TYPES_PATTERN.matcher(shortContentType).matches();
   }

   /**
    * Validate a response of an operation against the schema the contract gives for it. Only meaningful for a response
    * that {@link #isValidated(int, String)}.
    * @param verb        The verb of the operation, such as GET
    * @param path        The path of the operation, as the contract writes it, such as /pastry/{name}
    * @param status      The status of the response
    * @param contentType The content type of the response, with its parameters
    * @param body        The body of the response
    * @return The violations; a violation without pointer, whose message is
    *         {@link OpenAPISchemaValidator#MESSAGE_NOT_FOUND}, {@link OpenAPISchemaValidator#CONTENT_NOT_FOUND} or
    *         {@link OpenAPISchemaValidator#SCHEMA_NOT_FOUND}, when the contract does not describe the response or its
    *         schema. If empty, the response conforms to its schema
    * @throws IOException if the body is not JSON
    */
   public List<JsonSchemaViolation> validate(String verb, String path, int status, String contentType, String body)
         throws IOException {
      JsonNode bodyNode = OpenAPISchemaValidator.getJsonNode(body);
      String responsePointer = responsePointerOf(verb, path, status);
      if (swagger) {
         return SwaggerSchemaValidator.validateJsonMessageWithPointers(contract, bodyNode, responsePointer, namespace);
      }
      return OpenAPISchemaValidator.validateJsonMessageWithPointers(contract, bodyNode, responsePointer, contentType,
            namespace);
   }

   /**
    * Find the schema the contract gives for a response of an operation, the one {@link #validate} validates it against.
    * @param verb        The verb of the operation, such as GET
    * @param path        The path of the operation, as the contract writes it, such as /pastry/{name}
    * @param status      The status of the response
    * @param contentType The content type of the response, with its parameters
    * @return The schema, such as {"$ref": "#/components/schemas/Pastry"}; a missing node when there is none
    */
   public JsonNode schemaOf(String verb, String path, int status, String contentType) {
      JsonNode response = contract.at(responsePointerOf(verb, path, status));
      // A response can be just a reference.
      if (response.has("$ref")) {
         response = contract.at(response.path("$ref").asText().substring(1));
      }
      if (swagger) {
         return response.path("schema");
      }
      return OpenAPISchemaValidator.getMessageContentNode(response, contentType).path("schema");
   }

   /** The JSON pointer of a response in the contract, such as /paths/~1pastry~1{name}/get/responses/200. */
   private static String responsePointerOf(String verb, String path, int status) {
      return "/paths/" + path.replace("/", "~1") + "/" + verb.toLowerCase(Locale.ROOT) + "/responses/" + status;
   }

   /** A content type without its charset, such as application/json for application/json;charset=UTF-8. */
   private static String shortContentTypeOf(String contentType) {
      if (contentType != null && contentType.contains(CHARSET) && contentType.contains(";")) {
         return contentType.substring(0, contentType.indexOf(";"));
      }
      return contentType;
   }
}
