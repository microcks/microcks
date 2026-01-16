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
package io.github.microcks.minion.async.consumer;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;

import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncTestSpecification;
import io.github.microcks.minion.async.ConsumptionPhase;

import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.jboss.logging.Logger;

import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a topic on an Apache Kafka Broker. Endpoint
 * URL should be specified using the following form:
 * <code>kafka://{brokerhost[:port]}/{topic}[?option1=value1&amp;option2=value2]</code>
 * @author laurent
 */
public class KafkaMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating acceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "kafka://(?<brokerUrl>[^:]+(:\\d+)?)/(?<topic>[a-zA-Z0-9-_\\.]+)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   /** The endpoint URL option representing schema registry URL. */
   public static final String REGISTRY_URL_OPTION = "registryUrl";
   /** The endpoint URL option representing schema registry username. */
   public static final String REGISTRY_USERNAME_OPTION = "registryUsername";
   /** The endpoint URL option representing schema registry auth credentials source. */
   public static final String REGISTRY_AUTH_CREDENTIALS_SOURCE = "registryAuthCredSource";

   /** The endpoint URL option representing the offset we should start consume from. */
   public static final String START_OFFSET = "startOffset";
   /** The endpoint URL option representing the offset whe should end consumer to. */
   public static final String END_OFFSET = "endOffset";

   private File trustStore;

   private AsyncTestSpecification specification;

   protected Map<String, String> optionsMap;

   protected KafkaConsumer<String, byte[]> consumer;

   protected KafkaConsumer<String, GenericRecord> avroConsumer;

   protected Long startOffset;
   protected Long endOffset;

   // New fields for phase management
   private volatile ConsumptionPhase currentPhase = ConsumptionPhase.STARTING;
   private static final long DEFAULT_CONNECTION_TIMEOUT_MS = 10000L; // 10 seconds
   private long connectionStartTime;
   private long connectionDuration;
   private long messageWaitStartTime;
   private long messageWaitDuration;


   /**
    * Create a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public KafkaMessageConsumptionTask(AsyncTestSpecification testSpecification) {
      this.specification = testSpecification;
   }

   /**
    * Convenient static method for checking if this implementation will accept endpoint.
    * @param endpointUrl The endpoint URL to validate
    * @return True if endpointUrl can be used for connecting and consuming on endpoint
    */
   public static boolean acceptEndpoint(String endpointUrl) {
      return endpointUrl != null && endpointUrl.matches(ENDPOINT_PATTERN_STRING);
   }

   /**
    * Get the current consumption phase for monitoring purposes.
    * @return The current ConsumptionPhase
    */
   public ConsumptionPhase getCurrentPhase() {
      return currentPhase;
   }

   private void updatePhase(ConsumptionPhase newPhase) {
      this.currentPhase = newPhase;
      if (specification != null) {
         specification.setCurrentPhase(newPhase.name());
      }
   }

   /**
    * Get the connection duration in milliseconds.
    * @return The duration of the connection phase
    */
   public long getConnectionDuration() {
      return connectionDuration;
   }

   /**
    * Get the message wait duration in milliseconds.
    * @return The duration of the message waiting phase
    */
   public long getMessageWaitDuration() {
      return messageWaitDuration;
   }

   @Override
   public List<ConsumedMessage> call() throws Exception {
      updatePhase(ConsumptionPhase.CONNECTING);
      connectionStartTime = System.currentTimeMillis();

      try {
         if (consumer == null && avroConsumer == null) {
            initializeKafkaConsumerWithConnectionTimeout();
         }

         connectionDuration = System.currentTimeMillis() - connectionStartTime;

         List<ConsumedMessage> messages = new ArrayList<>();

         updatePhase(ConsumptionPhase.WAITING_FOR_MESSAGES);
         messageWaitStartTime = System.currentTimeMillis();
         logger.infof("Connected to Kafka in %d ms, now waiting for messages with timeout: %d ms", connectionDuration,
               specification.getTimeoutMS());

         // Start polling with appropriate consumer for records.
         // Do not forget to close the consumer before returning results.
         if (consumer != null) {
            consumeByteArray(messages);
            consumer.close();
         } else {
            consumeAvro(messages);
            avroConsumer.close();
         }

         messageWaitDuration = System.currentTimeMillis() - messageWaitStartTime;
         updatePhase(ConsumptionPhase.COMPLETED);

         logger.infof("Message consumption completed - Connection: %d ms, Message wait: %d ms", connectionDuration,
               messageWaitDuration);

         return messages;
      } catch (Exception e) {
         // Calculate durations even in case of failure
         if (connectionStartTime > 0 && connectionDuration == 0) {
            connectionDuration = System.currentTimeMillis() - connectionStartTime;
         }
         if (messageWaitStartTime > 0 && messageWaitDuration == 0) {
            messageWaitDuration = System.currentTimeMillis() - messageWaitStartTime;
         }

         updatePhase(ConsumptionPhase.FAILED);
         throw e;
      }
   }

   /**
    * Close the resources used by this task. Namely the Kafka consumer(s) and the optionally created truststore holding
    * Kafka client SSL credentials.
    * @throws IOException should not happen.
    */
   @Override
   public void close() throws IOException {
      if (consumer != null) {
         consumer.close();
      }
      if (avroConsumer != null) {
         avroConsumer.close();
      }
      if (trustStore != null && trustStore.exists()) {
         Files.delete(trustStore.toPath());
      }
   }

   /** Initialize Kafka consumer from built properties and subscribe to target topic. */
   private void initializeKafkaConsumer() {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      // Call matcher.find() to be able to use named expressions.
      matcher.find();
      String endpointBrokerUrl = matcher.group("brokerUrl");
      String endpointTopic = matcher.group("topic");
      String options = matcher.group("options");

      // Parse options if specified.
      if (options != null && !options.isBlank()) {
         optionsMap = ConsumptionTaskCommons.initializeOptionsMap(options);
      }

      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, endpointBrokerUrl);

      // Generate a unique GroupID for no collision with previous or other consumers.
      props.put(ConsumerConfig.GROUP_ID_CONFIG, specification.getTestResultId() + "-" + System.currentTimeMillis());
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-test");

      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

      // Value deserializer depends on schema registry presence.
      if (hasOption(REGISTRY_URL_OPTION)) {
         props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
         props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, optionsMap.get(REGISTRY_URL_OPTION));
         // Configure schema registry credentials if any.
         if (hasOption(REGISTRY_USERNAME_OPTION) || hasOption(REGISTRY_AUTH_CREDENTIALS_SOURCE)) {
            props.put(AbstractKafkaSchemaSerDeConfig.USER_INFO_CONFIG, optionsMap.get(REGISTRY_USERNAME_OPTION));
            props.put(AbstractKafkaSchemaSerDeConfig.BASIC_AUTH_CREDENTIALS_SOURCE,
                  optionsMap.get(REGISTRY_AUTH_CREDENTIALS_SOURCE));
         }
      } else {
         props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());
      }

      // Only retrieve incoming messages and do not persist offset.
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

      if (specification.getSecret() != null && specification.getSecret().getCaCertPem() != null) {
         try {
            // Because Kafka Java client does not support any other sources for SSL configuration,
            // we need to create a Truststore holding the secret certificate and credentials. See below:
            // https://cwiki.apache.org/confluence/display/KAFKA/KIP-486%3A+Support+custom+way+to+load+KeyStore+and+TrustStore
            trustStore = ConsumptionTaskCommons.installBrokerCertificate(specification);

            // Then we have to add SSL specific properties.
            props.put("security.protocol", "SSL");
            props.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, trustStore.getAbsolutePath());
            props.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, ConsumptionTaskCommons.TRUSTSTORE_PASSWORD);
         } catch (Exception e) {
            logger.error("Exception while installing custom truststore: " + e.getMessage());
         }
      }

      instanciateKafkaConsumer(props, endpointTopic);
   }

   /** Initialize Kafka consumer with connection timeout management. */
   private void initializeKafkaConsumerWithConnectionTimeout() throws Exception {
      // First, initialize the consumer normally
      initializeKafkaConsumer();

      // Get the configured connection timeout, or use default
      long connectionTimeout = specification.getConnectionTimeoutMS() != null ? specification.getConnectionTimeoutMS()
            : DEFAULT_CONNECTION_TIMEOUT_MS;

      // Test the connection with a separate timeout
      if (!testKafkaConnection(connectionTimeout)) {
         throw new RuntimeException("Failed to connect to Kafka broker within " + connectionTimeout + "ms");
      }
   }

   /** Test Kafka connection with timeout. */
   private boolean testKafkaConnection(long timeoutMs) {
      long startTime = System.currentTimeMillis();

      try {
         KafkaConsumer<?, ?> testConsumer = consumer != null ? consumer : avroConsumer;

         while ((System.currentTimeMillis() - startTime) < timeoutMs) {
            try {
               // Try to list topics with a short timeout to test connectivity
               testConsumer.listTopics(Duration.ofMillis(1000));
               logger.debugf("Successfully connected to Kafka in %d ms", System.currentTimeMillis() - startTime);
               return true;
            } catch (Exception e) {
               // If we haven't timed out yet, wait a bit and retry
               if ((System.currentTimeMillis() - startTime) < timeoutMs) {
                  Thread.sleep(500);
               }
            }
         }
      } catch (Exception e) {
         logger.warnf("Exception while testing Kafka connection: %s", e.getMessage());
      }

      return false;
   }

   private void instanciateKafkaConsumer(Properties props, String endpointTopic) {
      if (hasOption(START_OFFSET)) {
         try {
            startOffset = Long.parseLong(optionsMap.get(START_OFFSET));
         } catch (NumberFormatException nfe) {
            logger.warnf("The startOffset endpoint option {%s} is not a Long", optionsMap.get(START_OFFSET));
         }
      }
      if (hasOption(END_OFFSET)) {
         try {
            endOffset = Long.parseLong(optionsMap.get(END_OFFSET));
         } catch (NumberFormatException nfe) {
            logger.warnf("The endOffset endpoint option {%s} is not a Long", optionsMap.get(END_OFFSET));
         }
      }

      // Create the consumer from properties and subscribe to given topic.
      if (hasOption(REGISTRY_URL_OPTION)) {
         avroConsumer = new KafkaConsumer<>(props);
         avroConsumer.subscribe(Arrays.asList(endpointTopic), new StartOffsetSeeker());
      } else {
         consumer = new KafkaConsumer<>(props);
         consumer.subscribe(Arrays.asList(endpointTopic), new StartOffsetSeeker());
      }
   }

   /**
    * Safe method for checking if an option has been set.
    * @param optionKey Check if that option is available in options map.
    * @return true if option is present, false if undefined.
    */
   protected boolean hasOption(String optionKey) {
      if (optionsMap != null) {
         return optionsMap.containsKey(optionKey);
      }
      return false;
   }

   /** Consume simple byte[] on default consumer. Fill messages array. */
   private void consumeByteArray(List<ConsumedMessage> messages) {
      long startTime = System.currentTimeMillis();
      long timeoutTime = startTime + specification.getTimeoutMS();

      while (System.currentTimeMillis() - startTime < specification.getTimeoutMS()) {
         ConsumerRecords<String, byte[]> records = consumer
               .poll(Duration.ofMillis(timeoutTime - System.currentTimeMillis()));

         boolean oufOfOffsetRange = false;
         for (ConsumerRecord<String, byte[]> consumerRecord : records) {
            // Check current offset if a limit was set.
            if (endOffset != null && consumerRecord.offset() > endOffset) {
               oufOfOffsetRange = true;
               break;
            }
            // Build a ConsumedMessage from Kafka record.
            ConsumedMessage message = new ConsumedMessage();
            message.setReceivedAt(System.currentTimeMillis());
            message.setHeaders(buildHeaders(consumerRecord.headers()));
            message.setPayload(consumerRecord.value());
            messages.add(message);
         }
         // Exit main waiting loop if we reach the end.
         if (oufOfOffsetRange) {
            break;
         }
      }
   }

   /** Consumer avro records when connected to registry. Fill messages array. */
   private void consumeAvro(List<ConsumedMessage> messages) {
      long startTime = System.currentTimeMillis();
      long timeoutTime = startTime + specification.getTimeoutMS();

      while (System.currentTimeMillis() - startTime < specification.getTimeoutMS()) {
         ConsumerRecords<String, GenericRecord> records = avroConsumer
               .poll(Duration.ofMillis(timeoutTime - System.currentTimeMillis()));

         boolean oufOfOffsetRange = false;
         for (ConsumerRecord<String, GenericRecord> consumerRecord : records) {
            // Check current offset if a limit was set.
            if (endOffset != null && consumerRecord.offset() > endOffset) {
               oufOfOffsetRange = true;
               break;
            }
            // Build a ConsumedMessage from Kafka record.
            ConsumedMessage message = new ConsumedMessage();
            message.setReceivedAt(System.currentTimeMillis());
            message.setHeaders(buildHeaders(consumerRecord.headers()));
            message.setPayloadRecord(consumerRecord.value());
            messages.add(message);
         }
         // Exit main waiting loop if we reach the end.
         if (oufOfOffsetRange) {
            break;
         }
      }
   }

   /** Build set of Microcks headers from Kafka headers. */
   private Set<Header> buildHeaders(Headers headers) {
      Set<Header> results = new HashSet<>();
      if (headers == null || !headers.iterator().hasNext()) {
         return results;
      }
      Iterator<org.apache.kafka.common.header.Header> headersIterator = headers.iterator();
      while (headersIterator.hasNext()) {
         org.apache.kafka.common.header.Header header = headersIterator.next();
         Header result = new Header();
         result.setName(header.key());
         result.setValues(Set.of(new String(header.value())));
         results.add(result);
      }
      return results;
   }

   private class StartOffsetSeeker implements ConsumerRebalanceListener {
      @Override
      public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
         // Nothing to do on revocation.
      }

      @Override
      public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
         if (startOffset != null) {
            partitions.forEach(p -> {
               if (consumer != null) {
                  consumer.seek(p, startOffset);
               }
               if (avroConsumer != null) {
                  avroConsumer.seek(p, startOffset);
               }
            });
         }
      }
   }
}
