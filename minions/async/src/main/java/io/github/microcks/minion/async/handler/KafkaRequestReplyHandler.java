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

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.Config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Kafka implementation of request-reply handler. Listens for incoming request messages on a Kafka topic and sends
 * correlated replies.
 *
 * @author adamhicks
 */
public class KafkaRequestReplyHandler extends AbstractRequestReplyHandler {

   private final KafkaProducerManager producerManager;
   private final SchemaRegistry schemaRegistry;
   private final Config config;

   private KafkaConsumer<String, byte[]> consumer;
   private Thread consumerThread;

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
      super(mockDefinition, binding);
      this.producerManager = producerManager;
      this.schemaRegistry = schemaRegistry;
      this.config = config;
   }

   @Override
   public void start() throws Exception {
      if (isRunning()) {
         getLogger().warn("Handler already running");
         return;
      }

      getLogger().infof("Starting Kafka request-reply handler for %s - %s",
            mockDefinition.getOwnerService().getName() + ":" + mockDefinition.getOwnerService().getVersion(),
            mockDefinition.getOperation().getName());

      consumer = createConsumer();

      setRunning(true);
      consumerThread = new Thread(this::consumeLoop, "kafka-request-reply-" + getRequestTopic());
      consumerThread.start();

      getLogger().infof("Kafka request-reply handler started for %s", getRequestTopic());
   }

   @Override
   public void stop() {
      if (!isRunning()) {
         return;
      }

      getLogger().infof("Stopping Kafka request-reply handler for %s", getRequestTopic());

      setRunning(false);

      if (consumer != null) {
         consumer.wakeup();
      }

      if (consumerThread != null) {
         try {
            consumerThread.join(5000);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            getLogger().warn("Interrupted while waiting for consumer thread to stop");
         }
      }

      if (consumer != null) {
         consumer.close();
      }

      getLogger().infof("Kafka request-reply handler stopped for %s", getRequestTopic());
   }

   /** Main consumer loop - runs in separate thread. */
   private void consumeLoop() {
      getLogger().debugf("Starting consume loop for %s", getRequestTopic());

      try {
         while (isRunning()) {
            ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(1000));

            for (ConsumerRecord<String, byte[]> record : records) {
               handleRequest(record);
            }
         }
      } catch (org.apache.kafka.common.errors.WakeupException e) {
         getLogger().debug("Consumer wakeup called, shutting down");
      } catch (Exception e) {
         getLogger().errorf(e, "Unexpected error in consume loop for %s", getRequestTopic());
      } finally {
         getLogger().debugf("Exiting consume loop for %s", getRequestTopic());
      }
   }

   /** Handle a single request message and send the corresponding reply. */
   private void handleRequest(ConsumerRecord<String, byte[]> record) {
      getLogger().debugf("Received request message on %s", getRequestTopic());

      String requestBody = new String(record.value(), StandardCharsets.UTF_8);
      EvaluableRequest evaluableRequest = new EvaluableRequest(requestBody, null);

      Map<String, String> requestHeaders = new HashMap<>();
      for (org.apache.kafka.common.header.Header header : record.headers()) {
         requestHeaders.put(header.key(), new String(header.value(), StandardCharsets.UTF_8));
      }
      evaluableRequest.setHeaders(requestHeaders);

      EventMessage replyMessage = findReplyForRequest(evaluableRequest);
      if (replyMessage == null) {
         getLogger().warnf("No matching reply message found for request, skipping");
         return;
      }

      String replyContent = renderReplyContent(replyMessage, evaluableRequest);

      String replyTopic = getReplyDestination(evaluableRequest);
      String key = String.valueOf(System.currentTimeMillis());

      getLogger().debugf("Sending reply to %s", replyTopic);

      byte[] replyBytes = replyContent.getBytes(StandardCharsets.UTF_8);
      Set<org.apache.kafka.common.header.Header> kafkaHeaders = buildReplyHeaderSet(replyMessage);
      producerManager.publishMessage(replyTopic, key, replyBytes, kafkaHeaders);
   }

   /** Build Kafka-specific header set for reply message. */
   private Set<org.apache.kafka.common.header.Header> buildReplyHeaderSet(EventMessage replyMessage) {
      Set<org.apache.kafka.common.header.Header> headers = new HashSet<>();

      if (replyMessage.getHeaders() != null) {
         for (Header domainHeader : replyMessage.getHeaders()) {
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

   /** Create and configure the Kafka consumer. */
   private KafkaConsumer<String, byte[]> createConsumer() {
      Properties props = new Properties();

      String bootstrapServers = config.getValue("kafka.bootstrap.servers", String.class);
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

      String groupId = "microcks-request-reply-" + mockDefinition.getOwnerService().getId() + "-"
            + mockDefinition.getOperation().getName();
      props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-request-reply");

      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, true);

      addSecurityConfig(props);

      KafkaConsumer<String, byte[]> kafkaConsumer = new KafkaConsumer<>(props);

      String requestTopic = getRequestTopic();
      kafkaConsumer.subscribe(Collections.singletonList(requestTopic));

      getLogger().infof("Created Kafka consumer for request topic: %s", requestTopic);

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
