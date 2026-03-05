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
 */
public class EventData {

   private static final Logger LOGGER = Logger.getLogger(EventData.class);
   private static final ObjectMapper MAPPER = new ObjectMapper();

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
         JsonNode expectedNode = MAPPER.readTree(expectedCriteria);
         JsonNode incomingNode = MAPPER.readTree(payload);
         return isJsonSubset(expectedNode, incomingNode);
      } catch (StreamReadException e) {
         LOGGER.errorf("message data is not valid JSON: %s", e.getMessage());
         return false;

      } catch (IOException e) {
         LOGGER.errorf("Error reading expected message data: %s", e.getMessage());
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
