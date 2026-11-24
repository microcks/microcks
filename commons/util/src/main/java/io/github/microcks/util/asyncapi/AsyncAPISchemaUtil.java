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
import io.github.microcks.util.protobuf.ProtobufUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.protobuf.Descriptors;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.avro.AvroRuntimeException;
import org.apache.avro.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
   public static final String ASYNC_SCHEMA_SCHEMA_FORMAT_ELEMENT = "schemaFormat";
   public static final String ASYNC_SCHEMA_SCHEMA_ELEMENT = "schema";

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
    * if provided. Raise an {@link AsyncAPISchemaException} with message if problem while navigating the spec.
    * @throws AsyncAPISchemaException if a problem occurs while navigating the spec
    */
   public static Schema retrieveMessageAvroSchema(JsonNode specificationNode, String messagePathPointer,
         SchemaMap schemaMap) throws AsyncAPISchemaException {
      // Extract Json node for message pointer.
      JsonNode messageNode = specificationNode.at(messagePathPointer);
      if (messageNode == null || messageNode.isMissingNode()) {
         log.debug("messagePathPointer {} is not a valid JSON Pointer", messagePathPointer);
         throw new AsyncAPISchemaException(
               "messagePathPointer does not represent a valid JSON Pointer in AsyncAPI specification");
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
         SchemaMap schemaMap) throws AsyncAPISchemaException {
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
   private static Schema buildSingleMessageAvroSchema(JsonNode messageNode, SchemaMap schemaMap)
         throws AsyncAPISchemaException {
      // Check that message node has a payload attribute.
      if (!messageNode.has(ASYNC_SCHEMA_PAYLOAD_ELEMENT)) {
         log.debug("messageNode {} has no 'payload' attribute", messageNode);
         throw new AsyncAPISchemaException("message definition has no valid payload in AsyncAPI specification");
      }

      // Navigate to payload definition.
      messageNode = messageNode.path(ASYNC_SCHEMA_PAYLOAD_ELEMENT);

      // AsyncAPI v3 payload may be expressed as a Multi Format Schema Object, holding the actual
      // schema (and its optional external reference) under a nested 'schema' property, alongside a
      // 'schemaFormat' property. In that case, we have to unwrap it to get the real Avro schema node.
      if (messageNode.has(ASYNC_SCHEMA_SCHEMA_FORMAT_ELEMENT) && messageNode.has(ASYNC_SCHEMA_SCHEMA_ELEMENT)) {
         messageNode = messageNode.path(ASYNC_SCHEMA_SCHEMA_ELEMENT);
      }

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
            throw new AsyncAPISchemaException("no schema content found for " + ref + " in used SchemaMap.");
         }
      } else {
         // Schema is specified within the payload definition.
         schemaContent = messageNode.toString();
      }

      // Now build and return the schema. getSchema throws unchecked Avro errors (e.g. SchemaParseException) that
      // must be wrapped so callers relying on AsyncAPISchemaException don't let them escape and kill the caller.
      try {
         return AvroUtil.getSchema(schemaContent);
      } catch (AvroRuntimeException e) {
         log.info("Avro schema content cannot be parsed: {}", e.getMessage());
         throw new AsyncAPISchemaException("Avro schema content cannot be parsed: " + e.getMessage(), e);
      }
   }

   /** Check if a node has a reference and follow it to target node in the document. */
   public static JsonNode followRefIfAny(JsonNode referencableNode, JsonNode documentRoot) {
      if (referencableNode.has("$ref")) {
         String ref = referencableNode.path("$ref").asText();
         return followRefIfAny(documentRoot.at(ref.substring(1)), documentRoot);
      }
      return referencableNode;
   }

   /**
    * Retrieve the Protobuf descriptor of the message type expected for an AsyncAPI message. Message payload must be a
    * reference to a Protobuf schema like {@code ./user-signedup.proto#microcks.UserSignedUp} where the optional
    * fragment designates the message type (first message of the schema is used if none). Compiled descriptors are
    * looked up into {@code schemaMap} using the reference path, without its fragment.
    * @param specificationNode  The AsyncAPI full specification as a Jackson node
    * @param messagePathPointer A JSON Pointer for accessing expected message definition within spec
    * @param schemaMap          A map of Base64 encoded Protobuf descriptors, indexed by their schema reference path
    * @return The Protobuf message descriptor
    * @throws AsyncAPISchemaException if descriptor cannot be found or built
    */
   public static Descriptors.Descriptor retrieveMessageProtobufDescriptor(JsonNode specificationNode,
         String messagePathPointer, SchemaMap schemaMap) throws AsyncAPISchemaException {
      JsonNode messageNode = specificationNode.at(messagePathPointer);
      if (messageNode == null || messageNode.isMissingNode()) {
         throw new AsyncAPISchemaException(
               "messagePathPointer does not represent a valid JSON Pointer in AsyncAPI specification");
      }
      messageNode = followRefIfAny(messageNode, specificationNode);

      // In the case of AsyncAPI v3, we always got messages even if there's just one element.
      if (messageNode.isArray()) {
         if (messageNode.size() != 1) {
            throw new AsyncAPISchemaException("Protobuf payloads are only supported for single message operations");
         }
         messageNode = followRefIfAny(messageNode.get(0), specificationNode);
      }

      // AsyncAPI v3 payload may be expressed as a Multi Format Schema Object.
      JsonNode payloadNode = messageNode.path(ASYNC_SCHEMA_PAYLOAD_ELEMENT);
      if (payloadNode.has(ASYNC_SCHEMA_SCHEMA_FORMAT_ELEMENT) && payloadNode.has(ASYNC_SCHEMA_SCHEMA_ELEMENT)) {
         payloadNode = payloadNode.path(ASYNC_SCHEMA_SCHEMA_ELEMENT);
      }
      if (!payloadNode.has("$ref")) {
         throw new AsyncAPISchemaException("Protobuf message payload must be a reference to a Protobuf schema");
      }

      // './user-signedup.proto#microcks.UserSignedUp' => './user-signedup.proto' + 'microcks.UserSignedUp'
      String ref = payloadNode.path("$ref").asText();
      String typeName = null;
      if (ref.contains("#")) {
         typeName = ref.substring(ref.indexOf('#') + 1);
         ref = ref.substring(0, ref.indexOf('#'));
      }
      String base64Descriptor = schemaMap != null ? schemaMap.getSchemaEntry(ref) : null;
      if (base64Descriptor == null && schemaMap != null) {
         // Relative references have been replaced by URL encoded resource names at import time.
         base64Descriptor = schemaMap.getSchemaEntry(URLDecoder.decode(ref, StandardCharsets.UTF_8));
      }
      if (base64Descriptor == null) {
         throw new AsyncAPISchemaException("no Protobuf descriptor found for " + ref + " in used SchemaMap.");
      }
      try {
         return ProtobufUtil.findMessageDescriptor(base64Descriptor, typeName);
      } catch (InvalidProtocolBufferException | Descriptors.DescriptorValidationException
            | IllegalArgumentException e) {
         throw new AsyncAPISchemaException("Protobuf descriptor cannot be used: " + e.getMessage(), e);
      }
   }
}
