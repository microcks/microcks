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

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.producer.KafkaProducerManager;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Kafka implementation of request-reply handler. Listens for incoming request messages on a Kafka topic and sends
 * correlated replies.
 *
 * @author adamhicks
 */
public class KafkaRequestReplyHandler implements RequestReplyHandler {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private final AsyncMockDefinition mockDefinition;
   private final Binding binding;
   private final KafkaProducerManager producerManager;
   private final SchemaRegistry schemaRegistry;
   private final Config config;

   private KafkaConsumer<String, byte[]> consumer;
   private Thread consumerThread;
   private final AtomicBoolean running = new AtomicBoolean(false);

   /**
    * Create a new Kafka request-reply handler.
    *
    * @param mockDefinition  The mock definition containing operation and messages
    * @param binding         The Kafka binding information
    * @param producerManager The producer manager for sending replies
    * @param schemaRegistry  The schema registry for message schemas
    * @param config          The application configuration
    */
   public KafkaRequestReplyHandler(AsyncMockDefinition mockDefinition, Binding binding,
         KafkaProducerManager producerManager, SchemaRegistry schemaRegistry, Config config) {
      this.mockDefinition = mockDefinition;
      this.binding = binding;
      this.producerManager = producerManager;
      this.schemaRegistry = schemaRegistry;
      this.config = config;
   }

   @Override
   public void start() throws Exception {
      if (running.get()) {
         logger.warn("Handler already running");
         return;
      }

      logger.infof("Starting Kafka request-reply handler for %s - %s",
            mockDefinition.getOwnerService().getName() + ":" + mockDefinition.getOwnerService().getVersion(),
            mockDefinition.getOperation().getName());

      // Create Kafka consumer
      consumer = createConsumer();

      // Start consumer thread
      running.set(true);
      consumerThread = new Thread(this::consumeLoop, "kafka-request-reply-" + getRequestTopic());
      consumerThread.start();

      logger.infof("Kafka request-reply handler started for %s", getRequestTopic());
   }

   @Override
   public void stop() {
      if (!running.get()) {
         return;
      }

      logger.infof("Stopping Kafka request-reply handler for %s", getRequestTopic());

      running.set(false);

      if (consumer != null) {
         consumer.wakeup();
      }

      if (consumerThread != null) {
         try {
            consumerThread.join(5000); // Wait up to 5 seconds for graceful shutdown
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted while waiting for consumer thread to stop");
         }
      }

      if (consumer != null) {
         consumer.close();
      }

      logger.infof("Kafka request-reply handler stopped for %s", getRequestTopic());
   }

   @Override
   public boolean isRunning() {
      return running.get();
   }

   @Override
   public AsyncMockDefinition getMockDefinition() {
      return mockDefinition;
   }

   /** Main consumer loop - runs in separate thread. */
   private void consumeLoop() {
      logger.debugf("Starting consume loop for %s", getRequestTopic());

      try {
         while (running.get()) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, byte[]> record : records) {
               handleRequest(record);
            }
         }
      } catch (org.apache.kafka.common.errors.WakeupException e) {
         // Expected on shutdown
         logger.debug("Consumer wakeup called, shutting down");
      } catch (Exception e) {
         logger.errorf(e, "Unexpected error in consume loop for %s", getRequestTopic());
      } finally {
         logger.debugf("Exiting consume loop for %s", getRequestTopic());
      }
   }

   /** Handle a single request message and send the corresponding reply. */
   private void handleRequest(ConsumerRecord<String, byte[]> record) {
      logger.debugf("Received request message on %s", getRequestTopic());

      // Build an EvaluableRequest from the incoming Kafka request record.
      String requestBody = new String(record.value(), StandardCharsets.UTF_8);
      EvaluableRequest evaluableRequest = new EvaluableRequest(requestBody, null);

      // Extract request headers and make them available.
      Map<String, String> requestHeaders = new HashMap<>();
      for (org.apache.kafka.common.header.Header header : record.headers()) {
         requestHeaders.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
      }
      evaluableRequest.setHeaders(requestHeaders);

      // Find matching request-reply pair
      EventMessage replyMessage = findReplyForRequest(evaluableRequest);
      if (replyMessage == null) {
         logger.warnf("No matching reply message found for request, skipping");
         return;
      }

      // Render reply message content
      String replyContent = renderReplyContent(replyMessage, evaluableRequest);

      // Publish reply
      String replyTopic = getReplyTopic(replyMessage);
      String key = String.valueOf(System.currentTimeMillis());

      logger.debugf("Sending reply to %s", replyTopic);

      // Convert to byte array and publish with headers
      byte[] replyBytes = replyContent.getBytes(StandardCharsets.UTF_8);
      Set<org.apache.kafka.common.header.Header> kafkaHeaders = buildReplyHeaders(replyMessage);
      producerManager.publishMessage(replyTopic, key, replyBytes, kafkaHeaders);
   }

   /**
    * Find the reply message that corresponds to the incoming request. Matches requests to replies using the replyId
    * field. Request messages have their replyId set to point to their corresponding reply message's ID.
    */
   private EventMessage findReplyForRequest(EvaluableRequest evaluableRequest) {
      // Find the request message that matches the incoming content
      // Request messages have a replyId that points to their reply
      EventMessage matchingRequest = mockDefinition.getEventMessages().stream().filter(msg -> msg.getReplyId() != null)
            .filter(msg -> EventData.matchesExpectedMessage(msg, evaluableRequest.getBody())).findFirst().orElse(null);

      if (matchingRequest == null) {
         logger.debugf("No request message definition matches incoming request");
         return null;
      }

      // Find the reply message using the request's replyId
      String replyId = matchingRequest.getReplyId();
      return mockDefinition.getEventMessages().stream().filter(msg -> replyId.equals(msg.getId())).findFirst()
            .orElse(null);
   }

   /** Render reply content with any templating. */
   private String renderReplyContent(EventMessage replyMessage, EvaluableRequest evaluableRequest) {
      String content = replyMessage.getContent();

      // Apply template rendering if content contains expressions
      if (content.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         logger.debug("Reply message contains dynamic EL expression, rendering it...");
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
         engine.getContext().setVariable("request", evaluableRequest);

         try {
            content = engine.getValue(content);
         } catch (Throwable t) {
            logger.errorf(t, "Failed to evaluate template '%s'", content);
         }
      }

      return content;
   }

   /** Build headers for reply message. */
   private Set<org.apache.kafka.common.header.Header> buildReplyHeaders(EventMessage replyMessage) {
      Set<org.apache.kafka.common.header.Header> headers = new HashSet<>();

      // TODO: Add correlation ID header when we parse correlation object from
      // AsyncAPI spec

      // Add any headers defined in the reply message
      if (replyMessage.getHeaders() != null) {
         for (Header domainHeader : replyMessage.getHeaders()) {
            // Convert domain Header to Kafka Header
            if (domainHeader.getValues() != null && !domainHeader.getValues().isEmpty()) {
               String value = domainHeader.getValues().iterator().next();
               org.apache.kafka.common.header.Header kafkaHeader = new org.apache.kafka.common.header.internals.RecordHeader(
                     domainHeader.getName(), value.getBytes(StandardCharsets.UTF_8));
               headers.add(kafkaHeader);
            }
         }
      }

      return headers;
   }

   /** Get the request topic name. */
   private String getRequestTopic() {
      return producerManager.getTopicName(mockDefinition, mockDefinition.getEventMessages().get(0));
   }

   /** Get the reply topic name. */
   private String getReplyTopic(EventMessage replyMessage) {
      if (mockDefinition.getOperation().getReply() == null) {
         throw new IllegalStateException(
               "Request-reply operation must have reply information, but none found for operation: "
                     + mockDefinition.getOperation().getName());
      }

      // Check if we have an addressLocation (runtime expression)
      if (mockDefinition.getOperation().getReply().getAddressLocation() != null) {
         // TODO: Implement runtime expression resolution for addressLocation
         // This would parse expressions like "$message.header#/replyTo" or
         // "$message.payload#/topicName"
         // and extract the value from the request message headers or payload
         throw new UnsupportedOperationException(
               "Runtime expression resolution for addressLocation is not yet supported: "
                     + mockDefinition.getOperation().getReply().getAddressLocation());
      }

      // Use the channelAddress as the raw topic name
      if (mockDefinition.getOperation().getReply().getChannelAddress() != null) {
         return mockDefinition.getOperation().getReply().getChannelAddress();
      }

      throw new IllegalStateException("Reply information must specify either channelAddress or addressLocation");
   }

   /** Create and configure the Kafka consumer. */
   private KafkaConsumer<String, byte[]> createConsumer() {
      Properties props = new Properties();

      // Get bootstrap servers from config
      String bootstrapServers = config.getValue("kafka.bootstrap.servers", String.class);
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

      // Generate unique group ID for this handler
      String groupId = "microcks-request-reply-" + mockDefinition.getOwnerService().getId() + "-"
            + mockDefinition.getOperation().getName();
      props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-request-reply");

      // Use byte array deserializer for flexibility
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

      // Start from latest messages
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

      // Add security config if available
      addSecurityConfig(props);

      KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(props);

      // Subscribe to request topic
      String requestTopic = getRequestTopic();
      kafkaConsumer.subscribe(Collections.singletonList(requestTopic));

      logger.infof("Created Kafka consumer for request topic: %s", requestTopic);

      return kafkaConsumer;
   }

   /** Add security configuration if available. */
   private void addSecurityConfig(Properties props) {
      Optional<String> securityProtocol = config.getOptionalValue("kafka.security.protocol", String.class);
      if (securityProtocol.isPresent()) {
         props.put("security.protocol", securityProtocol.get());

         if (config.getOptionalValue("kafka.ssl.truststore.location", String.class).isPresent()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG,
                  config.getValue("kafka.ssl.truststore.location", String.class));
         }
         if (config.getOptionalValue("kafka.ssl.truststore.password", String.class).isPresent()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG,
                  config.getValue("kafka.ssl.truststore.password", String.class));
         }
         if (config.getOptionalValue("kafka.ssl.truststore.type", String.class).isPresent()) {
            props.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG,
                  config.getValue("kafka.ssl.truststore.type", String.class));
         }
      }
   }
}
