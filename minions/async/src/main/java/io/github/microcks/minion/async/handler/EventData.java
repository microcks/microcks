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

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.microcks.domain.EventMessage;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.util.Iterator;

/**
 * Compute dispatch criteria for async request-reply events.
 *
 * @author adamhicks
 */
public class EventData {

   private static final Logger logger = Logger.getLogger(EventData.class);
   private static final ObjectMapper mapper = new ObjectMapper();

   private EventData() {
   }

   public static boolean matchesExpectedMessage(EventMessage expMessage, String payload) {
      if (expMessage == null) {
         return false;
      }
      String expectedCriteria = expMessage.getContent();
      if (expectedCriteria == null || expectedCriteria.isBlank()) {
         return true;
      }
      try {
         JsonNode expectedNode = mapper.readTree(expectedCriteria);
         JsonNode incomingNode = mapper.readTree(payload);
         return isJsonSubset(expectedNode, incomingNode);
      } catch (StreamReadException e) {
         logger.errorf("message data is not valid JSON: %s", e.getMessage());
         return false;

      } catch (IOException e) {
         logger.errorf("Error reading expected message data: %s", e.getMessage());
         return false;
      }
   }

   private static boolean isJsonSubset(JsonNode expectedNode, JsonNode incomingNode) {
      if (expectedNode == null || incomingNode == null) {
         return expectedNode == incomingNode;
      }

      if (expectedNode.isObject()) {
         if (!incomingNode.isObject()) {
            return false;
         }
         Iterator<String> fieldNames = expectedNode.fieldNames();
         while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode expectedFieldValue = expectedNode.get(fieldName);
            JsonNode incomingFieldValue = incomingNode.get(fieldName);
            if (incomingFieldValue == null || !isJsonSubset(expectedFieldValue, incomingFieldValue)) {
               return false;
            }
         }
         return true;
      }

      if (expectedNode.isArray()) {
         if (!incomingNode.isArray() || expectedNode.size() != incomingNode.size()) {
            return false;
         }
         for (int i = 0; i < expectedNode.size(); i++) {
            if (!isJsonSubset(expectedNode.get(i), incomingNode.get(i))) {
               return false;
            }
         }
         return true;
      }

      return expectedNode.equals(incomingNode);
   }
}
