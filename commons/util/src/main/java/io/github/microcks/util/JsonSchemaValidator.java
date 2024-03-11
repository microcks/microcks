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
package io.github.microcks.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.load.configuration.LoadingConfiguration;
import com.github.fge.jsonschema.core.load.uri.URITranslatorConfiguration;
import com.github.fge.jsonschema.core.report.ProcessingMessage;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for validating Json objects against their Json schema. Supported version of Json schema is
 * http://json-schema.org/draft-07/schema.
 * @author laurent
 */
public class JsonSchemaValidator {

   /** A commons logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

   public static final String JSON_V4_SCHEMA_IDENTIFIER = "http://json-schema.org/draft-04/schema#";
   public static final String JSON_V7_SCHEMA_IDENTIFIER = "http://json-schema.org/draft-07/schema#";
   public static final String JSON_V12_SCHEMA_IDENTIFIER = "http://json-schema.org/draft/2020-12/schema#";
   public static final String JSON_SCHEMA_IDENTIFIER_ELEMENT = "$schema";

   public static final String JSON_SCHEMA_COMPONENTS_ELEMENT = "components";
   public static final String JSON_SCHEMA_PROPERTIES_ELEMENT = "properties";

   private JsonSchemaValidator() {
      // Private constructor to hide the implicit public one.
   }

   /**
    * Check if a Json object is valid against the given Json schema specification.
    * @param schemaText The Json schema specification as a string
    * @param jsonText   The Json object as a string
    * @return True if Json object is valid, false otherwise
    * @throws IOException if json string representations cannot be parsed
    */
   public static boolean isJsonValid(String schemaText, String jsonText) throws IOException {
      return isJsonValid(schemaText, jsonText, null);
   }

   /**
    * Check if a Json object is valid against the given Json schema specification.
    * @param schemaText The Json schema specification as a string
    * @param jsonText   The Json object as a string
    * @param namespace  Namespace definition to resolve relative dependencies in Json schema
    * @return True if Json object is valid, false otherwise
    * @throws IOException if json string representations cannot be parsed
    */
   public static boolean isJsonValid(String schemaText, String jsonText, String namespace) throws IOException {
      try {
         List<String> errors = validateJson(schemaText, jsonText, namespace);
         if (!errors.isEmpty()) {
            log.debug("Get validation errors, returning false");
            return false;
         }
      } catch (ProcessingException pe) {
         log.debug("Got processing exception while extracting schema, returning false");
         return false;
      }
      return true;
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of Json schema spec
    * semantics regarding additional or unknown attributes: schema must explicitly set <code>additionalProperties</code>
    * to false if you want to consider unknown attributes as validation errors. It returns a list of validation error
    * messages.
    * @param schemaText The Json schema specification as a string
    * @param jsonText   The Json object as a string
    * @return The list of validation failures. If empty, json object is valid !
    * @throws IOException         if json string representations cannot be parsed
    * @throws ProcessingException if json node does not represent valid Schema
    */
   public static List<String> validateJson(String schemaText, String jsonText) throws IOException, ProcessingException {
      return validateJson(getJsonNode(schemaText), getJsonNode(jsonText), null);
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of Json schema spec
    * semantics regarding additional or unknown attributes: schema must explicitly set <code>additionalProperties</code>
    * to false if you want to consider unknown attributes as validation errors. It returns a list of validation error
    * messages.
    * @param schemaText The Json schema specification as a string
    * @param jsonText   The Json object as a string
    * @param namespace  Namespace definition to resolve relative dependencies in Json schema
    * @return The list of validation failures. If empty, json object is valid !
    * @throws IOException         if json string representations cannot be parsed
    * @throws ProcessingException if json node does not represent valid Schema
    */
   public static List<String> validateJson(String schemaText, String jsonText, String namespace)
         throws IOException, ProcessingException {
      return validateJson(getJsonNode(schemaText), getJsonNode(jsonText), namespace);
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of Json schema spec
    * semantics regarding additional or unknown attributes: schema must explicitly set <code>additionalProperties</code>
    * to false if you want to consider unknown attributes as validation errors. It returns a list of validation error
    * messages.
    * @param schemaNode The Json schema specification as a Jackson node
    * @param jsonNode   The Json object as a Jackson node
    * @return The list of validation failures. If empty, json object is valid !
    * @throws ProcessingException if json node does not represent valid Schema
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode) throws ProcessingException {
      return validateJson(schemaNode, jsonNode, null);
   }

   /**
    * Validate a Json object representing by its text against a schema object representing byt its text too. Validation
    * is a deep one: its pursue checking children nodes on a failed parent. Validation is respectful of Json schema spec
    * semantics regarding additional or unknown attributes: schema must explicitly set <code>additionalProperties</code>
    * to false if you want to consider unknown attributes as validation errors. It returns a list of validation error
    * messages.
    * @param schemaNode The Json schema specification as a Jackson node
    * @param jsonNode   The Json object as a Jackson node
    * @param namespace  Namespace definition to resolve relative dependencies in Json schema
    * @return The list of validation failures. If empty, json object is valid !
    * @throws ProcessingException if json node does not represent valid Schema
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode, String namespace)
         throws ProcessingException {
      List<String> errors = new ArrayList<>();

      final JsonSchema jsonSchemaNode = extractJsonSchemaNode(schemaNode, namespace);

      // Ask for a deep check to get a full error report.
      ProcessingReport report = jsonSchemaNode.validate(jsonNode, true);
      if (!report.isSuccess()) {
         for (ProcessingMessage processingMessage : report) {
            errors.add(processingMessage.getMessage());
         }
      }

      return errors;
   }

   /**
    * Get a Jackson JsonNode representation for Json object.
    * @param jsonText The Json object as a string
    * @return The Jackson JsonNode corresponding to json object string
    * @throws IOException if json string representation cannot be parsed
    */
   public static JsonNode getJsonNode(String jsonText) throws IOException {
      return JsonLoader.fromString(jsonText);
   }

   /**
    * Get a Jackson JsonNode representation for Json schema.
    * @param schemaText The Json schema specification as a string
    * @return The Jackson JsonSchema corresponding to json schema string
    * @throws IOException         if json string representation cannot be parsed
    * @throws ProcessingException if json node does not represent valid Schema
    */
   public static JsonSchema getSchemaNode(String schemaText) throws IOException, ProcessingException {
      final JsonNode schemaNode = getJsonNode(schemaText);
      return extractJsonSchemaNode(schemaNode, null);
   }

   /**
    * Extract a Json SchemaNode from Jackson representation. Dependencies can be loaded using a namespace definition.
    * See
    * https://github.com/java-json-tools/json-schema-validator/blob/master/src/main/java/com/github/fge/jsonschema/examples/Example5.java
    * for example on how to use namespaces. Just provide null if no namespace.
    */
   private static JsonSchema extractJsonSchemaNode(JsonNode jsonNode, String namespace) throws ProcessingException {
      final JsonNode schemaIdentifier = jsonNode.get(JSON_SCHEMA_IDENTIFIER_ELEMENT);
      if (schemaIdentifier == null) {
         ((ObjectNode) jsonNode).put(JSON_SCHEMA_IDENTIFIER_ELEMENT, JSON_V7_SCHEMA_IDENTIFIER);
      }

      final JsonSchemaFactory factory;
      if (namespace != null) {
         log.debug("Setting namespace to {} in Json schema loading configuration", namespace);
         // Set up a loading configuration for provided namespace.
         final LoadingConfiguration cfg = LoadingConfiguration.newBuilder()
               .setURITranslatorConfiguration(URITranslatorConfiguration.newBuilder().setNamespace(namespace).freeze())
               .freeze();
         factory = JsonSchemaFactory.newBuilder().setLoadingConfiguration(cfg).freeze();
      } else {
         factory = JsonSchemaFactory.byDefault();
      }

      return factory.getJsonSchema(jsonNode);
   }
}
