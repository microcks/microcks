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
import com.networknt.schema.*;
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

   private static final ObjectMapper mapper = new ObjectMapper()
         .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
         .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN).enable(SerializationFeature.INDENT_OUTPUT);

   private JsonSchemaValidator() {

   }

   public static boolean isJsonValid(String schemaText, String jsonText) throws IOException {
      return isJsonValid(schemaText, jsonText, null);
   }

   public static boolean isJsonValid(String schemaText, String jsonText, String namespace) throws IOException {
      try {
         List<String> errors = validateJson(schemaText, jsonText, namespace);
         if (!errors.isEmpty()) {
            log.debug("Get validation errors, returning false");
            return false;
         }
      } catch (Exception pe) {
         log.debug("Got processing exception while extracting schema, returning false");
         return false;
      }
      return true;
   }

   public static List<String> validateJson(String schemaText, String jsonText) throws IOException {
      return validateJson(getJsonNode(schemaText), getJsonNode(jsonText), null);
   }

   public static List<String> validateJson(String schemaText, String jsonText, String namespace) throws IOException {
      return validateJson(getJsonNode(schemaText), getJsonNode(jsonText), namespace);
   }

   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode) {
      return validateJson(schemaNode, jsonNode, null);
   }

   public static List<String> validateJson(JsonNode schemaNode, JsonNode jsonNode, String namespace) {
      List<String> errors = new ArrayList<>();

      final JsonSchema jsonSchemaNode = extractJsonSchemaNode(schemaNode, namespace);

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

   public static JsonNode getJsonNode(String jsonText) throws IOException {
      return mapper.readTree(jsonText);
   }

   public static com.networknt.schema.JsonSchema getSchemaNode(String schemaText) throws IOException {
      final JsonNode schemaNode = getJsonNode(schemaText);
      return extractJsonSchemaNode(schemaNode, null);
   }

   private static com.networknt.schema.JsonSchema extractJsonSchemaNode(JsonNode jsonNode, String namespace) {
      JsonMetaSchema jsonMetaSchema = JsonMetaSchema.builder(JsonMetaSchema.getV202012()).build();
      com.networknt.schema.JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory
            .getInstance(SpecVersion.VersionFlag.V202012, builder -> {
               builder.metaSchema(jsonMetaSchema);
            });

      if (namespace != null) {
         URI baseUri = URI.create(namespace);
         return jsonSchemaFactory.getSchema(baseUri, jsonNode);
      }

      return jsonSchemaFactory.getSchema(jsonNode);
   }
}
