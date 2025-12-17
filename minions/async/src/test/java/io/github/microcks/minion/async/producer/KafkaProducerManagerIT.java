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

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;
import io.github.microcks.util.AvroUtil;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.shaded.com.github.dockerjava.core.MediaType;
import org.testcontainers.shaded.com.google.common.net.HttpHeaders;
import org.testcontainers.utility.DockerImageName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link KafkaProducerManager} and {@link ProducerManager} classes.
 * @author laurent
 */
@Testcontainers
class KafkaProducerManagerIT {

   private static final String TOPIC_NAME = "UsersignedupAvroAPI-0.1.1-user-signedup";
   private static final Network NETWORK = Network.newNetwork();

   @Container
   private static final KafkaContainer kafkaContainer = new KafkaContainer(
         DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withNetwork(NETWORK).withNetworkAliases("kafka")
               .withListener(() -> "kafka:19092");

   @BeforeEach
   void beforeEach() {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());

      try (AdminClient adminClient = AdminClient.create(props)) {
         // Delete test-topic topic
         adminClient.deleteTopics(TopicCollection.ofTopicNames(List.of(TOPIC_NAME)));
      }
   }

   @Test
   void testProduceAvroMockMessages() throws Exception {
      // Arrange.
      String asyncAPIContent = Files.readString(Paths.get("target/test-classes/io/github/microcks/minion/async",
            "user-signedup-avro-asyncapi-oneof-2.3.yaml"));

      // Prepare some event messages.
      EventMessage aliceEvent = new EventMessage();
      aliceEvent.setName("Alice");
      aliceEvent.setMediaType("avro/binary");
      aliceEvent.setContent("{\"displayName\": \"Alice\"}");
      EventMessage bobEvent = new EventMessage();
      bobEvent.setName("Bob");
      bobEvent.setMediaType("avro/binary");
      bobEvent.setContent("{\"email\": \"bob@example.com\"}");

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("d3d5a3ed-13bf-493f-a06d-bf93392f420b");
      service.setName("User signed-up Avro API");
      service.setVersion("0.1.1");
      service.setType(ServiceType.EVENT);

      Operation signedupOperation = new Operation();
      signedupOperation.setName("SUBSCRIBE user/signedup");
      service.setOperations(List.of(signedupOperation));

      // Assemble them into a repository.
      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signedupOperation,
            List.of(aliceEvent, bobEvent));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("d3d5a3ed-13bf-493f-a06d-bf93392f420b",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);
      schemaRegistry.updateRegistryForService("d3d5a3ed-13bf-493f-a06d-bf93392f420b");

      // Finally, arrange the objects under test.
      KafkaProducerManager kafkaProducerManager = new KafkaProducerManager();
      kafkaProducerManager.config = new FakeConfig();
      kafkaProducerManager.schemaRegistryUrl = Optional.empty();
      kafkaProducerManager.bootstrapServers = kafkaContainer.getBootstrapServers();
      kafkaProducerManager.create();

      ProducerManager producerManager = new ProducerManager(mockRepository, schemaRegistry, kafkaProducerManager, null,
            null, null, null, null, null);

      // Act.
      producerManager.produceKafkaMockMessages(mockDefinition);

      // Consume messages on topic during 1 second.
      List<byte[]> messages = consumeMessagesFromTopic(TOPIC_NAME, 1000);

      // Assert.
      assertFalse(messages.isEmpty());

      Schema signupUser = SchemaBuilder.record("SignupUser").fields().requiredString("displayName").endRecord();
      Schema loginUser = SchemaBuilder.record("LoginUser").fields().requiredString("email").endRecord();
      Schema union = SchemaBuilder.unionOf().type(signupUser).and().type(loginUser).endUnion();

      for (byte[] message : messages) {
         String json = AvroUtil.avroToJson(message, union);
         assertTrue(json.contains("Alice") || json.contains("bob@example.com"));
         assertTrue(json.contains("displayName") || json.contains("name"));
         // We'll probably have a {"displayName": "bob@example.com"} result here because of the union type
         // and the limitation of Avro that doesn't serialize property names. Both schema can actually be used
         // for deserialization and so the first wins!
      }
   }

   @Test
   void testProduceAvroMockMessagesWithRegistry() throws Exception {
      // Arrange.
      GenericContainer<?> schemaRegistryContainer = new GenericContainer<>(
            DockerImageName.parse("confluentinc/cp-schema-registry:7.5.0")).withNetwork(NETWORK).withExposedPorts(8889)
                  .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                  .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8889")
                  .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
                        //"PLAINTEXT://" + kafkaContainer.getNetworkAliases().get(0) + ":9092")
                        "PLAINTEXT://kafka:19092")
                  .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));
      schemaRegistryContainer.start();

      String asyncAPIContent = Files.readString(Paths.get("target/test-classes/io/github/microcks/minion/async",
            "user-signedup-avro-asyncapi-oneof-2.3.yaml"));

      // Prepare some event messages.
      EventMessage aliceEvent = new EventMessage();
      aliceEvent.setName("Alice");
      aliceEvent.setMediaType("avro/binary");
      aliceEvent.setContent("{\"displayName\": \"Alice\"}");
      EventMessage bobEvent = new EventMessage();
      bobEvent.setName("Bob");
      bobEvent.setMediaType("avro/binary");
      bobEvent.setContent("{\"email\": \"bob@example.com\"}");

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("d3d5a3ed-13bf-493f-a06d-bf93392f420b");
      service.setName("User signed-up Avro API");
      service.setVersion("0.1.1");
      service.setType(ServiceType.EVENT);

      Operation signedupOperation = new Operation();
      signedupOperation.setName("SUBSCRIBE user/signedup");
      service.setOperations(List.of(signedupOperation));

      // Assemble them into a repository.
      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signedupOperation,
            List.of(aliceEvent, bobEvent));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("d3d5a3ed-13bf-493f-a06d-bf93392f420b",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);
      schemaRegistry.updateRegistryForService("d3d5a3ed-13bf-493f-a06d-bf93392f420b");

      // Finally, arrange the objects under test.
      KafkaProducerManager kafkaProducerManager = new KafkaProducerManager();
      kafkaProducerManager.config = new FakeConfig();
      kafkaProducerManager.schemaRegistryUrl = Optional
            .of("http://localhost:%s".formatted(schemaRegistryContainer.getMappedPort(8889)));
      kafkaProducerManager.schemaRegistryConfluent = true;
      kafkaProducerManager.schemaRegistryUsername = Optional.empty();
      kafkaProducerManager.schemaRegistryCredentialsSource = "USER_INFO";
      kafkaProducerManager.bootstrapServers = kafkaContainer.getBootstrapServers();
      kafkaProducerManager.create();

      ProducerManager producerManager = new ProducerManager(mockRepository, schemaRegistry, kafkaProducerManager, null,
            null, null, null, null, null);
      producerManager.defaultAvroEncoding = "REGISTRY";

      // Act.
      producerManager.produceKafkaMockMessages(mockDefinition);

      // Consume messages on topic during 1 second.
      List<GenericRecord> messages = consumeAvroMessagesFromTopic(TOPIC_NAME,
            kafkaProducerManager.schemaRegistryUrl.get(), 1000);

      // Assert.
      assertFalse(messages.isEmpty());

      for (GenericRecord message : messages) {
         assertTrue(
               message.getSchema().getName().equals("SignupUser") || message.getSchema().getName().equals("LoginUser"));

         if ("SignupUser".equals(message.getSchema().getName())) {
            assertEquals("Alice", message.get("displayName").toString());
         } else {
            assertEquals("bob@example.com", message.get("email").toString());
         }
      }

      // Check that schemas have been put into registry.
      URL url = URI.create(kafkaProducerManager.schemaRegistryUrl.get() + "/subjects").toURL();
      HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
      httpConn.setRequestMethod("GET");
      httpConn.setRequestProperty(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON.getMediaType());
      httpConn.setDoOutput(false);

      if (httpConn.getResponseCode() == 200) {
         // Read response content and disconnect Http connection.
         StringBuilder responseContent = new StringBuilder();
         try (BufferedReader br = new BufferedReader(
               new InputStreamReader(httpConn.getInputStream(), StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
               responseContent.append(responseLine.trim());
            }
         }

         assertEquals(
               "[\"UsersignedupAvroAPI-0.1.1-user-signedup-LoginUser\",\"UsersignedupAvroAPI-0.1.1-user-signedup-SignupUser\"]",
               responseContent.toString());
      } else {
         httpConn.disconnect();
         schemaRegistryContainer.stop();
         fail("Schema registry /subjects request failed with code " + httpConn.getResponseCode());
      }

      schemaRegistryContainer.stop();
   }

   private static List<byte[]> consumeMessagesFromTopic(String topic, long timeout) {
      List<byte[]> messages = new ArrayList<>();

      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
      props.put(ConsumerConfig.GROUP_ID_CONFIG, "random-" + System.currentTimeMillis());
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, "random-" + System.currentTimeMillis());
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName());

      // Only retrieve incoming messages and do not persist offset.
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
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

         while (System.currentTimeMillis() - startTime < timeout) {
            ConsumerRecords<String, byte[]> records = consumer
                  .poll(Duration.ofMillis(timeoutTime - System.currentTimeMillis()));
            if (!records.isEmpty()) {
               records.forEach(rec -> messages.add(rec.value()));
            }
         }
      } catch (Exception e) {
         fail("Exception while connecting to Kafka broker", e);
      }
      return messages;
   }

   private static List<GenericRecord> consumeAvroMessagesFromTopic(String topic, String registryUrl, long timeout) {
      List<GenericRecord> messages = new ArrayList<>();

      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
      props.put(ConsumerConfig.GROUP_ID_CONFIG, "random-" + System.currentTimeMillis());
      props.put(ConsumerConfig.CLIENT_ID_CONFIG, "random-" + System.currentTimeMillis());
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
      props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, registryUrl);

      // Only retrieve incoming messages and do not persist offset.
      props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

      try (KafkaConsumer<String, GenericRecord> consumer = new KafkaConsumer<>(props)) {
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

         while (System.currentTimeMillis() - startTime < timeout) {
            ConsumerRecords<String, GenericRecord> records = consumer
                  .poll(Duration.ofMillis(timeoutTime - System.currentTimeMillis()));
            if (!records.isEmpty()) {
               records.forEach(rec -> messages.add(rec.value()));
            }
         }
      } catch (Exception e) {
         fail("Exception while connecting to Kafka broker", e);
      }
      return messages;
   }

   private static class FakeConfig implements Config {
      @Override
      public <T> T getValue(String propertyName, Class<T> propertyType) {
         return null;
      }

      @Override
      public ConfigValue getConfigValue(String propertyName) {
         return null;
      }

      @Override
      public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
         return Optional.empty();
      }

      @Override
      public Iterable<String> getPropertyNames() {
         return null;
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
