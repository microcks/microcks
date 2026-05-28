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

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.networknt.schema.JsonMetaSchema;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.SpecVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Helper class for validating Json objects against their Json schema. Supported version of Json schema is
 * http://json-schema.org/draft/2020-12/schema.
 * @author laurent
 */
public class JsonSchemaValidator {

   /** A commons logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(JsonSchemaValidator.class);

   public static final String JSON_V12_SCHEMA_IDENTIFIER = "http://json-schema.org/draft/2020-12/schema#";
   public static final String JSON_SCHEMA_IDENTIFIER_ELEMENT = "$schema";

   public static final String JSON_SCHEMA_COMPONENTS_ELEMENT = "components";
   public static final String JSON_SCHEMA_TYPE_ELEMENT = "type";
   public static final String JSON_SCHEMA_PROPERTIES_ELEMENT = "properties";
   public static final String JSON_SCHEMA_REQUIRED_ELEMENT = "required";
   public static final String JSON_SCHEMA_ITEMS_ELEMENT = "items";
   public static final String JSON_SCHEMA_ADD_PROPERTIES_ELEMENT = "additionalProperties";

   public static final String JSON_SCHEMA_OBJECT_TYPE = "object";
   public static final String JSON_SCHEMA_ARRAY_TYPE = "array";

   private static final ObjectMapper mapper = new ObjectMapper()
         .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
         .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN).enable(SerializationFeature.INDENT_OUTPUT);

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
      List<String> errors = validateJson(schemaText, jsonText, namespace);
      if (!errors.isEmpty()) {
         log.debug("Get validation errors, returning false");
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
    * @return The list of validation failure messages. If empty, json object is valid !
    * @throws IOException if json string representations cannot be parsed
    */
   public static List<String> validateJson(String schemaText, String jsonText) throws IOException {
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
    * @return The list of validation failure messages. If empty, json object is valid !
    * @throws IOException if json string representations cannot be parsed
    */
   public static List<String> validateJson(String schemaText, String jsonText, String namespace) throws IOException {
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
    * @return The list of validation failures messages. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode) {
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
    * @return The list of validation failure messages. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode, String namespace) {
      return validateJson(schemaNode, jsonNode, namespace, JsonSchemaDialect.DRAFT_2020_12);
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
    * @param dialect    The JSON Schema dialect to validate against (DRAFT_4 for OAS 3.0, DRAFT_2020_12 for OAS 3.1)
    * @return The list of validation failure messages. If empty, json object is valid !
    */
   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode, String namespace,
         JsonSchemaDialect dialect) {
      List<String> errors = new ArrayList<>();

      final JsonSchema jsonSchemaNode = extractJsonSchemaNode(schemaNode, namespace, toVersionFlag(dialect));

      Set<ValidationMessage> messages = jsonSchemaNode.validate(jsonNode, executionContext -> {
         executionContext.getExecutionConfig().setFormatAssertionsEnabled(true);
         executionContext.getExecutionConfig().setLocale(Locale.US);
      });

      if (!messages.isEmpty()) {
         for (ValidationMessage message : messages) {
            errors.add(message.getError());
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
      return mapper.readTree(jsonText);
   }

   private static JsonSchema extractJsonSchemaNode(JsonNode jsonNode, String namespace,
         SpecVersion.VersionFlag versionFlag) {
      JsonMetaSchema jsonMetaSchema = JsonMetaSchema.builder(metaSchemaFor(versionFlag)).build();
      JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(versionFlag,
            builder -> builder.metaSchema(jsonMetaSchema));

      if (namespace != null) {
         URI baseUri = URI.create(namespace);
         return jsonSchemaFactory.getSchema(baseUri, jsonNode);
      }

      return jsonSchemaFactory.getSchema(jsonNode);
   }

   private static JsonMetaSchema metaSchemaFor(SpecVersion.VersionFlag versionFlag) {
      return switch (versionFlag) {
         case V4 -> JsonMetaSchema.getV4();
         case V6 -> JsonMetaSchema.getV6();
         case V7 -> JsonMetaSchema.getV7();
         case V201909 -> JsonMetaSchema.getV201909();
         case V202012 -> JsonMetaSchema.getV202012();
      };
   }

   private static SpecVersion.VersionFlag toVersionFlag(JsonSchemaDialect dialect) {
      return switch (dialect) {
         case DRAFT_4 -> SpecVersion.VersionFlag.V4;
         case DRAFT_2020_12 -> SpecVersion.VersionFlag.V202012;
      };
   }
}
