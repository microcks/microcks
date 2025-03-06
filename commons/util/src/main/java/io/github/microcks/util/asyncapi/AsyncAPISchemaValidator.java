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
package io.github.microcks.util.asyncapi;

import io.github.microcks.util.AvroUtil;
import io.github.microcks.util.JsonSchemaValidator;
import io.github.microcks.util.SchemaMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.avro.AvroTypeException;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import static io.github.microcks.util.JsonSchemaValidator.*;
import static io.github.microcks.util.asyncapi.AsyncAPISchemaUtil.*;

/**
 * Helper class for validating Json objects against their AsyncAPI schema. Supported version of AsyncAPI schema are
 * https://www.asyncapi.com/docs/reference/specification/v3.0.0 and
 * https://www.asyncapi.com/docs/reference/specification/v2.x.
 * @author laurent
 */
public class AsyncAPISchemaValidator {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AsyncAPISchemaValidator.class);

   public static final String ASYNC_SCHEMA_PAYLOAD_ELEMENT = "payload";

   private static final String ONE_OF_STRUCT = "oneOf";
   private static final String[] STRUCTURES = { "allOf", "anyOf", ONE_OF_STRUCT, "not", "items",
         "additionalProperties" };
   private static final String[] NOT_SUPPORTED_ATTRIBUTES = { "discriminator", "externalDocs", "deprecated" };


   /** Private constructor to hide the implicit one. */
   private AsyncAPISchemaValidator() {
   }

   /**
    * Check if a Json object is valid against the given AsyncAPI schema specification.
    * @param schemaText The AsyncAPI schema specification as a string
    * @param jsonText   The Json object as a string
    * @return True if Json object is valid, false otherwise
    * @throws IOException if string representations cannot be parsed
    */
   public static boolean isJsonValid(String schemaText, String jsonText) throws IOException {
      List<String> errors = validateJson(schemaText, jsonText);
      if (!errors.isEmpty()) {
         log.debug("Get validation errors, returning false");
         return false;
      }
      return true;
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of AsyncAPI schema
    * spec semantics regarding additional or unknown attributes: schema must explicitely set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param schemaText The AsyncAPI schema specification as a string
    * @param jsonText   The Json object as a string
    * @return The list of validation failures. If empty, json object is valid !
    * @throws IOException if json string representations cannot be parsed
    */
   public static List<String> validateJson(String schemaText, String jsonText) throws IOException {
      return validateJson(getJsonNodeForSchema(schemaText), JsonSchemaValidator.getJsonNode(jsonText));
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of AsyncAPI schema
    * spec semantics regarding additional or unknown attributes: schema must explicitely set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param schemaNode The AsyncAPI schema specification as a Jackson node
    * @param jsonNode   The Json object as a Jackson node
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode) {
      return validateJson(schemaNode, jsonNode, null);
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of AsyncAPI schema
    * spec semantics regarding additional or unknown attributes: schema must explicitely set
    * <code>additionalProperties</code> to false if you want to consider unknown attributes as validation errors. It
    * returns a list of validation error messages.
    * @param schemaNode The AsyncAPI schema specification as a Jackson node
    * @param jsonNode   The Json object as a Jackson node
    * @param namespace  Namespace definition to resolve relative dependencies in Json schema
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode, String namespace) {
      schemaNode = convertAsyncAPISchemaToJsonSchema(schemaNode);
      return JsonSchemaValidator.validateJson(schemaNode, jsonNode, namespace);
   }

   /**
    * Validate a Json object representing an AsyncAPI message against a node representing a full AsyncAPI specification
    * (and not just a schema node). Specify the message by providing a valid JSON pointer for {@code messagePathPointer}
    * to allow finding the correct schema information. Validation is a deep one: its pursue checking children nodes on a
    * failed parent. Validation is respectful of AsyncAPI schema spec semantics regarding additional or unknown
    * attributes: schema must explicitly set {@code additionalProperties} to false if you want to consider unknown
    * attributes as validation errors. It returns a list of validation error messages.
    * @param specificationNode  The AsyncAPI full specification as a Jackson node
    * @param jsonNode           The Json object representing actual message as a Jackson node
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJsonMessage(JsonNode specificationNode, JsonNode jsonNode,
         String messagePathPointer) {
      return validateJsonMessage(specificationNode, jsonNode, messagePathPointer, null);
   }

   /**
    * Validate a Json object representing an AsyncAPI message against a node representing a full AsyncAPI specification
    * (and not just a schema node). Specify the message by providing a valid JSON pointer for {@code messagePathPointer}
    * to allow finding the correct schema information. Validation is a deep one: its pursue checking children nodes on a
    * failed parent. Validation is respectful of AsyncAPI schema spec semantics regarding additional or unknown
    * attributes: schema must explicitly set {@code additionalProperties} to false if you want to consider unknown
    * attributes as validation errors. It returns a list of validation error messages.
    * @param specificationNode  The AsyncAPI full specification as a Jackson node
    * @param jsonNode           The Json object representing actual message as a Jackson node
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @param namespace          Namespace definition to resolve relative dependencies in OpenAPI schema
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJsonMessage(JsonNode specificationNode, JsonNode jsonNode,
         String messagePathPointer, String namespace) {
      // Extract specific content type node for message node.
      JsonNode messageNode = specificationNode.at(messagePathPointer);
      if (messageNode == null || messageNode.isMissingNode()) {
         log.debug("messagePathPointer {} is not a valid JSON Pointer", messagePathPointer);
         return List.of("messagePathPointer does not represent a valid JSON Pointer in AsyncAPI specification");
      }
      // Message node can be just a reference.
      messageNode = followRefIfAny(messageNode, specificationNode);

      // We need to build a node holding schema information.
      JsonNode schemaNode = null;
      try {
         if (messageNode.isArray()) {
            // In the case of AsyncAPI v3, we always got messages even if there's just one element.
            // Wrapping them in oneOf, make us lose details on validation errors so just do it if necessary.
            ArrayNode messagesNode = (ArrayNode) messageNode;
            if (messagesNode.size() > 1) {
               schemaNode = buildOneOfMessageSchemaNode(specificationNode, (ArrayNode) messageNode);
            } else {
               schemaNode = retrieveSingleMessageSchemaNode(specificationNode,
                     followRefIfAny(messagesNode.get(0), specificationNode));
            }
         } else if (messageNode.has(ONE_OF_STRUCT)) {
            ArrayNode oneOfMessageNode = (ArrayNode) messageNode.get(ONE_OF_STRUCT);
            schemaNode = buildOneOfMessageSchemaNode(specificationNode, oneOfMessageNode);
         } else {
            schemaNode = retrieveSingleMessageSchemaNode(specificationNode, messageNode);
         }
      } catch (Exception e) {
         // Just return exception message as validation message.
         return Collections.singletonList(e.getMessage());
      }
      // Import all the common parts that may be referenced by references.
      ((ObjectNode) schemaNode).set(JSON_SCHEMA_COMPONENTS_ELEMENT,
            specificationNode.path(JSON_SCHEMA_COMPONENTS_ELEMENT).deepCopy());

      return validateJson(schemaNode, jsonNode, namespace);
   }

   /**
    * Validate an Avro binary representing an AsyncAPI message against a node representing a full AsyncAPI specification
    * (and not just a schema node). Specify the message by providing a valid JSON pointer for {@code messagePathPointer}
    * within specification to allow finding the correct schema information. Validation with avro binary is a shallow
    * one: because we do not have the schema used for writing the bytes, we can only check the given bytes are fitting
    * into the read schema from AsyncAPI document. It returns a list of validation error messages.
    * @param specificationNode  The AsyncAPI full specification as a Jackson node
    * @param avroBinary         The avro binary representing actual message
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @param schemaMap          An optional local Schema registry snapshot for resolving Avro schemas
    * @return The list of validation failures. If empty, avro binary is valid !
    */
   public static List<String> validateAvroMessage(JsonNode specificationNode, byte[] avroBinary,
         String messagePathPointer, SchemaMap schemaMap) {
      // Retrieve the schema to validate binary against.
      Schema avroSchema;
      try {
         avroSchema = retrieveMessageAvroSchema(specificationNode, messagePathPointer, schemaMap);
      } catch (Exception e) {
         return List.of(e.getMessage());
      }

      List<String> errors = new ArrayList<>();
      try {
         // Validation is shallow: we cannot detect schema incompatibilities as we do not
         // have the schema used for writing. Just checking we can read with given schema.
         AvroUtil.avroToAvroRecord(avroBinary, avroSchema);
         // We're satisfying at least one schema. Exit here.
         return List.of();
      } catch (AvroTypeException ate) {
         errors.add("Avro schema cannot be used to read message: " + ate.getMessage());
      } catch (IOException ioe) {
         errors.add("IOException while trying to validate message: " + ioe.getMessage());
      }
      return errors;
   }

   /**
    * Validate an Avro binary representing an AsyncAPI message against a node representing a full AsyncAPI specification
    * (and not just a schema node). Specify the message by providing a valid JSON pointer for {@code messagePathPointer}
    * within specification to allow finding the correct schema information. Validation with avro binary is a deep one:
    * each element of the reading schema from AsyncAPI spec is checked in terms of type compatibility, name and
    * required/optional property. It returns a list of validation error messages.
    * @param specificationNode  The AsyncAPI full specification as a Jackson node
    * @param avroRecord         The avro record representing actual message
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @param schemaMap          An optional local Schema registry snapshot for resolving Avro schemas
    * @return The list of validation failures. If empty, avro record is valid !
    */
   public static List<String> validateAvroMessage(JsonNode specificationNode, GenericRecord avroRecord,
         String messagePathPointer, SchemaMap schemaMap) {
      // Retrieve the schema to validate record against.
      Schema avroSchema = null;
      try {
         avroSchema = retrieveMessageAvroSchema(specificationNode, messagePathPointer, schemaMap);
      } catch (Exception e) {
         return List.of(e.getMessage());
      }

      // Validation is a deep one. Each element should be checked.
      if (AvroUtil.validate(avroSchema, avroRecord)) {
         return List.of();
      }
      // Produce some insights on what's going wrong. We'll accumulate the errors on different schemas.
      return AvroUtil.getValidationErrors(avroSchema, avroRecord);
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
    * Get a Jackson JsonNode representation for AsyncAPI schema text. This handles the fact that AsyncAPI spec may be
    * formatted in YAML. In that case, it handles the conversion.
    * @param schemaText The JSON or YAML string for AsyncAPI schema
    * @return The Jackson JsonNode corresponding to AsyncAPI schema string
    * @throws IOException if schema string representation cannot be parsed
    */
   public static JsonNode getJsonNodeForSchema(String schemaText) throws IOException {
      boolean isYaml = true;

      // Analyse first lines of content to guess content format.
      String line = null;
      BufferedReader reader = new BufferedReader(new StringReader(schemaText));
      while ((line = reader.readLine()) != null) {
         line = line.trim();
         // Check is we start with json object or array definition.
         if (line.startsWith("{") || line.startsWith("[")) {
            isYaml = false;
            break;
         }
         if (line.startsWith("---") || line.startsWith("-") || line.startsWith("asyncapi: ")) {
            break;
         }
      }
      reader.close();

      // Convert them to Node using Jackson object mapper.
      ObjectMapper mapper = null;
      if (isYaml) {
         log.debug("Guessing AsyncAPI spec format is YAML");
         mapper = new ObjectMapper(new YAMLFactory());
      } else {
         log.debug("Guessing AsyncAPI spec format is JSON");
         mapper = new ObjectMapper();
      }
      return mapper.readTree(schemaText);
   }

   /** Entry point method for converting an AsyncAPI schema node to Json schema. */
   private static JsonNode convertAsyncAPISchemaToJsonSchema(JsonNode jsonNode) {
      // Convert components.
      if (jsonNode.has("components")) {
         convertProperties(jsonNode.path("components").path("schemas").elements());
      }
      // Convert schema for all structures.
      for (String structure : STRUCTURES) {
         if (jsonNode.has(structure) && jsonNode.path(structure).isArray()) {
            ArrayNode arrayNode = (ArrayNode) jsonNode.path(structure);
            for (int i = 0; i < arrayNode.size(); i++) {
               JsonNode structureNode = arrayNode.get(i);
               structureNode = convertAsyncAPISchemaToJsonSchema(structureNode);
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
         property = convertAsyncAPISchemaToJsonSchema(property);
      }
   }

   /** Deal with converting type of a Json node object. */
   private static void convertType(JsonNode node) {
      // Convert nullable in additional type and remove node.
      if (node.has("type") && !node.path("type").asText().equals("object") && node.path("nullable").asBoolean()) {
         String type = node.path("type").asText();
         ArrayNode typeArray = ((ObjectNode) node).putArray("type");
         typeArray.add(type).add("null");
      }
   }

   /** Build a Json schema node for messages expressed as a direct oneOf structure. */
   private static JsonNode buildOneOfMessageSchemaNode(JsonNode specificationNode, ArrayNode oneOfMessageNode)
         throws Exception {
      // Initialize a oneOf schema with array.
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode schemaNode = mapper.createObjectNode();
      ArrayNode oneOfNode = mapper.createArrayNode();
      schemaNode.set(ONE_OF_STRUCT, oneOfNode);

      // Extract the schema payload for each alternative of the message.
      for (int i = 0; i < oneOfMessageNode.size(); i++) {
         JsonNode altMessageNode = oneOfMessageNode.get(i);
         // Extract the alternative message node.
         altMessageNode = followRefIfAny(altMessageNode, specificationNode);
         oneOfNode.add(retrieveSingleMessageSchemaNode(specificationNode, altMessageNode));
      }
      // Common parts that may be referenced by references should be imported at a upper level/
      return schemaNode;
   }

   /** Retrieve the Json schema node corresponding to a single message definition. */
   private static JsonNode retrieveSingleMessageSchemaNode(JsonNode specificationNode, JsonNode messageNode)
         throws Exception {
      // Check that message node has a payload attribute.
      if (!messageNode.has(ASYNC_SCHEMA_PAYLOAD_ELEMENT)) {
         log.debug("messageNode {} has no 'payload' attribute", messageNode);
         throw new Exception("message definition has no valid payload in AsyncAPI specification");
      }
      // Navigate to payload definition.
      messageNode = messageNode.path(ASYNC_SCHEMA_PAYLOAD_ELEMENT);

      // Payload node can be just a reference to another schema...
      messageNode = followRefIfAny(messageNode, specificationNode);

      // Build a schema object with messageNode as root.
      // Common parts that may be referenced by references should be imported at a upper level
      return messageNode.deepCopy();
   }
}
