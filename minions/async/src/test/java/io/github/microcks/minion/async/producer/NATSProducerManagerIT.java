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
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
            new ProducerManager.ProducerDependencies(null, null, natsProducerManager, null, null, null, null, null),
            null);
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

