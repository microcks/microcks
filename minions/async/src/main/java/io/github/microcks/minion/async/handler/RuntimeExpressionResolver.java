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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * Shared resolver for AsyncAPI Runtime Expressions used within the {@code reply.address.location} field of a
 * request-reply operation. This is protocol agnostic: it only requires the incoming request headers/attributes (as a
 * simple String map) and its raw payload, so any protocol specific request-reply handler can reuse it instead of
 * re-implementing its own resolution logic.
 * <p>
 * Supported expression formats:
 * <ul>
 * <li>{@code $message.header#/<name>} - extracts the value from a named request header/attribute.</li>
 * <li>{@code $message.payload#/<jsonPointer>} - extracts the value from the JSON request payload using a JSON
 * Pointer.</li>
 * </ul>
 *
 * @author laurent
 */
public final class RuntimeExpressionResolver {

   private static final Logger logger = Logger.getLogger(RuntimeExpressionResolver.class);

   private static final String HEADER_EXPRESSION_PREFIX = "$message.header#/";
   private static final String PAYLOAD_EXPRESSION_PREFIX = "$message.payload#";

   private static final ObjectMapper MAPPER = new ObjectMapper();

   private RuntimeExpressionResolver() {
      // Hide default constructor as this is a utility class.
   }

   /**
    * Resolve an AsyncAPI Runtime Expression against the incoming request headers/attributes and payload.
    *
    * @param expression The runtime expression string (e.g. {@code $message.header#/replyTo})
    * @param headers    The request headers or attributes (protocol representation agnostic, as a simple Map)
    * @param payload    The raw request payload (as a String, most often JSON)
    * @return The resolved string value, or {@code null} if the expression could not be resolved
    */
   public static String resolve(String expression, Map<String, String> headers, String payload) {
      if (expression == null) {
         return null;
      }

      if (expression.startsWith(HEADER_EXPRESSION_PREFIX)) {
         String headerName = expression.substring(HEADER_EXPRESSION_PREFIX.length());
         if (headers != null && headers.containsKey(headerName)) {
            return headers.get(headerName);
         }
         logger.warnf("Header/attribute '%s' not found in request message for addressLocation expression '%s'",
               headerName, expression);
         return null;
      }

      if (expression.startsWith(PAYLOAD_EXPRESSION_PREFIX)) {
         String jsonPointer = expression.substring(PAYLOAD_EXPRESSION_PREFIX.length());
         if (payload == null) {
            return null;
         }
         try {
            JsonNode root = MAPPER.readTree(payload);
            JsonNode node = root.at(jsonPointer);
            if (!node.isMissingNode() && !node.isNull()) {
               return node.asText();
            }
            logger.warnf("JSON Pointer '%s' yielded no value in request payload for expression '%s'", jsonPointer,
                  expression);
         } catch (Exception e) {
            logger.errorf(e, "Failed to parse request payload as JSON for addressLocation expression '%s'", expression);
         }
         return null;
      }

      logger.warnf("Unsupported addressLocation expression format (expected $message.header or $message.payload): '%s'",
            expression);
      return null;
   }
}
