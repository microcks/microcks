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
import io.github.microcks.util.SchemaMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class for extracting information from AsyncAPI schema. Supported version of AsyncAPI schema are
 * https://www.asyncapi.com/docs/reference/specification/v3.0.0 and
 * https://www.asyncapi.com/docs/reference/specification/v2.x.
 * @author laurent
 */
public class AsyncAPISchemaUtil {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AsyncAPISchemaUtil.class);

   public static final String ASYNC_SCHEMA_PAYLOAD_ELEMENT = "payload";
   private static final String ONE_OF_STRUCT = "oneOf";


   /** Private constructor to hide the implicit one. */
   private AsyncAPISchemaUtil() {
   }

   /** Define the JSON pointer expression to access the operation messages. */
   public static String findMessagePathPointer(JsonNode specificationNode, String operationName) {
      String messagePathPointer = "";
      String[] operationElements = operationName.split(" ");

      String asyncApi = specificationNode.path("asyncapi").asText("2");
      if (asyncApi.startsWith("3")) {
         // Assume we have an AsyncAPI v3 document.
         String operationNamePtr = "/operations/" + operationElements[1].replace("/", "~1");
         messagePathPointer = operationNamePtr + "/messages";
      } else {
         // Assume we have an AsyncAPI v2 document.
         String operationNamePtr = "/channels/" + operationElements[1].replace("/", "~1");
         if ("SUBSCRIBE".equals(operationElements[0])) {
            operationNamePtr += "/subscribe";
         } else {
            operationNamePtr += "/publish";
         }
         messagePathPointer = operationNamePtr + "/message";
      }
      return messagePathPointer;
   }

   /**
    * Retrieve the Avro schema corresponding to a message using its JSON pointer in Spec. Complete the {@code schemaMap}
    * if provided. Raise a simple exception with message if problem while navigating the spec.
    */
   public static Schema retrieveMessageAvroSchema(JsonNode specificationNode, String messagePathPointer,
         SchemaMap schemaMap) throws Exception {
      // Extract Json node for message pointer.
      JsonNode messageNode = specificationNode.at(messagePathPointer);
      if (messageNode == null || messageNode.isMissingNode()) {
         log.debug("messagePathPointer {} is not a valid JSON Pointer", messagePathPointer);
         throw new Exception("messagePathPointer does not represent a valid JSON Pointer in AsyncAPI specification");
      }
      // Message node can be just a reference.
      messageNode = followRefIfAny(messageNode, specificationNode);

      if (messageNode.isArray()) {
         // In the case of AsyncAPI v3, we always got messages even if there's just one element.
         // Wrapping them in oneOf, make us lose details on validation errors so just do it if necessary.
         ArrayNode messagesNode = (ArrayNode) messageNode;
         if (messagesNode.size() > 1) {
            return buildOneOfMessagesAvroSchemas(specificationNode, (ArrayNode) messageNode, schemaMap);
         }
         return buildSingleMessageAvroSchema(followRefIfAny(messagesNode.get(0), specificationNode), schemaMap);
      } else if (messageNode.has(ONE_OF_STRUCT)) {
         ArrayNode oneOfMessageNode = (ArrayNode) messageNode.get(ONE_OF_STRUCT);
         return buildOneOfMessagesAvroSchemas(specificationNode, oneOfMessageNode, schemaMap);
      } else {
         return buildSingleMessageAvroSchema(messageNode, schemaMap);
      }
   }

   /** Build an array of Avro schemas for messages expressed as a direct oneOf structure. */
   private static Schema buildOneOfMessagesAvroSchemas(JsonNode specificationNode, ArrayNode oneOfMessageNode,
         SchemaMap schemaMap) throws Exception {
      // Initialize a oneOf schema with array.
      Schema[] schemas = new Schema[oneOfMessageNode.size()];

      // Extract the schema payload for each alternative of the message.
      for (int i = 0; i < oneOfMessageNode.size(); i++) {
         JsonNode altMessageNode = oneOfMessageNode.get(i);
         // Extract a schema for each messages
         altMessageNode = followRefIfAny(altMessageNode, specificationNode);
         schemas[i] = buildSingleMessageAvroSchema(altMessageNode, schemaMap);
      }

      return Schema.createUnion(schemas);
   }

   /** Build an Avro schema for spring message definition. */
   private static Schema buildSingleMessageAvroSchema(JsonNode messageNode, SchemaMap schemaMap) throws Exception {
      // Check that message node has a payload attribute.
      if (!messageNode.has(ASYNC_SCHEMA_PAYLOAD_ELEMENT)) {
         log.debug("messageNode {} has no 'payload' attribute", messageNode);
         throw new Exception("message definition has no valid payload in AsyncAPI specification");
      }

      // Navigate to payload definition.
      messageNode = messageNode.path(ASYNC_SCHEMA_PAYLOAD_ELEMENT);

      // Payload node can be just a reference to another schema... But in the case of Avro, this is an external schema
      // as #/components/schemas can only hold JSON schemas. So we have to use a registry for resolving and accessing
      // this Avro schema. We'll have to build an Avro Schema either from payload content or registry content.
      String schemaContent = null;

      if (messageNode.has("$ref")) {
         // Remove trailing anchor marker if any.
         // './user-signedup.avsc#/User' => './user-signedup.avsc'
         String ref = messageNode.path("$ref").asText();
         log.debug("Looking for an external Avro schema in registry: {}", ref);
         if (ref.contains("#")) {
            ref = ref.substring(0, ref.indexOf("#"));
         }
         if (schemaMap != null) {
            schemaContent = schemaMap.getSchemaEntry(ref);
         }
         if (schemaContent == null) {
            log.info("No schema content found in SchemaMap. {} is not found", ref);
            throw new Exception("no schema content found for " + ref + " in used SchemaMap.");
         }
      } else {
         // Schema is specified within the payload definition.
         schemaContent = messageNode.toString();
      }

      // Now build and return the schema.
      return AvroUtil.getSchema(schemaContent);
   }

   /** Check if a node has a reference and follow it to target node in the document. */
   public static JsonNode followRefIfAny(JsonNode referencableNode, JsonNode documentRoot) {
      if (referencableNode.has("$ref")) {
         String ref = referencableNode.path("$ref").asText();
         return followRefIfAny(documentRoot.at(ref.substring(1)), documentRoot);
      }
      return referencableNode;
   }
}
