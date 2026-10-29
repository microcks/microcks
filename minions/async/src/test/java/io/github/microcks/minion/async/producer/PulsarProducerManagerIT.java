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
import io.github.microcks.domain.Service;
import io.github.microcks.minion.async.AsyncMockDefinition;

import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.pulsar.PulsarContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link PulsarProducerManager} class.
 * @author Malay Dewangan
 */
@Testcontainers
class PulsarProducerManagerIT {

   @Container
   private static final PulsarContainer pulsarContainer = new PulsarContainer(
         DockerImageName.parse("apachepulsar/pulsar:4.2.4"));

   private PulsarProducerManager pulsarProducerManager;
   private PulsarClient subscriberClient;

   @BeforeEach
   void setUp() throws Exception {
      pulsarProducerManager = new PulsarProducerManager();
      pulsarProducerManager.serviceUrl = pulsarContainer.getPulsarBrokerUrl();
      pulsarProducerManager.create();

      subscriberClient = PulsarClient.builder().serviceUrl(pulsarContainer.getPulsarBrokerUrl()).build();
   }

   @AfterEach
   void tearDown() throws Exception {
      pulsarProducerManager.close();
      subscriberClient.close();
   }

   @Test
   void testPublishMessage() throws Exception {
      try (Consumer<byte[]> consumer = subscriberClient.newConsumer().topic("test-topic").subscriptionName("test")
            .subscribe()) {
         pulsarProducerManager.publishMessage("test-topic", "{\"greeting\": \"hello\"}",
               Map.of("x-trace-id", "abc-123"));

         Message<byte[]> received = consumer.receive(5, TimeUnit.SECONDS);
         assertNotNull(received);
         assertEquals("{\"greeting\": \"hello\"}", new String(received.getValue(), StandardCharsets.UTF_8));
         assertEquals("abc-123", received.getProperty("x-trace-id"));
      }
   }

   @Test
   void testGetTopicName() {
      Service service = new Service();
      service.setName("User signed-up API");
      service.setVersion("0.1.1");
      Operation operation = new Operation();
      operation.setName("SUBSCRIBE user/signedup");
      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, List.of());

      assertEquals("UsersignedupAPI-0.1.1-user-signedup",
            pulsarProducerManager.getTopicName(definition, new EventMessage()));

      Binding binding = new Binding(BindingType.PULSAR);
      binding.setPersistent(false);
      operation.addBinding(BindingType.PULSAR.name(), binding);
      assertEquals("non-persistent://public/default/UsersignedupAPI-0.1.1-user-signedup",
            pulsarProducerManager.getTopicName(definition, new EventMessage()));
   }
}
