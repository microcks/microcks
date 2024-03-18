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

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.BindingType;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class that holds constants definition found in AsyncAPI v2 and v3 specifications as well as utility methods
 * for discovering bindings and examples.
 * @author laurent
 */
public class AsyncAPICommons {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(AsyncAPICommons.class);

   public static final String MESSAGES = "messages";
   public static final String BINDINGS = "bindings";

   public static final String SCHEMA_NODE = "schema";
   public static final String CHANNEL_NODE = "channel";
   public static final String ADDRESS_NODE = "address";
   public static final String PARAMETERS_NODE = "parameters";
   public static final String LOCATION_NODE = "location";
   public static final String EXAMPLES_NODE = "examples";
   public static final String EXAMPLE_VALUE_NODE = "value";
   public static final String EXAMPLE_PAYLOAD_NODE = "payload";
   public static final String EXAMPLE_HEADERS_NODE = "headers";
   public static final String QUEUE_VALUE = "queue";
   public static final String TOPIC_VALUE = "topic";

   private AsyncAPICommons() {
      // Private constructor to hide the implicit one as it's a utility class.
   }

   /**
    * Browse and complete an operation bindings with the bindings information at Channel level.
    * @param operation The operation whose bindings should be completed
    * @param bindings  The Channel level bindings node
    */
   public static void completeChannelLevelBindings(Operation operation, JsonNode bindings) {
      Iterator<String> bindingNames = bindings.fieldNames();
      while (bindingNames.hasNext()) {
         String bindingName = bindingNames.next();
         JsonNode bindingNode = bindings.path(bindingName);

         switch (bindingName) {
            case "ws":
               Binding b = retrieveOrInitOperationBinding(operation, BindingType.WS);
               b.setMethod(bindingNode.path("method").asText(null));
               break;
            case "amqp":
               b = retrieveOrInitOperationBinding(operation, BindingType.AMQP);
               if (bindingNode.has("is")) {
                  String is = bindingNode.path("is").asText();
                  if (QUEUE_VALUE.equals(is)) {
                     b.setDestinationType(QUEUE_VALUE);
                     JsonNode queue = bindingNode.get(QUEUE_VALUE);
                     b.setDestinationName(queue.get("name").asText());
                  } else if ("routingKey".equals(is)) {
                     JsonNode exchange = bindingNode.get("exchange");
                     b.setDestinationType(exchange.get("type").asText());
                  }
               }
               break;
            case "googlepubsub":
               b = retrieveOrInitOperationBinding(operation, BindingType.GOOGLEPUBSUB);
               b.setDestinationName(bindingNode.path(TOPIC_VALUE).asText(null));
               b.setPersistent(bindingNode.path("messageRetentionDuration").asBoolean(false));
               break;
            default:
               break;
         }
      }
   }

   /**
    * Browse and complete an operation bindings with the bindings information at Operation level.
    * @param operation The operation whose bindings should be completed
    * @param bindings  The Operation level bindings node
    */
   public static void completeOperationLevelBindings(Operation operation, JsonNode bindings) {
      Iterator<String> bindingNames = bindings.fieldNames();
      while (bindingNames.hasNext()) {
         String bindingName = bindingNames.next();
         JsonNode bindingNode = bindings.path(bindingName);

         switch (bindingName) {
            case "kafka":
               break;
            case "mqtt":
               Binding b = retrieveOrInitOperationBinding(operation, BindingType.MQTT);
               b.setQoS(bindingNode.path("qos").asText(null));
               b.setPersistent(bindingNode.path("retain").asBoolean(false));
               break;
            case "amqp1":
               b = retrieveOrInitOperationBinding(operation, BindingType.AMQP1);
               b.setDestinationName(bindingNode.path("destinationName").asText(null));
               b.setDestinationType(bindingNode.path("destinationType").asText(null));
               break;
            case "nats":
               b = retrieveOrInitOperationBinding(operation, BindingType.NATS);
               b.setDestinationName(bindingNode.path(QUEUE_VALUE).asText(null));
               break;
            case "sqs":
               b = retrieveOrInitOperationBinding(operation, BindingType.SQS);
               if (bindingNode.has(QUEUE_VALUE)) {
                  b.setDestinationName(bindingNode.get(QUEUE_VALUE).path("name").asText(null));
                  b.setPersistent(bindingNode.path("messageRetentionPeriod").asBoolean(false));
               }
               break;
            case "sns":
               b = retrieveOrInitOperationBinding(operation, BindingType.SNS);
               if (bindingNode.has(TOPIC_VALUE) && bindingNode.get(TOPIC_VALUE).has("name")) {
                  b.setDestinationName(bindingNode.get(TOPIC_VALUE).path("name").asText(null));
               }
               break;
            default:
               break;
         }
      }
   }

   /**
    * Browse and complete an operation bindings with the bindings information at Message level.
    * @param operation The operation whose bindings should be completed
    * @param bindings  The Message level bindings node
    */
   public static void completeMessageLevelBindings(Operation operation, JsonNode bindings) {
      Iterator<String> bindingNames = bindings.fieldNames();
      while (bindingNames.hasNext()) {
         String bindingName = bindingNames.next();
         JsonNode bindingNode = bindings.path(bindingName);

         switch (bindingName) {
            case "kafka":
               Binding b = retrieveOrInitOperationBinding(operation, BindingType.KAFKA);
               if (bindingNode.has("key")) {
                  b.setKeyType(bindingNode.path("key").path("type").asText());
               }
               break;
            default:
               break;
         }
      }
   }

   /** Check variables parts presence into given channel address. */
   public static boolean channelAddressHasParts(String address) {
      return (address.indexOf("{") != -1);
   }

   /** Extract the list of Header from an example node. */
   public static List<Header> getExampleHeaders(JsonNode example) {
      List<Header> results = new ArrayList<>();

      if (example.has(EXAMPLE_HEADERS_NODE)) {
         Iterator<Map.Entry<String, JsonNode>> headers = null;

         if (example.path(EXAMPLE_HEADERS_NODE).isObject()) {
            headers = example.path(EXAMPLE_HEADERS_NODE).fields();
         } else if (example.path(EXAMPLE_HEADERS_NODE).isTextual()) {
            // Try to parse string as a JSON Object...
            try {
               ObjectMapper mapper = new ObjectMapper();
               JsonNode headersNode = mapper.readTree(example.path(EXAMPLE_HEADERS_NODE).asText());
               headers = headersNode.fields();
            } catch (Exception e) {
               log.warn("Headers value {} is a string but not JSON, skipping it",
                     example.path(EXAMPLE_HEADERS_NODE).asText());
            }
         }

         if (headers != null) {
            while (headers.hasNext()) {
               Map.Entry<String, JsonNode> property = headers.next();

               Header header = new Header();
               header.setName(property.getKey());
               // Values may be multiple and CSV.
               Set<String> headerValues = Arrays.stream(property.getValue().asText().split(",")).map(String::trim)
                     .collect(Collectors.toSet());
               header.setValues(headerValues);
               results.add(header);
            }
         }
      }
      return results;
   }

   /** Get existing operation binding type or initialize a new one. */
   private static Binding retrieveOrInitOperationBinding(Operation operation, BindingType type) {
      Binding binding = null;
      if (operation.getBindings() != null) {
         binding = operation.getBindings().get(type.toString());
      }
      if (binding == null) {
         binding = new Binding(type);
         operation.addBinding(type.toString(), binding);
      }
      return binding;
   }
}
