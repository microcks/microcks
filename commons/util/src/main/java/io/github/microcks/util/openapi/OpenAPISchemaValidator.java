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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.github.microcks.util.JsonSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.microcks.util.JsonSchemaValidator.*;

/**
 * Helper class for validating Json objects against their OpenAPI schema. Supported version of OpenAPI schema is
 * https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.1.md.
 * @author laurent
 */
public class OpenAPISchemaValidator {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OpenAPISchemaValidator.class);

   private static final String ONE_OF = "oneOf";
   private static final String NULLABLE = "nullable";
   private static final String[] STRUCTURES = { "allOf", "anyOf", ONE_OF, "not", "items", "additionalProperties" };
   private static final String[] COMPOSITION_STRUCTURES = { "allOf", "anyOf", ONE_OF };

   private static final String[] NOT_SUPPORTED_ATTRIBUTES = { NULLABLE, "discriminator", "readOnly", "writeOnly", "xml",
         "externalDocs", "example", "deprecated" };


   /** Private constructor to hide the implicit one. */
   private OpenAPISchemaValidator() {
   }

   /**
    * Check if a Json object is valid against the given OpenAPI schema specification.
    * @param schemaText The OpenAPI schema specification as a string
    * @param jsonText   The Json object as a string
    * @return True if Json object is valid, false otherwise
    * @throws IOException if string representations cannot be parsed
    */
   public static boolean isJsonValid(String schemaText, String jsonText) throws IOException {
      return isJsonValid(schemaText, jsonText, null);
   }


   /**
    * Check if a Json object is valid against the given OpenAPI schema specification.
    * @param schemaText The OpenAPI schema specification as a string
    * @param jsonText   The Json object as a string
    * @param namespace  Namespace definition to resolve relative dependencies in Json schema
    * @return True if Json object is valid, false otherwise
    * @throws IOException if string representations cannot be parsed
    */
   public static boolean isJsonValid(String schemaText, String jsonText, String namespace) throws IOException {
      List<String> errors = validateJson(schemaText, jsonText, namespace);
      if (!errors.isEmpty()) {
         log.debug("Get validation errors, returning false");
         return false;
      }
      return true;
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of OpenAPI schema
    * spec semantics regarding additional or unknown attributes: schema must explicitly set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param schemaText The OpenAPI schema specification as a string
    * @param jsonText   The Json object as a string
    * @return The list of validation failures. If empty, json object is valid !
    * @throws IOException if json string representations cannot be parsed
    */
   public static List<String> validateJson(String schemaText, String jsonText) throws IOException {
      return validateJson(schemaText, jsonText, null);
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of OpenAPI schema
    * spec semantics regarding additional or unknown attributes: schema must explicitly set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param schemaText The OpenAPI schema specification as a string
    * @param jsonText   The Json object as a string
    * @param namespace  Namespace definition to resolve relative dependencies in Json schema
    * @return The list of validation failures. If empty, json object is valid !
    * @throws IOException if json string representations cannot be parsed
    */
   public static List<String> validateJson(String schemaText, String jsonText, String namespace) throws IOException {
      return validateJson(getJsonNodeForSchema(schemaText), JsonSchemaValidator.getJsonNode(jsonText), namespace);
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of OpenAPI schema
    * spec semantics regarding additional or unknown attributes: schema must explicitly set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param schemaNode The OpenAPI schema specification as a Jackson node
    * @param jsonNode   The Json object as a Jackson node
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode) {
      return validateJson(schemaNode, jsonNode, null);
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of OpenAPI schema
    * spec semantics regarding additional or unknown attributes: schema must explicitly set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param schemaNode The OpenAPI schema specification as a Jackson node
    * @param jsonNode   The Json object as a Jackson node
    * @param namespace  Namespace definition to resolve relative dependencies in Json schema
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode, String namespace) {
      schemaNode = convertOpenAPISchemaToJsonSchema(schemaNode);
      return JsonSchemaValidator.validateJson(schemaNode, jsonNode, namespace);
   }

   /**
    * Validate a Json object representing an OpenAPI message (response or request) against a node representing a full
    * OpenAPI specification (and not just a schema node). Specify the message by providing a valid JSON pointer for
    * <code>messagePathPointer</code> within specification and a <code>contentType</code> to allow finding the correct
    * schema information. Validation is a deep one: its pursue checking children nodes on a failed parent. Validation is
    * respectful of OpenAPI schema spec semantics regarding additional or unknown attributes: schema must explicitly set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param specificationNode  The OpenAPI full specification as a Jackson node
    * @param jsonNode           The Json object representing actual message as a Jackson node
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @param contentType        The Content-Type of the message to valid
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJsonMessage(JsonNode specificationNode, JsonNode jsonNode,
         String messagePathPointer, String contentType) {
      return validateJsonMessage(specificationNode, jsonNode, messagePathPointer, contentType, null);
   }

   /**
    * Validate a Json object representing an OpenAPI message (response or request) against a node representing a full
    * OpenAPI specification (and not just a schema node). Specify the message by providing a valid JSON pointer for
    * <code>messagePathPointer</code> within specification and a <code>contentType</code> to allow finding the correct
    * schema information. Validation is a deep one: its pursue checking children nodes on a failed parent. Validation is
    * respectful of OpenAPI schema spec semantics regarding additional or unknown attributes: schema must explicitly set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param specificationNode  The OpenAPI full specification as a Jackson node
    * @param jsonNode           The Json object representing actual message as a Jackson node
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @param contentType        The Content-Type of the message to valid
    * @param namespace          Namespace definition to resolve relative dependencies in OpenAPI schema
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJsonMessage(JsonNode specificationNode, JsonNode jsonNode,
         String messagePathPointer, String contentType, String namespace) {
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
      // Extract message corresponding to contentType.
      messageNode = getMessageContentNode(messageNode, contentType);
      if (messageNode == null || messageNode.isMissingNode()) {
         log.debug("content for {} cannot be found into OpenAPI specification", contentType);
         return List.of("messagePathPointer does not represent an existing JSON Pointer in OpenAPI specification");
      }

      // Build a schema object with responseNode schema as root and by importing
      // all the common parts that may be referenced by references.
      JsonNode schemaNode = messageNode.path("schema").deepCopy();
      if (schemaNode == null || schemaNode.isMissingNode()) {
         log.debug("schema for {} cannot be found into OpenAPI specification", messageNode);
         return List.of("schemaPathPointer does not represent an existing JSON Pointer in OpenAPI specification");
      }
      ((ObjectNode) schemaNode).set(JSON_SCHEMA_COMPONENTS_ELEMENT,
            specificationNode.path(JSON_SCHEMA_COMPONENTS_ELEMENT).deepCopy());

      return validateJson(schemaNode, jsonNode, namespace);
   }

   /**
    * Get a Jackson JsonNode representation for Json object.
    * @param jsonText The Json object as a string
    * @return The Jackson JsonNode corresponding to json object string
    * @throws IOException if json string representation cannot be parsed
    */
   public static JsonNode getJsonNode(String jsonText) throws IOException {
      return JsonSchemaValidator.getJsonNode(jsonText);
   }

   /**
    * Get a Jackson JsonNode representation for OpenAPI schema text. This handles the fact that OpenAPI spec may be
    * formatted in YAML. In that case, it handles the conversion.
    * @param schemaText The JSON or YAML string for OpenAPI schema
    * @return The Jackson JsonNode corresponding to OpenAPI schema string
    * @throws IOException if schema string representation cannot be parsed
    */
   public static JsonNode getJsonNodeForSchema(String schemaText) throws IOException {
      boolean isYaml = true;

      // Analyse first lines of content to guess content format.
      String line = null;
      int lineNumber = 0;
      BufferedReader reader = new BufferedReader(new StringReader(schemaText));
      while ((line = reader.readLine()) != null) {
         line = line.trim();
         // Check is we start with json object or array definition.
         if (lineNumber == 0 && (line.startsWith("{") || line.startsWith("["))) {
            isYaml = false;
            break;
         }
         if (line.startsWith("---") || line.startsWith("-") || line.startsWith("openapi: ")) {
            break;
         }
         lineNumber++;
      }
      reader.close();

      // Convert them to Node using Jackson object mapper.
      ObjectMapper mapper = null;
      if (isYaml) {
         log.debug("Guessing OpenAPI spec format is YAML");
         LoaderOptions options = new LoaderOptions();
         // If schema is too big, increase the code point limit to avoid exception.
         // Default is 3MB hard coded in Snake Yaml, we set it to bytes length + 256.
         if (schemaText.getBytes(StandardCharsets.UTF_8).length > 3 * 1024 * 1024) {
            log.warn("OpenAPI schema is too big, increasing code point limit to 9MB");
            options.setCodePointLimit(schemaText.getBytes(StandardCharsets.UTF_8).length + 256);
         }
         mapper = new ObjectMapper(YAMLFactory.builder().loaderOptions(options).build());
      } else {
         log.debug("Guessing OpenAPI spec format is JSON");
         mapper = new ObjectMapper();
      }
      return mapper.readTree(schemaText);
   }

   protected static JsonNode getMessageContentNode(JsonNode responseCodeNode, String contentType) {
      JsonNode contentNode = responseCodeNode.at("/content/" + contentType.replace("/", "~1"));
      if (contentNode == null || contentNode.isMissingNode()) {
         // If no exact matching, try loose matching browsing the content types.
         // We may have 'application/json; charset=utf-8' on one side and 'application/json;charset=UTF-8' on the other.
         Iterator<Map.Entry<String, JsonNode>> contents = responseCodeNode.path("content").fields();
         while (contents.hasNext()) {
            Map.Entry<String, JsonNode> contentTypeNode = contents.next();
            if (contentTypeNode.getKey().replace(" ", "").equalsIgnoreCase(contentType.replace(" ", ""))) {
               return contentTypeNode.getValue();
            }
         }

         // If no match here, it's maybe contentType contains charset but not the spec.
         // Remove charset information and try again.
         if (contentType.contains("charset=") && contentType.indexOf(";") > 0) {
            return getMessageContentNode(responseCodeNode, contentType.substring(0, contentType.indexOf(";")));
         }
      }
      return contentNode;
   }

   /** Entry point method for converting an OpenAPI schema node to Json schema. */
   private static JsonNode convertOpenAPISchemaToJsonSchema(JsonNode jsonNode) {
      // Convert components.
      if (jsonNode.has(JSON_SCHEMA_COMPONENTS_ELEMENT)) {
         convertProperties(jsonNode.path(JSON_SCHEMA_COMPONENTS_ELEMENT).path("schemas").elements());
      }
      // Convert schema for all structures.
      for (String structure : STRUCTURES) {
         if (jsonNode.has(structure) && jsonNode.path(structure).isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode.path(structure);
            for (int i = 0; i < arrayNode.size(); i++) {
               JsonNode structureNode = arrayNode.get(i);
               structureNode = convertOpenAPISchemaToJsonSchema(structureNode);
            }
         }
      }

      // Convert properties and types.
      if (jsonNode.has("type") && jsonNode.path("type").asText().equals("object")) {
         convertProperties(jsonNode.path(JSON_SCHEMA_PROPERTIES_ELEMENT).elements());
      } else {
         convertType(jsonNode);
      }

      // Remove all unsupported attributes.
      for (String notSupported : NOT_SUPPORTED_ATTRIBUTES) {
         if (jsonNode.has(notSupported)) {
            ((ObjectNode) jsonNode).remove(notSupported);
         }
      }

      return jsonNode;
   }

   /** Deal with converting properties of a Json node object. */
   private static void convertProperties(Iterator<JsonNode> properties) {
      while (properties.hasNext()) {
         JsonNode property = properties.next();
         property = convertOpenAPISchemaToJsonSchema(property);
      }
   }

   private static Optional<String> getCompositionStructureType(JsonNode node) {
      for (var current : COMPOSITION_STRUCTURES) {
         if (node.has(current)) {
            return Optional.of(current);
         }
      }
      return Optional.empty();
   }

   private static boolean isOneOfNullable(ArrayNode oneOf) {
      for (Iterator<JsonNode> it = oneOf.iterator(); it.hasNext();) {
         JsonNode current = it.next();
         if (current.isObject() && ((ObjectNode) current).has("type")
               && ((ObjectNode) current).get("type").asText().equals("null")) {
            return true;
         }
      }
      return false;
   }

   /** Deal with converting type of a Json node object. */
   private static void convertType(JsonNode node) {
      if (node.has("type") && !node.path("type").asText().equals("object")) {

         // Convert nullable in additional type and remove node.
         if (node.path(NULLABLE).asBoolean()) {
            String type = node.path("type").asText();
            ArrayNode typeArray = ((ObjectNode) node).putArray("type");
            typeArray.add(type).add("null");
         }
      }

      // Handle OneOf, AnyOf & AllOf.
      if (node.path(NULLABLE).asBoolean()) {
         Optional<String> maybeStructure = getCompositionStructureType(node);
         if (maybeStructure.isPresent()) {
            String structure = maybeStructure.get();
            if (structure.equals(ONE_OF)) {
               // Append null type to oneOf if it's not already there.
               var oneOf = ((ArrayNode) node.path(ONE_OF));
               if (!isOneOfNullable(oneOf)) {
                  ObjectNode nullNode = new ObjectMapper().createObjectNode();
                  nullNode.put("type", "null");
                  ((ArrayNode) node.path(ONE_OF)).add(nullNode);
               }
            } else {
               // Nesting current structure inside a OneOf.
               ObjectNode allOfChildObject = new ObjectMapper().createObjectNode();
               allOfChildObject.set(structure, node.path(structure));
               ((ObjectNode) node).remove(structure);

               ((ObjectNode) node).putArray(ONE_OF);
               ((ArrayNode) node.path(ONE_OF)).add(allOfChildObject);

               //Adding null type to oneOf structure
               ObjectNode nullNode = new ObjectMapper().createObjectNode();
               nullNode.put("type", "null");
               ((ArrayNode) node.path(ONE_OF)).add(nullNode);
            }
         }
      }

   }
}
