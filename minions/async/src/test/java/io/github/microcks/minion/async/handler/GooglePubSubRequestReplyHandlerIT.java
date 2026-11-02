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
import io.github.microcks.minion.async.producer.GooglePubSubProducerManager;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PubsubMessage;
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

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link GooglePubSubRequestReplyHandler} and {@link RequestReplyHandlerManager} classes.
 * @author laurent
 */
@Testcontainers
class GooglePubSubRequestReplyHandlerIT {

   private static final String PROJECT_ID = "test-project";
   private static final String REQUEST_TOPIC = "UserAccountService-1.0.0-user-signup";
   private static final String REPLY_TOPIC = "UserAccountService-1.0.0-user-signup-reply";
   private static final Network NETWORK = Network.newNetwork();

   @Container
   private static final PubSubEmulatorContainer emulatorContainer = new PubSubEmulatorContainer(
         DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:549.0.0-emulators"))
               .withNetwork(NETWORK).withNetworkAliases("pubsub-emulator");

   private ManagedChannel channel;
   private TransportChannelProvider channelProvider;
   private NoCredentialsProvider credentialsProvider;

   private GooglePubSubRequestReplyHandler handler;
   private GooglePubSubProducerManager pubSubProducerManager;

   @BeforeEach
   void beforeEach() throws Exception {
      channel = ManagedChannelBuilder.forTarget(emulatorContainer.getEmulatorEndpoint()).usePlaintext().build();
      channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
      credentialsProvider = NoCredentialsProvider.create();
   }

   @AfterEach
   void afterEach() {
      if (handler != null && handler.isRunning()) {
         handler.stop();
      }
      if (channel != null) {
         channel.shutdown();
      }
   }

   @Test
   void testRequestReplyHandlerWithJsonMessages() throws Exception {
      // Arrange.
      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0-reply.yaml"));

      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("UserSignupRequest");
      requestMessage.setMediaType("application/json");
      requestMessage.setContent("""
            {"email": "john.doe@example.com", "username": "johndoe", "fullName": "John Doe"}""");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("reply-msg-1");
      replyMessage.setName("UserSignupReply");
      replyMessage.setMediaType("application/json");
      replyMessage.setContent("""
            {"userId": "usr-12345", "status": "success", "message": "User account created successfully"}""");

      requestMessage.setReplyId(replyMessage.getId());

      Service service = new Service();
      service.setId("user-account-service-id");
      service.setName("User Account Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation signupOperation = new Operation();
      signupOperation.setName("RECEIVE user/signup");
      signupOperation.addResourcePath(REQUEST_TOPIC);

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress(REPLY_TOPIC);
      signupOperation.setReply(replyInfo);

      service.setOperations(List.of(signupOperation));

      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signupOperation,
            List.of(requestMessage, replyMessage));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("user-account-service-id",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);

      pubSubProducerManager = createProducerManager();

      Binding pubsubBinding = new Binding();
      pubsubBinding.setType(BindingType.GOOGLEPUBSUB);

      GooglePubSubRequestReplyHandlerFactory factory = createFactory(pubSubProducerManager, schemaRegistry);
      handler = factory.createHandler(mockDefinition, pubsubBinding);

      handler.start();

      // Wait for subscriber to be ready.
      Thread.sleep(2000);

      // Act - publish a request message directly on the request topic.
      publishRequestMessage(REQUEST_TOPIC, """
            {"email": "john.doe@example.com", "username": "johndoe", "fullName": "John Doe"}""", Map.of());

      // Consume reply messages from reply topic.
      List<String> replyMessages = consumeMessagesFromTopic(REPLY_TOPIC, 8000);

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

      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("UserSignupRequest");
      requestMessage.setMediaType("application/json");
      requestMessage.setContent("""
            {"email": "john.doe@example.com", "username": "johndoe", "fullName": "John Doe"}""");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("reply-msg-1");
      replyMessage.setName("UserSignupReply");
      replyMessage.setMediaType("application/json");
      replyMessage.setContent("""
            {"userId": "usr-12345", "status": "success", "message": "User account created successfully"}""");

      requestMessage.setReplyId(replyMessage.getId());

      Service service = new Service();
      service.setId("user-account-service-id-2");
      service.setName("User Account Service Two");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation signupOperation = new Operation();
      signupOperation.setName("RECEIVE user/signup2");
      signupOperation.addResourcePath("UserAccountServiceTwo-1.0.0-user-signup2");

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress("UserAccountServiceTwo-1.0.0-user-signup2-reply");
      signupOperation.setReply(replyInfo);

      service.setOperations(List.of(signupOperation));

      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signupOperation,
            List.of(requestMessage, replyMessage));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("user-account-service-id-2",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);

      pubSubProducerManager = createProducerManager();

      Binding pubsubBinding = new Binding();
      pubsubBinding.setType(BindingType.GOOGLEPUBSUB);

      GooglePubSubRequestReplyHandlerFactory factory = createFactory(pubSubProducerManager, schemaRegistry);
      handler = factory.createHandler(mockDefinition, pubsubBinding);

      handler.start();
      Thread.sleep(2000);

      // Act - send a request message that does not match expected content.
      publishRequestMessage("UserAccountServiceTwo-1.0.0-user-signup2", """
            {"email": "test@example.com", "username": "johndoe", "fullName": "John Doe"}""", Map.of());

      // Assert.
      List<String> replyMessages = consumeMessagesFromTopic("UserAccountServiceTwo-1.0.0-user-signup2-reply", 4000);
      assertTrue(replyMessages.isEmpty(), "Should not have received any reply message");
   }

   @Test
   void testHandlerStartStop() throws Exception {
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

      requestMessage.setReplyId(replyMessage.getId());

      Service service = new Service();
      service.setId("test-service-id");
      service.setName("Test Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("RECEIVE test/op");
      operation.addResourcePath("test-op");

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress("test-reply");
      operation.setReply(replyInfo);

      service.setOperations(List.of(operation));

      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, operation,
            List.of(requestMessage, replyMessage));

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("test-service-id", asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);

      pubSubProducerManager = createProducerManager();

      Binding pubsubBinding = new Binding();
      pubsubBinding.setType(BindingType.GOOGLEPUBSUB);

      GooglePubSubRequestReplyHandlerFactory factory = createFactory(pubSubProducerManager, schemaRegistry);
      handler = factory.createHandler(mockDefinition, pubsubBinding);

      assertFalse(handler.isRunning(), "Handler should not be running initially");

      handler.start();
      Thread.sleep(500);
      assertTrue(handler.isRunning(), "Handler should be running after start");

      handler.stop();
      Thread.sleep(500);
      assertFalse(handler.isRunning(), "Handler should not be running after stop");
   }

   private void publishRequestMessage(String topic, String message, Map<String, String> attributes) throws Exception {
      TopicName topicName = TopicName.of(PROJECT_ID, topic);

      // Ensure request topic exists before publishing (handler may create the subscription concurrently).
      com.google.cloud.pubsub.v1.TopicAdminSettings.Builder tasBuilder = com.google.cloud.pubsub.v1.TopicAdminSettings
            .newBuilder().setCredentialsProvider(credentialsProvider).setTransportChannelProvider(channelProvider);
      try (com.google.cloud.pubsub.v1.TopicAdminClient topicAdminClient = com.google.cloud.pubsub.v1.TopicAdminClient
            .create(tasBuilder.build())) {
         try {
            topicAdminClient.getTopic(topicName);
         } catch (com.google.api.gax.rpc.NotFoundException nfe) {
            topicAdminClient.createTopic(topicName);
         }
      }

      Publisher publisher = Publisher.newBuilder(topicName).setChannelProvider(channelProvider)
            .setCredentialsProvider(credentialsProvider).build();
      try {
         PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(ByteString.copyFromUtf8(message))
               .putAllAttributes(attributes).build();
         publisher.publish(pubsubMessage).get();
      } finally {
         publisher.shutdown();
      }
   }

   private List<String> consumeMessagesFromTopic(String topic, long timeoutMs) throws Exception {
      TopicName topicName = TopicName.of(PROJECT_ID, topic);
      SubscriptionName subscriptionName = SubscriptionName.of(PROJECT_ID, topic + "-test-consumer");

      // Ensure the (reply) topic exists before subscribing: it may not have been created yet if the handler
      // did not publish on it (e.g. lazily created on first reply message by the producer manager).
      com.google.cloud.pubsub.v1.TopicAdminSettings.Builder tasBuilder = com.google.cloud.pubsub.v1.TopicAdminSettings
            .newBuilder().setCredentialsProvider(credentialsProvider).setTransportChannelProvider(channelProvider);
      try (com.google.cloud.pubsub.v1.TopicAdminClient topicAdminClient = com.google.cloud.pubsub.v1.TopicAdminClient
            .create(tasBuilder.build())) {
         try {
            topicAdminClient.getTopic(topicName);
         } catch (com.google.api.gax.rpc.NotFoundException nfe) {
            topicAdminClient.createTopic(topicName);
         }
      }

      SubscriptionAdminSettings.Builder sasBuilder = SubscriptionAdminSettings.newBuilder()
            .setCredentialsProvider(credentialsProvider).setTransportChannelProvider(channelProvider);
      try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(sasBuilder.build())) {
         try {
            subscriptionAdminClient.getSubscription(subscriptionName);
         } catch (com.google.api.gax.rpc.NotFoundException nfe) {
            subscriptionAdminClient.createSubscription(subscriptionName, topicName, PushConfig.getDefaultInstance(),
                  10);
         }
      }

      List<String> messages = new CopyOnWriteArrayList<>();
      MessageReceiver receiver = (message, consumer) -> {
         messages.add(message.getData().toStringUtf8());
         consumer.ack();
      };

      Subscriber subscriber = Subscriber
            .newBuilder(ProjectSubscriptionName.of(subscriptionName.getProject(), subscriptionName.getSubscription()),
                  receiver)
            .setChannelProvider(channelProvider).setCredentialsProvider(credentialsProvider).build();
      subscriber.startAsync().awaitRunning();

      long start = System.currentTimeMillis();
      while (messages.isEmpty() && System.currentTimeMillis() - start < timeoutMs) {
         Thread.sleep(200);
      }
      subscriber.stopAsync();

      return new ArrayList<>(messages);
   }

   private GooglePubSubProducerManager createProducerManager() throws Exception {
      GooglePubSubProducerManager manager = new GooglePubSubProducerManager();
      // Fields are package-private on the producer manager, so use reflection from this test package.
      setField(manager, "project", PROJECT_ID);
      setField(manager, "emulatorHostPort", emulatorContainer.getEmulatorEndpoint());
      manager.create();
      return manager;
   }

   private static void setField(Object target, String fieldName, Object value) throws Exception {
      java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
   }

   private GooglePubSubRequestReplyHandlerFactory createFactory(GooglePubSubProducerManager producerManager,
         SchemaRegistry schemaRegistry) {
      return new GooglePubSubRequestReplyHandlerFactory(producerManager, schemaRegistry);
   }
}
