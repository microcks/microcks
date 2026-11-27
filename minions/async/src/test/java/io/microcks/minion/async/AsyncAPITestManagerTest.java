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
package io.github.microcks.minion.async;

import io.github.microcks.util.asyncapi.AsyncAPISchemaUtil;
import io.github.microcks.util.asyncapi.AsyncAPISchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

public class AsyncAPITestManagerTest {

   private static final String ASYNC_API_2_SCHEMA_TEXT1 = """
         asyncapi: '2.0.0'
         info:
           title: Example
           version: '1.0.0'
           description: Example YAML
         channels:
           sendMessage:
             description: Example YAML
             publish:
               summary: Example YAML
               operationId: sendMessage
               message:
                 $ref: '#/components/messages/send'
         components:
           messages:
             send:
               summary: Example message
               contentType: text/plain
         """;

   @Test
   void testGetExpectedContentType() {
      JsonNode specificationNode = null;
      try {
         specificationNode = AsyncAPISchemaValidator.getJsonNodeForSchema(ASYNC_API_2_SCHEMA_TEXT1);
      } catch (IOException e) {
         Assertions.fail("Exception should not be thrown");
      }
      String messagePathPointer = AsyncAPISchemaUtil.findMessagePathPointer(specificationNode, "SEND sendMessage");
      Assertions.assertNotNull(messagePathPointer);
      Assertions.assertTrue(messagePathPointer.contains("/channels/sendMessage/publish/message"));
      String expectedContentType = getExpectedContentType(specificationNode, messagePathPointer);
      Assertions.assertTrue(expectedContentType.contains("text/plain"));
   }

   /** Retrieve the expected content type for an AsyncAPI message. */
   private String getExpectedContentType(JsonNode specificationNode, String messagePathPointer) {
      // Retrieve default content type, defaulting to application/json.
      String defaultContentType = specificationNode.path("defaultContentType").asText("application/json");
      // Get message real content type if defined.
      String contentType = defaultContentType;
      JsonNode messageNode = specificationNode.at(messagePathPointer);
      // messageNode will be an array of messages
      if (messageNode.isArray() && messageNode.size() > 0) {
         messageNode = messageNode.get(0);
      }
      // If it's a $ref, then navigate to it.
      while (messageNode.has("$ref")) {
         // $ref: '#/components/messages/lightMeasured'
         String ref = messageNode.path("$ref").asText();
         messageNode = specificationNode.at(ref.substring(1));
      }
      if (messageNode.has("contentType")) {
         contentType = messageNode.path("contentType").asText();
      }
      return contentType;
   }
}
