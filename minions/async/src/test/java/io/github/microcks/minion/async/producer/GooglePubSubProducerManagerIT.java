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

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is an integration test using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link GooglePubSubProducerManager} and {@link ProducerManager} classes.
 * @author laurent
 */
@Testcontainers
class GooglePubSubProducerManagerIT {

   private static final Network NETWORK = Network.newNetwork();

   private ManagedChannel channel;
   private TransportChannelProvider channelProvider;
   private NoCredentialsProvider credentialsProvider;
   private SubscriptionAdminClient subscriptionAdminClient;
   private List<String> messages;

   @Container
   private static final PubSubEmulatorContainer emulatorContainer = new PubSubEmulatorContainer(
         DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:549.0.0-emulators"))
               .withNetwork(NETWORK).withNetworkAliases("pubsub-emulator");

   @BeforeEach
   void beforeEach() throws Exception {
      channel = ManagedChannelBuilder.forTarget(emulatorContainer.getEmulatorEndpoint()).usePlaintext().build();
      channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
      credentialsProvider = NoCredentialsProvider.create();
      SubscriptionAdminSettings subscriptionAdminSettings = SubscriptionAdminSettings.newBuilder()
            .setTransportChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build();
      subscriptionAdminClient = SubscriptionAdminClient.create(subscriptionAdminSettings);
      messages = new ArrayList<>();
   }

   @AfterEach
   void afterEach() {
      if (channel != null) {
         channel.shutdown();
      }
   }

   @Test
   void testProduceMockMessages() throws Exception {
      // Arrange.
      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0.yaml"));

      // Prepare some event messages.
      EventMessage aliceEvent = new EventMessage();
      aliceEvent.setName("Alice");
      aliceEvent.setMediaType("application/json");
      aliceEvent.setContent("{\"fullName\": \"Alice\", \"email\": \"alice@acme.com\", \"age\": 30}");
      EventMessage bobEvent = new EventMessage();
      bobEvent.setName("Bob");
      bobEvent.setMediaType("application/json");
      bobEvent.setContent("{\"fullName\": \"Bod\", \"email\": \"bod@acme.com\", \"age\": 35}");

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("d3d5a3ed-13bf-493f-a06d-bf93392f420b");
      service.setName("User signed-up API");
      service.setVersion("0.3.0");
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
      GooglePubSubProducerManager pubSubProducerManager = new GooglePubSubProducerManager();
      pubSubProducerManager.project = "test-project";
      pubSubProducerManager.emulatorHostPort = emulatorContainer.getEmulatorEndpoint();
      pubSubProducerManager.create();

      ProducerManager producerManager = new ProducerManager(mockRepository, schemaRegistry, null, null, null, null,
            pubSubProducerManager, null, null);

      // Act a 1st time to ensure topic creation before starting subscriber.
      producerManager.produceGooglePubSubMockMessages(mockDefinition);

      // Start subscriber to consume messages.
      Subscriber subscriber = startConsumingMessagesFromTopic("test-project", "UsersignedupAPI-0.3.0-user-signedup");
      subscriber.startAsync().awaitRunning();

      // Act a 2nd time to produce messages while subscriber is active.
      producerManager.produceGooglePubSubMockMessages(mockDefinition);

      Thread.sleep(2000L);
      subscriber.stopAsync();

      // Assert.
      assertFalse(messages.isEmpty());
      assertEquals(2, messages.size());

      for (String message : messages) {
         assertTrue("{\"fullName\": \"Alice\", \"email\": \"alice@acme.com\", \"age\": 30}".equals(message)
               || "{\"fullName\": \"Bod\", \"email\": \"bod@acme.com\", \"age\": 35}".equals(message));
      }
   }

   private Subscriber startConsumingMessagesFromTopic(String projectId, String topicId) {

      String subscriptionId = "test-subscription";
      SubscriptionName subscriptionName = SubscriptionName.of(projectId, subscriptionId);

      subscriptionAdminClient.createSubscription(subscriptionName, TopicName.of(projectId, topicId),
            PushConfig.getDefaultInstance(), 10);

      MessageReceiver receiver = (message, consumer) -> {
         String messageData = message.getData().toString(StandardCharsets.UTF_8);
         this.messages.add(messageData);
         consumer.ack();
      };

      return Subscriber
            .newBuilder(ProjectSubscriptionName.of(subscriptionName.getProject(), subscriptionName.getSubscription()),
                  receiver)
            .setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build();
   }
}
