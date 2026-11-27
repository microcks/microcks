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

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link GooglePubSubMessageConsumptionTask} class.
 * @author laurent
 */
@Testcontainers
class GooglePubSubMessageConsumptionTaskIT {

   private static final Network NETWORK = Network.newNetwork();
   private static final String PROJECT_ID = "my-project";
   private static final String TOPIC_NAME = "test-topic";
   private static final String TEXT_MESSAGE_TEMPLATE = "{\"greeting\": \"Hello World!\", \"number\": %s}";

   @Container
   private static final PubSubEmulatorContainer emulatorContainer = new PubSubEmulatorContainer(
         DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:549.0.0-emulators"))
               .withNetwork(NETWORK).withNetworkAliases("pubsub-emulator");

   private ManagedChannel channel;
   private TransportChannelProvider channelProvider;
   private NoCredentialsProvider credentialsProvider;

   @BeforeEach
   void beforeEach() throws Exception {
      // Ensure the topic exists.
      String hostport = emulatorContainer.getEmulatorEndpoint();
      channel = ManagedChannelBuilder.forTarget(hostport).usePlaintext().build();
      channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
      credentialsProvider = NoCredentialsProvider.create();

      // Create the topic.
      TopicAdminSettings topicAdminSettings = TopicAdminSettings.newBuilder()
            .setTransportChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build();
      try (TopicAdminClient topicAdminClient = TopicAdminClient.create(topicAdminSettings)) {
         TopicName topicName = TopicName.of(PROJECT_ID, TOPIC_NAME);
         topicAdminClient.createTopic(topicName);
      }
   }

   @AfterEach
   void afterEach() {
      channel.shutdown();
   }

   @Test
   void shouldReceiveMessageOnTopicCorrectly() throws Exception {
      // Arrange.
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(2000L);
      asyncTestSpecification.setEndpointUrl("googlepubsub://" + PROJECT_ID + "/" + TOPIC_NAME + "?emulatorHost="
            + emulatorContainer.getEmulatorEndpoint());

      List<ConsumedMessage> messages = null;

      // Act.
      try (GooglePubSubMessageConsumptionTask pubsubConsumptionTask = new GooglePubSubMessageConsumptionTask(
            asyncTestSpecification); ExecutorService executorService = Executors.newFixedThreadPool(2);) {
         List<Future<List<ConsumedMessage>>> outputs = executorService
               .invokeAll(List.of(new Callable<List<ConsumedMessage>>() {
                  @Override
                  public List<ConsumedMessage> call() throws Exception {
                     // Wait a bit so that consumption task has actually start.
                     await().during(600, TimeUnit.MILLISECONDS).until(() -> true);
                     sendTextMessagesOnTopic(1);
                     return Collections.emptyList();
                  }
               }, pubsubConsumptionTask), asyncTestSpecification.getTimeoutMS() + 1000L, TimeUnit.MILLISECONDS);

         messages = outputs.get(1).get();
      }


      // Assert.
      Assertions.assertFalse(messages.isEmpty());
      Assertions.assertEquals(1, messages.size());
      ConsumedMessage message = messages.get(0);
      Assertions.assertEquals(TEXT_MESSAGE_TEMPLATE.formatted(0),
            new String(message.getPayload(), StandardCharsets.UTF_8));
   }

   private void sendTextMessagesOnTopic(int numberOfMessages) {
      try {
         // Create the publisher.
         Publisher publisher = Publisher.newBuilder(TopicName.of(PROJECT_ID, TOPIC_NAME))
               .setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build();

         for (int i = 0; i < numberOfMessages; i++) {
            PubsubMessage message = PubsubMessage.newBuilder()
                  .setData(ByteString.copyFromUtf8(TEXT_MESSAGE_TEMPLATE.formatted(i))).build();
            publisher.publish(message);
            await().pollDelay(250, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(true));
         }
      } catch (Exception e) {
         channel.shutdown();
         fail("Exception while connecting to Emulator PubSub topic", e);
      }
   }
}
