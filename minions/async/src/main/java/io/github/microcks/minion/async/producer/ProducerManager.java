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
package io.github.microcks.minion.async.producer;

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.BindingType;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.Constants;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.util.AvroUtil;
import io.github.microcks.util.SchemaMap;
import io.github.microcks.util.asyncapi.AsyncAPISchemaUtil;
import io.github.microcks.util.asyncapi.AsyncAPISchemaValidator;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.arc.Unremovable;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * ProducerManager is the responsible for emitting mock event messages when specific frequency triggered is reached.
 * Need to specify it as @Unremovable to avoid Quarkus ARC optimization removing beans that are not injected elsewhere
 * (this one is resolved using Arc.container().instance() method from ProducerScheduler).
 * @author laurent
 */
@Unremovable
@ApplicationScoped
public class ProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   final AsyncMockRepository mockRepository;

   final SchemaRegistry schemaRegistry;

   final KafkaProducerManager kafkaProducerManager;
   final MQTTProducerManager mqttProducerManager;
   final NATSProducerManager natsProducerManager;
   final AMQPProducerManager amqpProducerManager;
   final GooglePubSubProducerManager googlePubSubProducerManager;
   final AmazonSQSProducerManager amazonSQSProducerManager;
   final AmazonSNSProducerManager amazonSNSProducerManager;

   @Inject
   @RootWebSocketProducerManager
   WebSocketProducerManager wsProducerManager;

   @ConfigProperty(name = "minion.supported-bindings")
   String[] supportedBindings;

   @ConfigProperty(name = "minion.default-avro-encoding", defaultValue = "RAW")
   String defaultAvroEncoding;

   public ProducerManager(AsyncMockRepository mockRepository, SchemaRegistry schemaRegistry,
         KafkaProducerManager kafkaProducerManager, MQTTProducerManager mqttProducerManager,
         NATSProducerManager natsProducerManager, AMQPProducerManager amqpProducerManager,
         GooglePubSubProducerManager googlePubSubProducerManager, AmazonSQSProducerManager amazonSQSProducerManager,
         AmazonSNSProducerManager amazonSNSProducerManager) {
      this.mockRepository = mockRepository;
      this.schemaRegistry = schemaRegistry;
      this.kafkaProducerManager = kafkaProducerManager;
      this.mqttProducerManager = mqttProducerManager;
      this.natsProducerManager = natsProducerManager;
      this.amqpProducerManager = amqpProducerManager;
      this.googlePubSubProducerManager = googlePubSubProducerManager;
      this.amazonSQSProducerManager = amazonSQSProducerManager;
      this.amazonSNSProducerManager = amazonSNSProducerManager;
   }

   /**
    * Produce all the async mock messages corresponding to specified frequency.
    * @param frequency The frequency to emit messages for
    */
   public void produceAsyncMockMessagesAt(Long frequency) {
      logger.info("Producing async mock messages for frequency: " + frequency);

      Set<AsyncMockDefinition> mockDefinitions = mockRepository.getMockDefinitionsByFrequency(frequency);
      for (AsyncMockDefinition definition : mockDefinitions) {
         logger.debugf("Processing definition of service {%s}",
               definition.getOwnerService().getName() + ':' + definition.getOwnerService().getVersion());

         for (String binding : definition.getOperation().getBindings().keySet()) {
            // Ensure this minion supports this binding.
            if (Arrays.asList(supportedBindings).contains(binding)) {
               Binding bindingDef = definition.getOperation().getBindings().get(binding);

               switch (BindingType.valueOf(binding)) {
                  case KAFKA:
                     produceKafkaMockMessages(definition);
                     break;
                  case NATS:
                     produceNatsMockMessages(definition);
                     break;
                  case MQTT:
                     produceMQTTMocksMessages(definition);
                     break;
                  case WS:
                     produceWSMockMessages(definition);
                     break;
                  case AMQP:
                     produceAMQPMockMessages(definition, bindingDef);
                     break;
                  case GOOGLEPUBSUB:
                     produceGooglePubSubMockMessages(definition);
                     break;
                  case SQS:
                     produceSQSMockMessages(definition);
                     break;
                  case SNS:
                     produceSNSMockMessages(definition);
                     break;
                  default:
                     break;
               }
            }
         }
      }
   }

   /** Take care publishing Kafka mock messages for definition. */
   protected void produceKafkaMockMessages(AsyncMockDefinition definition) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String topic = kafkaProducerManager.getTopicName(definition, eventMessage);
         String key = String.valueOf(System.currentTimeMillis());
         String message = renderEventMessageContent(eventMessage);

         // Check it Avro binary is expected, we should convert to bytes.
         if (Constants.AVRO_BINARY_CONTENT_TYPES.contains(eventMessage.getMediaType())) {
            // Retrieve an Avro schema for this operation.
            Schema schema = null;

            // First browse schema entries for this operation.
            List<SchemaRegistry.SchemaEntry> entries = schemaRegistry.getSchemaEntries(definition.getOwnerService())
                  .stream().filter(entry -> entry.getOperations() != null
                        && entry.getOperations().contains(definition.getOperation().getName()))
                  .toList();

            if (entries.isEmpty()) {
               // If no entry found for the operation, we have to extract Avro schema from the AsyncAPI spec.
               entries = schemaRegistry.getSchemaEntries(definition.getOwnerService()).stream()
                     .filter(entry -> ResourceType.ASYNC_API_SPEC.equals(entry.getType())).toList();

               SchemaMap schemaMap = new SchemaMap();
               schemaRegistry.getSchemaEntries(definition.getOwnerService())
                     .forEach(schemaEntry -> schemaMap.putSchemaEntry(schemaEntry.getPath(), schemaEntry.getContent()));

               try {
                  // Extract embedded Avro schema from AsyncAPI spec.
                  JsonNode specificationNode = AsyncAPISchemaValidator
                        .getJsonNodeForSchema(entries.getFirst().getContent());
                  schema = AsyncAPISchemaUtil.retrieveMessageAvroSchema(specificationNode, AsyncAPISchemaUtil
                        .findMessagePathPointer(specificationNode, definition.getOperation().getName()), schemaMap);

                  if (schema.isUnion() && schema.getTypes().size() == 1) {
                     schema = schema.getTypes().getFirst();
                  }
               } catch (Exception e) {
                  logger.errorf("Exception while extracting Avro schema from AsyncAPI spec", e);
               }
            } else {
               // Directly get the Avro schema from one schema entry (.asvc file as external ref).
               schema = AvroUtil.getSchema(entries.getFirst().getContent());
            }

            if (schema != null) {
               logger.debugf("Found an Avro schema '%s' for operation '%s'", schema,
                     definition.getOperation().getName());

               try {
                  if (Constants.REGISTRY_AVRO_ENCODING.equals(defaultAvroEncoding)
                        && kafkaProducerManager.isRegistryEnabled()) {
                     logger.debug("Using a registry and converting message to Avro record");
                     GenericRecord avroRecord = AvroUtil.jsonToAvroRecord(message, schema);
                     kafkaProducerManager.publishMessage(topic, key, avroRecord,
                           kafkaProducerManager.renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(),
                                 eventMessage.getHeaders()));
                  } else {
                     logger.debug("Converting message to Avro bytes array");
                     byte[] avroBinary = AvroUtil.jsonToAvro(message, schema);
                     kafkaProducerManager.publishMessage(topic, key, avroBinary,
                           kafkaProducerManager.renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(),
                                 eventMessage.getHeaders()));
                  }
               } catch (Exception e) {
                  logger.errorf("Exception while converting {%s} to Avro using schema {%s}", message, schema.toString(),
                        e);
               }
            } else {
               logger.warnf("Failed finding a suitable Avro schema for the '%s' operation. No publication done.",
                     definition.getOperation().getName());
            }
         } else {
            kafkaProducerManager.publishMessage(topic, key, message, kafkaProducerManager
                  .renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
         }
      }
   }

   /** Take care publishing Nats mock messages for definition. */
   protected void produceNatsMockMessages(AsyncMockDefinition definition) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String topic = natsProducerManager.getTopicName(definition, eventMessage);
         String message = renderEventMessageContent(eventMessage);
         natsProducerManager.publishMessage(topic, message, natsProducerManager
               .renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
      }
   }

   /** Take care publishing MQTT mock messages for definition. */
   protected void produceMQTTMocksMessages(AsyncMockDefinition definition) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String topic = mqttProducerManager.getTopicName(definition, eventMessage);
         String message = renderEventMessageContent(eventMessage);
         mqttProducerManager.publishMessage(topic, message);
      }
   }

   /** Take care publishing WebSocket mock messages for definition. */
   protected void produceWSMockMessages(AsyncMockDefinition definition) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String channel = wsProducerManager.getRequestURI(definition, eventMessage);
         String message = renderEventMessageContent(eventMessage);
         wsProducerManager.publishMessage(channel, message, eventMessage.getHeaders());
      }
   }

   /** Take care publishing AMQP mock messages for definition. */
   protected void produceAMQPMockMessages(AsyncMockDefinition definition, Binding bindingDef) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String destinationName = amqpProducerManager.getDestinationName(definition, eventMessage);
         String message = renderEventMessageContent(eventMessage);
         amqpProducerManager.publishMessage(bindingDef.getDestinationType(), destinationName, message,
               amqpProducerManager.renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(),
                     eventMessage.getHeaders()));
      }
   }

   /** Take care publishing Google PubSub mock messages for definition. */
   protected void produceGooglePubSubMockMessages(AsyncMockDefinition definition) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String topicName = googlePubSubProducerManager.getTopicName(definition, eventMessage);
         String message = renderEventMessageContent(eventMessage);
         googlePubSubProducerManager.publishMessage(topicName, message, googlePubSubProducerManager
               .renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
      }
   }

   /** Take care publishing SQS mock messages for definition. */
   protected void produceSQSMockMessages(AsyncMockDefinition definition) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String queueName = amazonSQSProducerManager.getQueueName(definition, eventMessage);
         String message = renderEventMessageContent(eventMessage);
         amazonSQSProducerManager.publishMessage(queueName, message, amazonSQSProducerManager
               .renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
      }
   }

   /** Take care publishing SNS mock messages for definition. */
   protected void produceSNSMockMessages(AsyncMockDefinition definition) {
      for (EventMessage eventMessage : definition.getEventMessages()) {
         String topicName = amazonSNSProducerManager.getTopicName(definition, eventMessage);
         String message = renderEventMessageContent(eventMessage);
         amazonSNSProducerManager.publishMessage(topicName, message, amazonSNSProducerManager
               .renderEventMessageHeaders(TemplateEngineFactory.getTemplateEngine(), eventMessage.getHeaders()));
      }
   }

   /**
    * Format the destination operation part by computing an address with optional dynamic parts. This doesn't include
    * protocol specific sanitization (like replacing `/` with `-`, etc.)
    * @param operation    The operation to format a destination part for
    * @param eventMessage The message to format a destination part for
    * @return The operation part in a full destination name (usually service + version + operation)
    */
   public static String getDestinationOperationPart(Operation operation, EventMessage eventMessage) {
      // In AsyncAPI v2, channel address is directly the operation name.
      String operationPart = removeActionInOperationName(operation.getName());

      // Take care of templatized address for URI_PART dispatcher style.
      if ("URI_PARTS".equals(operation.getDispatcher())) {
         // In AsyncAPI v3, operation is different from channel and channel templatized address may be in resourcePaths.
         for (String resourcePath : operation.getResourcePaths()) {
            if (resourcePath.contains("{")) {
               operationPart = resourcePath;
               break;
            }
         }
         operationPart = replacePartPlaceholders(operationPart, eventMessage);
      }
      return operationPart;
   }

   /** Render event message content from definition applying template rendering if required. */
   private String renderEventMessageContent(EventMessage eventMessage) {
      String content = eventMessage.getContent();
      if (content.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         logger.debug("EventMessage contains dynamic EL expression, rendering it...");
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();

         try {
            content = engine.getValue(content);
         } catch (Throwable t) {
            logger.errorf("Failing at evaluating template '%s'", content, t);
         }
      }
      return content;
   }

   /** Remove the AsyncAPI action (or verb) at the beginning of operation name if present. */
   private static String removeActionInOperationName(String operationName) {
      if (operationName.startsWith("SUBSCRIBE ") || operationName.startsWith("PUBLISH ")
            || operationName.startsWith("SEND ") || operationName.startsWith("RECEIVE ")) {
         return operationName.substring(operationName.indexOf(" ") + 1);
      }
      return operationName;
   }

   /** Replace address placeholders ('{}') with their values coming from message dispatch criteria. */
   private static String replacePartPlaceholders(String address, EventMessage eventMessage) {
      String partsCriteria = eventMessage.getDispatchCriteria();
      if (partsCriteria != null && !partsCriteria.isBlank()) {
         String[] criterion = partsCriteria.split("/");
         for (String criteria : criterion) {
            if (criteria != null && !criteria.isBlank()) {
               String[] element = criteria.split("=");
               String key = String.format("\\{%s\\}", element[0]);
               address = address.replaceAll(key, URLEncoder.encode(element[1], Charset.defaultCharset()));
            }
         }
      }
      return address;
   }
}
