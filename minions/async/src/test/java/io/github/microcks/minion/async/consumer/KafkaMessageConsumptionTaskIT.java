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

import io.github.microcks.minion.async.AsyncTestSpecification;
import io.github.microcks.util.AvroUtil;

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicCollection;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link KafkaMessageConsumptionTask} class.
 * @author laurent
 */
@Testcontainers
class KafkaMessageConsumptionTaskIT {

   private static final String TOPIC_NAME = "test-topic";
   private static final String TEXT_MESSAGE_TEMPLATE = "{\"greeting\": \"Hello World!\", \"number\": %s}";
   private static final String AVRO_SCHEMA = """
         {
           "namespace": "microcks.avro",
           "type": "record",
           "name": "Message",
           "fields": [
              {"name": "greeting", "type": "string"},
              {"name": "number",  "type": "int"}
           ]
         }
         """;

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
   void testReceiveTextMessageOnTopicCorrectly() throws Exception {
      // Arrange.
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(1000L);
      asyncTestSpecification.setEndpointUrl(
            "kafka://%s/%s".formatted(kafkaContainer.getBootstrapServers().replace("PLAINTEXT://", ""), TOPIC_NAME));

      KafkaMessageConsumptionTask kafkaMessageConsumptionTask = new KafkaMessageConsumptionTask(asyncTestSpecification);

      // Act.
      ExecutorService executorService = Executors.newFixedThreadPool(2);
      List<Future<List<ConsumedMessage>>> outputs = executorService
            .invokeAll(List.of(new Callable<List<ConsumedMessage>>() {
               @Override
               public List<ConsumedMessage> call() throws Exception {
                  // Wait a bit so that consumption task has actually start.
                  await().during(600, TimeUnit.MILLISECONDS).until(() -> true);
                  sendTextMessagesOnTopic(1);
                  return Collections.emptyList();
               }
            }, kafkaMessageConsumptionTask), asyncTestSpecification.getTimeoutMS() + 1000L, TimeUnit.MILLISECONDS);

      List<ConsumedMessage> messages = outputs.get(1).get();

      // Assert.
      Assertions.assertFalse(messages.isEmpty());
      Assertions.assertEquals(1, messages.size());
      ConsumedMessage message = messages.get(0);
      Assertions.assertEquals(TEXT_MESSAGE_TEMPLATE.formatted(0),
            new String(message.getPayload(), StandardCharsets.UTF_8));
   }

   @Test
   void testReceiveTextMessageOnTopicWithOffsetsCorrectly() throws Exception {
      // Arrange.
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(1000L);
      asyncTestSpecification.setEndpointUrl("kafka://%s/%s?startOffset=0&endOffset=4"
            .formatted(kafkaContainer.getBootstrapServers().replace("PLAINTEXT://", ""), TOPIC_NAME));

      KafkaMessageConsumptionTask kafkaMessageConsumptionTask = new KafkaMessageConsumptionTask(asyncTestSpecification);

      // Send 10 messages.
      sendTextMessagesOnTopic(10);

      // Act.
      List<ConsumedMessage> messages = kafkaMessageConsumptionTask.call();

      // Assert.
      Assertions.assertFalse(messages.isEmpty());
      Assertions.assertEquals(5, messages.size());
      for (int i = 0; i < 5; i++) {
         ConsumedMessage message = messages.get(i);
         Assertions.assertEquals(TEXT_MESSAGE_TEMPLATE.formatted(i),
               new String(message.getPayload(), StandardCharsets.UTF_8));
      }
   }

   @Test
   void testReceiveAvroMessageOnTopicWithRegistry() throws Exception {
      // Arrange
      GenericContainer<?> schemaRegistryContainer = new GenericContainer<>(
            DockerImageName.parse("confluentinc/cp-schema-registry:7.5.0")).withNetwork(NETWORK).withExposedPorts(8889)
                  .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                  .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8889")
                  .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS",
                        //"PLAINTEXT://" + kafkaContainer.getNetworkAliases().get(0) + ":9092")
                        "PLAINTEXT://kafka:19092")
                  .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));
      schemaRegistryContainer.start();

      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(1000L);
      asyncTestSpecification.setEndpointUrl(("kafka://%s/%s?startOffset=0"
            + "&registryUrl=http://localhost:%s&registryUsername=fred:letmein&registryAuthCredSource=USER_INFO")
                  .formatted(kafkaContainer.getBootstrapServers().replace("PLAINTEXT://", ""), TOPIC_NAME,
                        schemaRegistryContainer.getMappedPort(8889)));

      KafkaMessageConsumptionTask kafkaMessageConsumptionTask = new KafkaMessageConsumptionTask(asyncTestSpecification);

      // Send 1 Avro message.
      sendAvroMessagesOnTopicWithRegistry(schemaRegistryContainer.getMappedPort(8889), 1);

      // Act.
      List<ConsumedMessage> messages = kafkaMessageConsumptionTask.call();
      schemaRegistryContainer.stop();

      // Assert.
      Assertions.assertFalse(messages.isEmpty());
      Assertions.assertEquals(1, messages.size());
      ConsumedMessage message = messages.get(0);

      Assertions.assertNull(message.getPayload());
      Assertions.assertNotNull(message.getPayloadRecord());
      Assertions.assertEquals(TEXT_MESSAGE_TEMPLATE.formatted(0), message.getPayloadRecord().toString());
   }

   private static void sendTextMessagesOnTopic(int number) {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-str-producer");
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

      KafkaProducer<String, String> producer = new KafkaProducer<>(props);

      for (int i = 0; i < number; i++) {
         ProducerRecord<String, String> kafkaRecord = new ProducerRecord<>(TOPIC_NAME,
               String.valueOf(System.currentTimeMillis()), TEXT_MESSAGE_TEMPLATE.formatted(i));
         producer.send(kafkaRecord);
      }
      producer.flush();
      producer.close();
   }

   private static void sendAvroMessagesOnTopicWithRegistry(int schemaRegistryPort, int number) throws Exception {
      Properties props = new Properties();
      props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaContainer.getBootstrapServers());
      props.put(ProducerConfig.CLIENT_ID_CONFIG, "microcks-async-minion-registry-producer");
      props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

      // Put Confluent Registry specific SerDes class and registry properties.
      props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
      props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,
            "http://localhost:%d".formatted(schemaRegistryPort));
      props.put(AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, true);
      props.put(AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY,
            io.confluent.kafka.serializers.subject.TopicRecordNameStrategy.class.getName());

      KafkaProducer<String, GenericRecord> producer = new KafkaProducer<>(props);

      for (int i = 0; i < number; i++) {
         GenericRecord avroRecord = AvroUtil.jsonToAvroRecord(TEXT_MESSAGE_TEMPLATE.formatted(i), AVRO_SCHEMA);
         ProducerRecord<String, GenericRecord> kafkaRecord = new ProducerRecord<>(TOPIC_NAME,
               String.valueOf(System.currentTimeMillis()), avroRecord);
         producer.send(kafkaRecord);
      }
      producer.flush();
      producer.close();
   }
}
