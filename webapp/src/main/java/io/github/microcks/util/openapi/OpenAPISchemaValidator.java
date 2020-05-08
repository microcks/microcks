/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import io.github.microcks.util.JsonSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper class for validating Json objects against their OpenAPI schema. Supported version
 * of OpenAPI schema is https://github.com/OAI/OpenAPI-Specification/blob/master/versions/3.0.1.md.
 * @author laurent
 */
public class OpenAPISchemaValidator {

   /** A commons logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(OpenAPISchemaValidator.class);

   private static final String[] STRUCTURES = {
         "allOf", "anyOf", "oneOf", "not", "items", "additionalProperties"
   };
   private static final String[] NOT_SUPPORTED_ATTRIBUTES = {
         "nullable", "discriminator", "readOnly", "writeOnly",
         "xml", "externalDocs", "example", "deprecated"
   };

   /**
    * Check if a Json object is valid againts the given OpenAPI schema specification.
    * @param schemaText The OpenAPI schema specification as a string
    * @param jsonText The Json object as a string
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
    * Validate a Json object representing by its text against a schema object representing byt its
    * text too. Validation is a deep one: its pursue checking children nodes on a failed parent. Validation
    * is respectful of OpenAPI schema spec semantics regarding additional or unknown attributes: schema must
    * explicitely set <code>additionalProperties</code> to false if you want to consider unknown attributes
    * as validation errors. It returns a list of validation error messages.
    * @param schemaText The OpenAPI schema specification as a string
    * @param jsonText The Json object as a string
    * @return The list of validation failures. If empty, json object is valid !
    * @throws IOException if json string representations cannot be parsed
    */
   public static List<String> validateJson(String schemaText, String jsonText) throws IOException {
      return validateJson(getJsonNodeForSchema(schemaText), JsonSchemaValidator.getJsonNode(jsonText));
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its
    * text too. Validation is a deep one: its pursue checking children nodes on a failed parent. Validation
    * is respectful of OpenAPI schema spec semantics regarding additional or unknown attributes: schema must
    * explicitely set <code>additionalProperties</code> to false if you want to consider unknown attributes
    * as validation errors. It returns a list of validation error messages.
    * @param schemaNode The OpenAPI schema specification as a Jackson node
    * @param jsonNode The Json object as a Jackson node
    * @return The list of validation failures. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode) {
      schemaNode = convertOpenAPISchemaToJsonSchema(schemaNode);

      try {
         return JsonSchemaValidator.validateJson(schemaNode, jsonNode);
      } catch (ProcessingException e) {
         log.debug("Got a ProcessingException while trying to interpret schemaNode as a real schema");
         List<String> errors = new ArrayList<>();
         errors.add("schemaNode does not seem to represent a valid OpenAPI schema");
         return errors;
      }
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
    * Get a Jackson JsonNode representation for OpenAPI schema text. This handles
    * the fact that OpenAPI spec may be formatted in YAML. In that case, it handles the
    * conversion.
    * @param schemaText The JSON or YAML string for OpenAPI schema
    * @return The Jackson JsonNode corresponding to OpenAPI schema string
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
         if (line.startsWith("---")) {
            isYaml = true;
            break;
         }
      }
      reader.close();

      // Convert them to Node using Jackson object mapper.
      ObjectMapper mapper = null;
      if (isYaml) {
         log.debug("Guessing OpenAPI spec format is YAML");
         mapper = new ObjectMapper(new YAMLFactory());
      } else {
         log.debug("Guessing OpenAPI spec format is JSON");
         mapper = new ObjectMapper();
      }
      return mapper.readTree(schemaText);
   }


   /** Entry point method for converting an OpenAPU schema node to Json schem */
   private static JsonNode convertOpenAPISchemaToJsonSchema(JsonNode jsonNode) {
      // Convert schema for all structures.
      for (String structure : STRUCTURES) {
         if (jsonNode.has(structure) && jsonNode.path(structure).isArray()) {
            jsonNode = convertOpenAPISchemaToJsonSchema(jsonNode);
         }
      }

      // Convert properties and types.
      if (jsonNode.has("type") && jsonNode.path("type").asText().equals("object")) {
         convertProperties(jsonNode.path("properties").elements());
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

   /** Deal with conerting type of a Json node object. */
   private static void convertType(JsonNode node) {
      if (node.has("type") && !node.path("type").asText().equals("object")) {

         // Convert date format to date-time.
         if (node.has("format") && node.path("format").asText().equals("date")
               && node.path("type").asText().equals("string")) {
            ((ObjectNode) node).put("format", "date-time");
         }

         // Convert nullable in additional type and remove node.
         if (node.path("nullable").asBoolean()) {
            String type = node.path("type").asText();
            ArrayNode typeArray = ((ObjectNode) node).putArray("type");
            typeArray.add(type).add("null");
         }
      }
   }
}
