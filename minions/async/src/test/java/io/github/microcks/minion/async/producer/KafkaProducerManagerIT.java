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
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.TestCaseResult;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.client.KeycloakConfig;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;
import io.github.microcks.minion.async.client.dto.TestCaseReturnDTO;
import io.github.microcks.util.AvroUtil;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

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

   private static final Network NETWORK = Network.newNetwork();

   @Container
   private static final KafkaContainer kafkaContainer = new KafkaContainer(
         DockerImageName.parse("confluentinc/cp-kafka:7.5.0")).withNetwork(NETWORK).withNetworkAliases("kafka")
         .withListener(() -> "kafka:19092");

   @Test
   void testProduceAvroMockMessages() throws Exception {
      // Arrange.
      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-avro-asyncapi-oneof-2.3.yaml"));

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
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signedupOperation, List.of(aliceEvent, bobEvent));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new MicrocksAPIConnector() {
         @Override
         public KeycloakConfig getKeycloakConfig() {
            return null;
         }

         @Override
         public List<Service> listServices(String authorization, int page, int size) {
            return null;
         }

         @Override
         public ServiceView getService(String authorization, String serviceId, boolean messages) {
            return null;
         }

         @Override
         public List<Resource> getResources(String serviceId) {
            if ("d3d5a3ed-13bf-493f-a06d-bf93392f420b".equals(serviceId)) {
               Resource resource = new Resource();
               resource.setId("578497d2-2695-4aff-b7c6-ff7b9a373aa9");
               resource.setType(ResourceType.ASYNC_API_SPEC);
               resource.setContent(asyncAPIContent);
               return List.of(resource);
            }
            return Collections.emptyList();
         }

         @Override
         public TestCaseResult reportTestCaseResult(String testResultId, TestCaseReturnDTO testCaseReturn) {
            return null;
         }
      };
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);
      schemaRegistry.updateRegistryForService("d3d5a3ed-13bf-493f-a06d-bf93392f420b");

      // Finally, arrange the objects under test.
      KafkaProducerManager kafkaProducerManager = new KafkaProducerManager();
      kafkaProducerManager.config = new Config() {
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
      };
      kafkaProducerManager.schemaRegistryUrl = Optional.empty();
      kafkaProducerManager.bootstrapServers = kafkaContainer.getBootstrapServers();
      kafkaProducerManager.create();

      ProducerManager producerManager = new ProducerManager(mockRepository, schemaRegistry, kafkaProducerManager,
            null, null, null, null, null, null);

      // Act.
      producerManager.produceKafkaMockMessages(mockDefinition);

      // Consume messages on topic during 1 second.
      List<byte[]> messages = consumerMessagesFromTopic("UsersignedupAvroAPI-0.1.1-user-signedup", 1000);

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

   private static List<byte[]> consumerMessagesFromTopic(String topic, long timeout) {
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

      KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props);
      try {
         // Subscribe Kafka consumer and receive 1 message.
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
      } finally {
         consumer.close();
      }
      return messages;
   }
}
