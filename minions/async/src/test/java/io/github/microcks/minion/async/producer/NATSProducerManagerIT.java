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
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.util.AvroUtil;

import org.apache.avro.Schema;
import org.apache.avro.SchemaBuilder;

import io.nats.client.Connection;
import io.nats.client.Nats;
import io.nats.client.Subscription;
import io.nats.client.Message;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link NATSProducerManager} class.
 * @author laurent
 */
@Testcontainers
class NATSProducerManagerIT {

   private static final int NATS_PORT = 4222;

   @Container
   private static final GenericContainer<?> natsContainer = new GenericContainer<>(
         DockerImageName.parse("nats:2.14.0-alpine")).withExposedPorts(NATS_PORT)
               .waitingFor(Wait.forLogMessage(".*Server is ready.*\\n", 1));

   @Test
   void testPublishSimpleMessage() throws Exception {
      // Arrange.
      String natsUrl = "nats://localhost:" + natsContainer.getMappedPort(NATS_PORT);

      NATSProducerManager natsProducerManager = new NATSProducerManager();
      natsProducerManager.natsServer = natsUrl;
      natsProducerManager.natsUsername = null;
      natsProducerManager.natsPassword = null;
      natsProducerManager.create();

      // Subscribe to the topic before publishing.
      Connection subscriberConnection = Nats.connect(natsUrl);
      Subscription subscription = subscriberConnection.subscribe("test-topic");
      subscriberConnection.flush(Duration.ofSeconds(1));

      // Act.
      natsProducerManager.publishMessage("test-topic", "{\"greeting\": \"hello\"}", null);

      // Assert.
      Message received = subscription.nextMessage(Duration.ofSeconds(2));
      assertNotNull(received);
      assertEquals("{\"greeting\": \"hello\"}", new String(received.getData(), StandardCharsets.UTF_8));

      subscriberConnection.close();
   }

   @Test
   void testPublishMessageWithHeaders() throws Exception {
      // Arrange.
      String natsUrl = "nats://localhost:" + natsContainer.getMappedPort(NATS_PORT);

      NATSProducerManager natsProducerManager = new NATSProducerManager();
      natsProducerManager.natsServer = natsUrl;
      natsProducerManager.natsUsername = null;
      natsProducerManager.natsPassword = null;
      natsProducerManager.create();

      // Subscribe to the topic before publishing.
      Connection subscriberConnection = Nats.connect(natsUrl);
      Subscription subscription = subscriberConnection.subscribe("test-headers-topic");
      subscriberConnection.flush(Duration.ofSeconds(1));

      // Prepare NATS headers.
      io.nats.client.impl.Headers natsHeaders = new io.nats.client.impl.Headers();
      natsHeaders.add("X-Custom-Header", "custom-value");

      // Act.
      natsProducerManager.publishMessage("test-headers-topic", "{\"user\": \"alice\"}", natsHeaders);

      // Assert.
      Message received = subscription.nextMessage(Duration.ofSeconds(2));
      assertNotNull(received);
      assertEquals("{\"user\": \"alice\"}", new String(received.getData(), StandardCharsets.UTF_8));
      assertTrue(received.hasHeaders());
      assertEquals("custom-value", received.getHeaders().getFirst("X-Custom-Header"));

      subscriberConnection.close();
   }

   @Test
   void testPublishBinaryMessage() throws Exception {
      // Arrange.
      String natsUrl = "nats://localhost:" + natsContainer.getMappedPort(NATS_PORT);

      NATSProducerManager natsProducerManager = new NATSProducerManager();
      natsProducerManager.natsServer = natsUrl;
      natsProducerManager.natsUsername = null;
      natsProducerManager.natsPassword = null;
      natsProducerManager.create();

      // Subscribe to the topic before publishing.
      Connection subscriberConnection = Nats.connect(natsUrl);
      Subscription subscription = subscriberConnection.subscribe("test-binary-topic");
      subscriberConnection.flush(Duration.ofSeconds(1));

      // Bytes that are not valid UTF-8, so that a String round-trip would not preserve them.
      byte[] payload = new byte[] { 0x00, (byte) 0xC3, 0x01, (byte) 0xFF, 0x2A };

      // Act.
      natsProducerManager.publishMessage("test-binary-topic", payload, null);

      // Assert.
      Message received = subscription.nextMessage(Duration.ofSeconds(2));
      assertNotNull(received);
      assertArrayEquals(payload, received.getData());

      subscriberConnection.close();
   }

   @Test
   void testProduceNatsAvroMockMessages() throws Exception {
      // Arrange.
      String natsUrl = "nats://localhost:" + natsContainer.getMappedPort(NATS_PORT);

      String userAvsc = """
            {"type":"record","name":"User","namespace":"io.github.microcks","fields":[\
            {"name":"fullName","type":"string"},{"name":"age","type":"int"}]}""";
      String userJson = "{\"fullName\": \"Laurent Broudoux\", \"age\": 41}";

      // Prepare an Avro event message: its content is JSON, its media type says the wire format is not.
      EventMessage laurentEvent = new EventMessage();
      laurentEvent.setName("Laurent");
      laurentEvent.setMediaType("avro/binary");
      laurentEvent.setContent(userJson);

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("abcd-5678");
      service.setName("User signed-up Avro API");
      service.setVersion("0.1.1");
      service.setType(ServiceType.EVENT);

      Operation signedupOperation = new Operation();
      signedupOperation.setName("SUBSCRIBE user/signedup");
      service.setOperations(List.of(signedupOperation));

      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signedupOperation, List.of(laurentEvent));
      mockRepository.storeMockDefinition(mockDefinition);

      // Hold the Avro schema in the registry as an .avsc attached to the operation.
      Resource resource = new Resource();
      resource.setName("user.avsc");
      resource.setPath("user.avsc");
      resource.setType(ResourceType.AVRO_SCHEMA);
      resource.setContent(userAvsc);
      resource.setOperations(Set.of(signedupOperation.getName()));

      SchemaRegistry schemaRegistry = mock(SchemaRegistry.class);
      when(schemaRegistry.getSchemaEntries(service)).thenReturn(List.of(schemaRegistry.new SchemaEntry(resource)));

      NATSProducerManager natsProducerManager = new NATSProducerManager();
      natsProducerManager.natsServer = natsUrl;
      natsProducerManager.natsUsername = null;
      natsProducerManager.natsPassword = null;
      natsProducerManager.create();

      String expectedTopic = natsProducerManager.getTopicName(mockDefinition, laurentEvent);

      Connection subscriberConnection = Nats.connect(natsUrl);
      Subscription subscription = subscriberConnection.subscribe(expectedTopic);
      subscriberConnection.flush(Duration.ofSeconds(1));

      // Act.
      ProducerManager producerManager = new ProducerManager(mockRepository, schemaRegistry,
            new ProducerManager.ProducerDependencies(null, null, natsProducerManager, null, null, null, null), null);
      producerManager.produceNatsMockMessages(mockDefinition);

      // Assert - what landed on the broker is Avro binary, and it decodes back to the example.
      Message received = subscription.nextMessage(Duration.ofSeconds(2));
      assertNotNull(received);
      assertNotEquals(userJson, new String(received.getData(), StandardCharsets.UTF_8));
      assertEquals(userJson.replace(" ", ""),
            AvroUtil.avroToJson(received.getData(), AvroUtil.getSchema(userAvsc)).replace(" ", ""));

      subscriberConnection.close();
   }

   @Test
   void testProduceNatsAvroMockMessagesFromAsyncAPI3MultiFormatSchema() throws Exception {
      // Arrange - an AsyncAPI v3 document whose Avro schema is wrapped in a Multi Format Schema Object, which is
      // the placement the spec mandates and the one a v3 generator emits.
      String natsUrl = "nats://localhost:" + natsContainer.getMappedPort(NATS_PORT);
      String serviceId = "9f3a0c1e-6b1d-4f2a-9c8e-3d5b7a2f4c10";
      String asyncAPIContent = Files.readString(Paths.get("target/test-classes/io/github/microcks/minion/async",
            "user-signedup-avro-multiformat-asyncapi-3.0.yaml"));

      String laurentJson = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";
      EventMessage laurentEvent = new EventMessage();
      laurentEvent.setName("laurent");
      laurentEvent.setMediaType("avro/binary");
      laurentEvent.setContent(laurentJson);

      Service service = new Service();
      service.setId(serviceId);
      service.setName("User signed-up Avro API V3");
      service.setVersion("0.1.1");
      service.setType(ServiceType.EVENT);

      Operation signedupOperation = new Operation();
      signedupOperation.setName("SEND sendUserSignedUp");
      service.setOperations(List.of(signedupOperation));

      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signedupOperation, List.of(laurentEvent));
      mockRepository.storeMockDefinition(mockDefinition);

      // The registry holds the AsyncAPI spec only: the Avro schema has to be extracted from it, which is what the
      // Multi Format Schema Object handling makes possible.
      SchemaRegistry schemaRegistry = new SchemaRegistry(new FakeMicrocksAPIConnector(serviceId, asyncAPIContent));
      schemaRegistry.updateRegistryForService(serviceId);

      NATSProducerManager natsProducerManager = new NATSProducerManager();
      natsProducerManager.natsServer = natsUrl;
      natsProducerManager.natsUsername = null;
      natsProducerManager.natsPassword = null;
      natsProducerManager.create();

      String expectedTopic = natsProducerManager.getTopicName(mockDefinition, laurentEvent);

      Connection subscriberConnection = Nats.connect(natsUrl);
      Subscription subscription = subscriberConnection.subscribe(expectedTopic);
      subscriberConnection.flush(Duration.ofSeconds(1));

      // Act.
      ProducerManager producerManager = new ProducerManager(mockRepository, schemaRegistry,
            new ProducerManager.ProducerDependencies(null, null, natsProducerManager, null, null, null, null), null);
      producerManager.produceNatsMockMessages(mockDefinition);

      // Assert - Avro binary encoded against the schema embedded in the v3 document.
      Message received = subscription.nextMessage(Duration.ofSeconds(2));
      assertNotNull(received);
      assertNotEquals(laurentJson, new String(received.getData(), StandardCharsets.UTF_8));

      Schema userSchema = SchemaBuilder.record("User").namespace("microcks.avro").fields().requiredString("fullName")
            .requiredString("email").requiredInt("age").endRecord();
      String decoded = AvroUtil.avroToJson(received.getData(), userSchema);
      assertEquals(laurentJson.replace(" ", ""), decoded.replace(" ", ""));

      subscriberConnection.close();
   }

   @Test
   void testProduceNatsMockMessages() throws Exception {
      // Arrange.
      String natsUrl = "nats://localhost:" + natsContainer.getMappedPort(NATS_PORT);

      // Prepare some event messages.
      EventMessage aliceEvent = new EventMessage();
      aliceEvent.setName("Alice");
      aliceEvent.setMediaType("application/json");
      aliceEvent.setContent("{\"displayName\": \"Alice\"}");

      EventMessage bobEvent = new EventMessage();
      bobEvent.setName("Bob");
      bobEvent.setMediaType("application/json");
      bobEvent.setContent("{\"displayName\": \"Bob\"}");

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("abcd-1234");
      service.setName("User signed-up API");
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

      // Create and configure the NATSProducerManager.
      NATSProducerManager natsProducerManager = new NATSProducerManager();
      natsProducerManager.natsServer = natsUrl;
      natsProducerManager.natsUsername = null;
      natsProducerManager.natsPassword = null;
      natsProducerManager.create();

      // Compute the expected topic name.
      String expectedTopic = natsProducerManager.getTopicName(mockDefinition, aliceEvent);

      // Subscribe to the expected topic before publishing.
      Connection subscriberConnection = Nats.connect(natsUrl);
      Subscription subscription = subscriberConnection.subscribe(expectedTopic);
      subscriberConnection.flush(Duration.ofSeconds(1));

      // Act - Publish messages using ProducerManager.
      ProducerManager producerManager = new ProducerManager(mockRepository, null,
            new ProducerManager.ProducerDependencies(null, null, natsProducerManager, null, null, null, null), null);
      producerManager.produceNatsMockMessages(mockDefinition);

      // Assert - Consume messages from NATS.
      List<String> messages = new ArrayList<>();
      Message received;
      while ((received = subscription.nextMessage(Duration.ofSeconds(2))) != null) {
         messages.add(new String(received.getData(), StandardCharsets.UTF_8));
      }

      assertFalse(messages.isEmpty());
      assertEquals(2, messages.size());
      assertTrue(messages.stream().anyMatch(m -> m.contains("Alice")));
      assertTrue(messages.stream().anyMatch(m -> m.contains("Bob")));

      subscriberConnection.close();
   }
}

