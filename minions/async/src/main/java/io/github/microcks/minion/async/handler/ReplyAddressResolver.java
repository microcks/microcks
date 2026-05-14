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
package io.github.microcks.minion.async.handler;

import io.github.microcks.util.el.EvaluableRequest;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

/**
 * Resolves AsyncAPI v3 runtime expressions for reply address locations.
 * <p>
 * Supports the following expression patterns:
 * <ul>
 * <li>{@code $message.header#/headerName} - extract value from request message headers</li>
 * <li>{@code $message.payload#/jsonPointer} - extract value from request message JSON payload</li>
 * </ul>
 * <p>
 * Current scope: payload-based resolution is intentionally JSON-scoped in this implementation. Binary encodings and
 * non-JSON serialization formats (for example Avro/Protobuf with or without schema registry integration) are out of
 * scope for this class and should be introduced via a dedicated serializer-aware extension.
 *
 * @author rootp1
 */
public class ReplyAddressResolver {

   private static final Logger logger = Logger.getLogger(ReplyAddressResolver.class);
   private static final ObjectMapper mapper = new ObjectMapper();

   private static final String MESSAGE_HEADER_PREFIX = "$message.header#";
   private static final String MESSAGE_PAYLOAD_PREFIX = "$message.payload#";

   private ReplyAddressResolver() {
   }

   /**
    * Resolve a runtime expression to a reply destination address. The expression is evaluated against the incoming
    * request message's headers and payload.
    * <p>
    * Note: payload expressions ({@code $message.payload#...}) currently require a JSON request body.
    *
    * @param addressLocation  The runtime expression (e.g. "$message.header#/replyTo" or "$message.payload#/replyTopic")
    * @param evaluableRequest The incoming request message with body and headers
    * @return the resolved destination address string
    * @throws IllegalArgumentException if the expression format is not supported or cannot be resolved
    */
   public static String resolve(String addressLocation, EvaluableRequest evaluableRequest) {
      if (addressLocation == null || addressLocation.isBlank()) {
         throw new IllegalArgumentException("addressLocation expression must not be null or blank");
      }

      if (addressLocation.startsWith(MESSAGE_HEADER_PREFIX)) {
         return resolveFromHeaders(addressLocation.substring(MESSAGE_HEADER_PREFIX.length()), evaluableRequest);
      }

      if (addressLocation.startsWith(MESSAGE_PAYLOAD_PREFIX)) {
         return resolveFromPayload(addressLocation.substring(MESSAGE_PAYLOAD_PREFIX.length()), evaluableRequest);
      }

      throw new IllegalArgumentException("Unsupported addressLocation expression format: " + addressLocation
            + ". Supported formats: $message.header#/propertyName, $message.payload#/jsonPointer");
   }

   private static String resolveFromHeaders(String pointer, EvaluableRequest evaluableRequest) {
      if (evaluableRequest.getHeaders() == null) {
         throw new IllegalArgumentException(
               "Cannot resolve header expression '$message.header#" + pointer + "': request has no headers");
      }

      // Strip leading '/' from the pointer, as the AsyncAPI spec uses JSON pointer syntax
      // but header lookups use plain names (e.g. "$message.header#/replyTo" -> header key "replyTo")
      String headerName = pointer.startsWith("/") ? pointer.substring(1) : pointer;

      String value = evaluableRequest.getHeaders().get(headerName);
      if (value == null || value.isEmpty()) {
         throw new IllegalArgumentException(
               "Cannot resolve header expression '$message.header#" + pointer + "': header not found in request");
      }

      logger.debugf("Resolved reply address from header '%s': %s", headerName, value);
      return value;
   }

   private static String resolveFromPayload(String pointer, EvaluableRequest evaluableRequest) {
      if (evaluableRequest.getBody() == null || evaluableRequest.getBody().isBlank()) {
         throw new IllegalArgumentException(
               "Cannot resolve payload expression '$message.payload#" + pointer + "': request body is empty");
      }

      try {
         JsonNode rootNode = mapper.readTree(evaluableRequest.getBody());
         JsonPointer jsonPointer = JsonPointer.compile(pointer.isEmpty() ? "" : pointer);
         JsonNode valueNode = rootNode.at(jsonPointer);

         if (valueNode.isMissingNode() || valueNode.isNull()) {
            throw new IllegalArgumentException("Cannot resolve payload expression '$message.payload#" + pointer
                  + "': path not found in request body");
         }

         String value = valueNode.isTextual() ? valueNode.asText() : valueNode.toString();
         logger.debugf("Resolved reply address from payload '%s': %s", pointer, value);
         return value;
      } catch (IllegalArgumentException e) {
         throw e;
      } catch (Exception e) {
         throw new IllegalArgumentException("Cannot resolve payload expression '$message.payload#" + pointer
               + "': failed to parse request body as JSON", e);
      }
   }
}
