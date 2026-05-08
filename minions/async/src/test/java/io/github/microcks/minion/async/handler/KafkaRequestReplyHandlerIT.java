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
import io.github.microcks.domain.BindingType;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ReplyInfo;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;
import io.github.microcks.minion.async.producer.FakeMicrocksAPIConnector;
import io.github.microcks.minion.async.producer.KafkaProducerManager;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link KafkaRequestReplyHandler} and {@link RequestReplyHandlerManager} classes.
 *
 * @author adamhicks
 */
@Testcontainers
class KafkaRequestReplyHandlerIT {

   private static final String REQUEST_TOPIC = "UserAccountService-1.0.0-user-signup";
   private static final String REPLY_TOPIC = "UserAccountService-1.0.0-user-signup-reply";
   private static final Network NETWORK = Network.newNetwork();

   @Container
   private static final KafkaContainer kafkaContainer = new KafkaContainer(
         DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withNetwork(NETWORK).withNetworkAliases("kafka")
               .withListener(() -> "kafka:19092");

   private KafkaRequestReplyHandler handler;
   private KafkaProducerManager kafkaProducerManager;

   @BeforeEach
   void beforeEach() throws Exception {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());

      try (AdminClient adminClient = AdminClient.create(props)) {
         // Delete topics if they exist
         adminClient.deleteTopics(TopicCollection.ofTopicNames(List.of(REQUEST_TOPIC, REPLY_TOPIC)));
      }

      // Wait a bit for topic deletion
      Thread.sleep(500);
   }

   @AfterEach
   void afterEach() {
      if (handler != null && handler.isRunning()) {
         handler.stop();
      }
   }

   @Test
   void testRequestReplyHandlerWithJsonMessages() throws Exception {
      // Arrange.
      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0-reply.yaml"));

      // Prepare request and reply messages.
      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("UserSignupRequest");
      requestMessage.setMediaType("application/json");
      requestMessage.setContent("""
            {"email": "john.doe@example.com", "username": "johndoe", "fullName": "John Doe"}""");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("reply-msg-1"); // Set ID for linking
      replyMessage.setName("UserSignupReply");
      replyMessage.setMediaType("application/json");
      replyMessage.setContent("""
            {"userId": "usr-12345", "status": "success", "message": "User account created successfully"}""");

      // Link request to reply via replyId
      requestMessage.setReplyId(replyMessage.getId());

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("user-account-service-id");
      service.setName("User Account Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation signupOperation = new Operation();
      signupOperation.setName("RECEIVE user/signup");
      signupOperation.addResourcePath(REQUEST_TOPIC);

      // Setup reply information
      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress(REPLY_TOPIC);
      signupOperation.setReply(replyInfo);

      service.setOperations(List.of(signupOperation));

      // Assemble them into a repository - both request and reply messages!
      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signupOperation,
            List.of(requestMessage, replyMessage));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("user-account-service-id",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);

      // Setup Kafka producer manager for replies
      kafkaProducerManager = createKafkaProducerManager();

      // Create Kafka binding
      Binding kafkaBinding = new Binding();
      kafkaBinding.setType(BindingType.KAFKA);

      // Create and start the handler
      KafkaRequestReplyHandlerFactory factory = createFactory(kafkaProducerManager, schemaRegistry);
      handler = factory.createHandler(mockDefinition, kafkaBinding);

      handler.start();

      // Wait for consumer to be ready
      Thread.sleep(2000);

      // Act - Send a request message to the request topic
      sendRequestMessage(REQUEST_TOPIC, """
            {"email": "john.doe@example.com", "username": "johndoe", "fullName": "John Doe"}""");

      // Wait for handler to process and send reply
      Thread.sleep(1000);

      // Consume reply messages from reply topic
      List<String> replyMessages = consumeMessagesFromTopic(REPLY_TOPIC, 2000);

      // Assert.
      assertFalse(replyMessages.isEmpty(), "Should have received at least one reply message");

      String receivedReply = replyMessages.get(0);
      assertTrue(receivedReply.contains("usr-12345"), "Reply should contain userId");
      assertTrue(receivedReply.contains("success"), "Reply should contain success status");
   }

   @Test
   void testRequestReplyHandlerIgnoresUnmatchingMessage() throws Exception {
      // Arrange.
      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0-reply.yaml"));

      // Prepare request and reply messages.
      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("UserSignupRequest");
      requestMessage.setMediaType("application/json");
      requestMessage.setContent("""
            {"email": "john.doe@example.com", "username": "johndoe", "fullName": "John Doe"}""");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("reply-msg-1"); // Set ID for linking
      replyMessage.setName("UserSignupReply");
      replyMessage.setMediaType("application/json");
      replyMessage.setContent("""
            {"userId": "usr-12345", "status": "success", "message": "User account created successfully"}""");

      // Link request to reply via replyId
      requestMessage.setReplyId(replyMessage.getId());

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("user-account-service-id");
      service.setName("User Account Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation signupOperation = new Operation();
      signupOperation.setName("RECEIVE user/signup");
      signupOperation.addResourcePath(REQUEST_TOPIC);

      // Setup reply information
      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress(REPLY_TOPIC);
      signupOperation.setReply(replyInfo);

      service.setOperations(List.of(signupOperation));

      // Assemble them into a repository - both request and reply messages!
      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signupOperation,
            List.of(requestMessage, replyMessage));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("user-account-service-id",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);

      // Setup Kafka producer manager for replies
      kafkaProducerManager = createKafkaProducerManager();

      // Create Kafka binding
      Binding kafkaBinding = new Binding();
      kafkaBinding.setType(BindingType.KAFKA);

      // Create and start the handler
      KafkaRequestReplyHandlerFactory factory = createFactory(kafkaProducerManager, schemaRegistry);
      handler = factory.createHandler(mockDefinition, kafkaBinding);

      handler.start();

      // Wait for consumer to be ready
      Thread.sleep(2000);

      // Act - Send a request message to the request topic with test@example.com
      // instead of john.doe@example.com
      sendRequestMessage(REQUEST_TOPIC, """
            {"email": "test@example.com", "username": "johndoe", "fullName": "John Doe"}""");

      // Wait for handler to process and send reply
      Thread.sleep(1000);

      // Consume reply messages from reply topic
      List<String> replyMessages = consumeMessagesFromTopic(REPLY_TOPIC, 2000);

      // Assert.
      assertTrue(replyMessages.isEmpty(), "Should not have received any reply message");
   }

   @Test
   void testRequestReplyHandlerWithDynamicAddressLocation() throws Exception {
      String DYNAMIC_REPLY_TOPIC = "UserAccountService-1.0.0-user-signup-dynamic-reply";

      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0-reply.yaml"));

      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("UserSignupRequest");
      requestMessage.setMediaType("application/json");
      requestMessage.setContent("""
            {"email": "john.doe@example.com", "username": "johndoe", "fullName": "John Doe"}""");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("reply-msg-dynamic");
      replyMessage.setName("UserSignupReply");
      replyMessage.setMediaType("application/json");
      replyMessage.setContent("""
            {"userId": "usr-dynamic", "status": "success", "message": "Dynamic reply"}""");

      requestMessage.setReplyId(replyMessage.getId());

      Service service = new Service();
      service.setId("user-account-service-dynamic");
      service.setName("User Account Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation signupOperation = new Operation();
      signupOperation.setName("RECEIVE user/signup");
      signupOperation.addResourcePath(REQUEST_TOPIC);

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setAddressLocation("$message.header#/replyTo");
      signupOperation.setReply(replyInfo);

      service.setOperations(List.of(signupOperation));

      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signupOperation,
            List.of(requestMessage, replyMessage));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("user-account-service-dynamic",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);

      kafkaProducerManager = createKafkaProducerManager();

      Binding kafkaBinding = new Binding();
      kafkaBinding.setType(BindingType.KAFKA);

      KafkaRequestReplyHandlerFactory factory = createFactory(kafkaProducerManager, schemaRegistry);
      handler = factory.createHandler(mockDefinition, kafkaBinding);

      handler.start();
      Thread.sleep(2000);

      // Send request with replyTo header pointing to dynamic reply topic
      sendRequestMessageWithHeader(REQUEST_TOPIC, """
            {"email": "john.doe@example.com", "username": "johndoe", "fullName": "John Doe"}""", "replyTo",
            DYNAMIC_REPLY_TOPIC);

      Thread.sleep(1000);

      List<String> replyMessages = consumeMessagesFromTopic(DYNAMIC_REPLY_TOPIC, 2000);

      assertFalse(replyMessages.isEmpty(), "Should have received at least one reply message on dynamic topic");

      String receivedReply = replyMessages.get(0);
      assertTrue(receivedReply.contains("usr-dynamic"), "Reply should contain dynamic userId");
   }

   @Test
   void testRequestReplyHandlerWithPayloadAddressLocation() throws Exception {
      String PAYLOAD_REPLY_TOPIC = "UserAccountService-1.0.0-user-signup-payload-reply";

      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0-reply.yaml"));

      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("UserSignupRequest");
      requestMessage.setMediaType("application/json");
      requestMessage.setContent("""
            {"email": "jane@example.com", "username": "janedoe"}""");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("reply-msg-payload");
      replyMessage.setName("UserSignupReply");
      replyMessage.setMediaType("application/json");
      replyMessage.setContent("""
            {"userId": "usr-payload", "status": "success"}""");

      requestMessage.setReplyId(replyMessage.getId());

      Service service = new Service();
      service.setId("user-account-service-payload");
      service.setName("User Account Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation signupOperation = new Operation();
      signupOperation.setName("RECEIVE user/signup");
      signupOperation.addResourcePath(REQUEST_TOPIC);

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setAddressLocation("$message.payload#/replyChannel");
      signupOperation.setReply(replyInfo);

      service.setOperations(List.of(signupOperation));

      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signupOperation,
            List.of(requestMessage, replyMessage));

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("user-account-service-payload",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);

      kafkaProducerManager = createKafkaProducerManager();

      Binding kafkaBinding = new Binding();
      kafkaBinding.setType(BindingType.KAFKA);

      KafkaRequestReplyHandlerFactory factory = createFactory(kafkaProducerManager, schemaRegistry);
      handler = factory.createHandler(mockDefinition, kafkaBinding);

      handler.start();
      Thread.sleep(2000);

      // Send request with replyChannel in the payload
      sendRequestMessage(REQUEST_TOPIC,
            """
                  {"email": "jane@example.com", "username": "janedoe", "replyChannel": "UserAccountService-1.0.0-user-signup-payload-reply"}""");

      Thread.sleep(1000);

      List<String> replyMessages = consumeMessagesFromTopic(PAYLOAD_REPLY_TOPIC, 2000);

      assertFalse(replyMessages.isEmpty(), "Should have received at least one reply message on payload-derived topic");

      String receivedReply = replyMessages.get(0);
      assertTrue(receivedReply.contains("usr-payload"), "Reply should contain payload userId");
   }

   @Test
   void testHandlerStartStop() throws Exception {
      // Arrange.
      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0-reply.yaml"));

      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("TestRequest");
      requestMessage.setMediaType("application/json");
      requestMessage.setContent("{}");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("test-reply-1");
      replyMessage.setName("TestReply");
      replyMessage.setMediaType("application/json");
      replyMessage.setContent("""
            {"status": "ok"}""");

      // Link request to reply
      requestMessage.setReplyId(replyMessage.getId());

      Service service = new Service();
      service.setId("test-service-id");
      service.setName("Test Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("RECEIVE test/op");
      operation.addResourcePath("test/op");

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress("test/reply");
      operation.setReply(replyInfo);

      service.setOperations(List.of(operation));

      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, operation,
            List.of(requestMessage, replyMessage));

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("test-service-id", asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);

      kafkaProducerManager = createKafkaProducerManager();

      Binding kafkaBinding = new Binding();
      kafkaBinding.setType(BindingType.KAFKA);

      KafkaRequestReplyHandlerFactory factory = createFactory(kafkaProducerManager, schemaRegistry);
      handler = factory.createHandler(mockDefinition, kafkaBinding);

      // Act & Assert
      assertFalse(handler.isRunning(), "Handler should not be running initially");

      handler.start();
      Thread.sleep(500);
      assertTrue(handler.isRunning(), "Handler should be running after start");

      handler.stop();
      Thread.sleep(500);
      assertFalse(handler.isRunning(), "Handler should not be running after stop");
   }

   private void sendRequestMessage(String topic, String message) {
      sendRequestMessageWithHeader(topic, message, null, null);
   }

   private void sendRequestMessageWithHeader(String topic, String message, String headerKey, String headerValue) {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());

      try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(props)) {
         ProducerRecord<String, byte[]> record = new ProducerRecord<>(topic, message.getBytes(StandardCharsets.UTF_8));
         if (headerKey != null && headerValue != null) {
            record.headers().add(new org.apache.kafka.common.header.internals.RecordHeader(headerKey,
                  headerValue.getBytes(StandardCharsets.UTF_8)));
         }
         producer.send(record).get();
         producer.flush();
      } catch (Exception e) {
         fail("Failed to send request message", e);
      }
   }

   private List<String> consumeMessagesFromTopic(String topic, long timeout) {
      List<String> messages = new ArrayList<>();

      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
      props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, "test-consumer-" + System.currentTimeMillis());
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

      // Only retrieve incoming messages and do not persist offset.
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

      try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
         // Subscribe Kafka consumer and receive messages.
         consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceListener() {
            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
               partitions.forEach(p -> consumer.seek(p, 0));
            }
         });

         long startTime = System.currentTimeMillis();
         long timeoutTime = startTime + timeout;

         while (System.currentTimeMillis() < timeoutTime) {
            ConsumerRecords<String, byte[]> records = consumer
                  .poll(Duration.ofMillis(timeoutTime - System.currentTimeMillis()));
            if (!records.isEmpty()) {
               records.forEach(rec -> messages.add(new String(rec.value(), StandardCharsets.UTF_8)));
            }
         }
      } catch (Exception e) {
         fail("Exception while connecting to Kafka broker", e);
      }
      return messages;
   }

   private KafkaProducerManager createKafkaProducerManager() {
      KafkaProducerManager manager = new KafkaProducerManager();
      // Use reflection to set private fields for testing
      try {
         java.lang.reflect.Field configField = KafkaProducerManager.class.getDeclaredField("config");
         configField.setAccessible(true);
         configField.set(manager, new FakeConfig(kafkaContainer.getBootstrapServers()));

         java.lang.reflect.Field bootstrapField = KafkaProducerManager.class.getDeclaredField("bootstrapServers");
         bootstrapField.setAccessible(true);
         bootstrapField.set(manager, kafkaContainer.getBootstrapServers());

         java.lang.reflect.Field registryField = KafkaProducerManager.class.getDeclaredField("schemaRegistryUrl");
         registryField.setAccessible(true);
         registryField.set(manager, Optional.empty());

         manager.create();
      } catch (Exception e) {
         fail("Failed to initialize KafkaProducerManager", e);
      }
      return manager;
   }

   private KafkaRequestReplyHandlerFactory createFactory(KafkaProducerManager producerManager,
         SchemaRegistry schemaRegistry) {
      FakeConfig config = new FakeConfig(kafkaContainer.getBootstrapServers());
      return new KafkaRequestReplyHandlerFactory(producerManager, schemaRegistry, config);
   }

   private static class FakeConfig implements Config {
      private final Map<String, String> properties = new HashMap<>();

      public FakeConfig(String bootstrapServers) {
         properties.put("kafka.bootstrap.servers", bootstrapServers);
      }

      @Override
      @SuppressWarnings("unchecked")
      public <T> T getValue(String propertyName, Class<T> propertyType) {
         if (properties.containsKey(propertyName)) {
            return (T) properties.get(propertyName);
         }
         return null;
      }

      @Override
      public ConfigValue getConfigValue(String propertyName) {
         return null;
      }

      @Override
      @SuppressWarnings("unchecked")
      public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
         if (properties.containsKey(propertyName)) {
            return Optional.of((T) properties.get(propertyName));
         }
         return Optional.empty();
      }

      @Override
      public Iterable<String> getPropertyNames() {
         return properties.keySet();
      }

      @Override
      public Iterable<ConfigSource> getConfigSources() {
         return null;
      }

      @Override
      public <T> Optional<Converter<T>> getConverter(Class<T> forType) {
         return Optional.empty();
      }

      @Override
      public <T> T unwrap(Class<T> type) {
         return null;
      }
   }
}
