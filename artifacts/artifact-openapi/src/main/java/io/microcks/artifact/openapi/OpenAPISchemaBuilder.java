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
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.microcks.util.JsonSchemaValidator;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * Helper class for building/inferring a Json Schema from Json node object.
 * @author laurent
 */
public class OpenAPISchemaBuilder {

   public static final String JSON_SCHEMA_TYPE = "type";
   public static final String JSON_SCHEMA_ITEMS = "items";
   public static final String JSON_SCHEMA_OBJECT_TYPE = "object";
   public static final String JSON_SCHEMA_ARRAY_TYPE = "array";
   public static final String JSON_SCHEMA_PROPERTIES = "properties";


   private OpenAPISchemaBuilder() {
      // Hide the implicit default constructor.
   }

   /**
    * Build the Json schema for the type corresponding to given Json node provided as string.
    * @param jsonText The String representing Json node to introspect for building a Json schema
    * @return A JsonNode representing the Json Schema fragment for this type
    * @throws IOException Tf given Json string cannot be parsed as Json
    */
   public static JsonNode buildTypeSchemaFromJson(String jsonText) throws IOException {
      return buildTypeSchemaFromJson(JsonSchemaValidator.getJsonNode(jsonText));
   }

   /**
    * Build the Json schema for the type corresponding to given Json node.
    * @param node The Jackson Json node to introspect for building a Json schema
    * @return A JsonNode representing the Json Schema fragment for this type
    */
   public static JsonNode buildTypeSchemaFromJson(JsonNode node) {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode schemaNode = mapper.createObjectNode();

      traverseNode(node, schemaNode, mapper);
      return schemaNode;
   }

   /** Travers a node and complete its schema from schema parent node. */
   private static void traverseNode(JsonNode currentNode, ObjectNode parentNodeSchemaNode, ObjectMapper mapper) {
      switch (currentNode.getNodeType()) {
         case OBJECT:
            parentNodeSchemaNode.put(JSON_SCHEMA_TYPE, JSON_SCHEMA_OBJECT_TYPE);
            ObjectNode propertiesNodeSchemaNode = parentNodeSchemaNode.putObject(JSON_SCHEMA_PROPERTIES);

            Iterator<Map.Entry<String, JsonNode>> fieldsNodes = currentNode.fields();
            while (fieldsNodes.hasNext()) {
               Map.Entry<String, JsonNode> fieldsNode = fieldsNodes.next();
               ObjectNode fieldNodeSchemaNode = propertiesNodeSchemaNode.putObject(fieldsNode.getKey());
               traverseNode(fieldsNode.getValue(), fieldNodeSchemaNode, mapper);
            }
            break;
         case ARRAY:
            parentNodeSchemaNode.put(JSON_SCHEMA_TYPE, JSON_SCHEMA_ARRAY_TYPE);
            ObjectNode itemsNodeSchemaNode = parentNodeSchemaNode.putObject(JSON_SCHEMA_ITEMS);

            JsonNode firstChild = ((ArrayNode) currentNode).elements().next();
            traverseNode(firstChild, itemsNodeSchemaNode, mapper);
            break;
         default:
            parentNodeSchemaNode.put(JSON_SCHEMA_TYPE, getJsonSchemaScalarType(currentNode.getNodeType()));
      }
   }

   /** Return the actual Json schema type for given scalar type from Jackson internals. */
   private static String getJsonSchemaScalarType(JsonNodeType nodeType) {
      switch (nodeType) {
         case STRING:
            return "string";
         case BOOLEAN:
            return "boolean";
         case NUMBER:
            return "number";
         default:
            return nodeType.toString().toLowerCase();
      }
   }
}
