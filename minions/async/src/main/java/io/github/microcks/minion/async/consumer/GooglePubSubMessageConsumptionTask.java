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

import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.protobuf.Duration;
import com.google.pubsub.v1.ExpirationPolicy;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.Subscription;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a queue on Google Cloud PubSub. Endpoint URL
 * should be specified using the following form:
 * <code>googlepubsub://{projectId}/{topic}[?option1=value1&amp;option2=value2]</code>
 * @author laurent
 */
public class GooglePubSubMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating acceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "googlepubsub://(?<projectId>[a-zA-Z0-9-_]+)/(?<topic>[a-zA-Z0-9-_\\.]+)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   private static final String CLOUD_OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

   private static final String SUBSCRIPTION_PREFIX = "-microcks-test";

   /**
    * emulatorHost is an option key (for the endpoint specification) that allows the test to specify a PubSub emulator
    * to use instead of Google Cloud
    */
   public static final String EMULATOR_HOST_OPTION = "emulatorHost";

   private AsyncTestSpecification specification;

   protected Map<String, String> optionsMap;

   private CredentialsProvider credentialsProvider;
   private TransportChannelProvider channelProvider;

   private SubscriptionName subscriptionName;

   private Subscriber subscriber;

   /**
    * Creates a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public GooglePubSubMessageConsumptionTask(AsyncTestSpecification testSpecification) {
      this.specification = testSpecification;
   }

   /**
    * Convenient static method for checking if this implementation will accept endpoint.
    * @param endpointUrl The endpoint URL to validate
    * @return True if endpointUrl can be used for connecting and consuming on endpoint
    */
   public static boolean acceptEndpoint(String endpointUrl) {
      return endpointUrl != null && endpointUrl.matches(ENDPOINT_PATTERN_STRING);
   }

   @Override
   public List<ConsumedMessage> call() throws Exception {
      // As initialization can be long (circa 1 sec), we have to remove this time from wait time.
      long start = System.currentTimeMillis();
      if (subscriber == null) {
         initializePubSubSubscriber();
      }
      List<ConsumedMessage> messages = new ArrayList<>();

      // Subscribe to PubSub.
      MessageReceiver receiver = (message, consumer) -> {
         logger.info("Received a new PubSub Message: " + message.getData().toStringUtf8());
         // Build a ConsumedMessage from PubSub message.
         ConsumedMessage consumedMessage = new ConsumedMessage();
         consumedMessage.setReceivedAt(System.currentTimeMillis());
         consumedMessage.setPayload(message.getData().toByteArray());
         messages.add(consumedMessage);

         consumer.ack();
      };

      Subscriber.Builder subBuilder = Subscriber
            .newBuilder(ProjectSubscriptionName.of(subscriptionName.getProject(), subscriptionName.getSubscription()),
                  receiver)
            .setCredentialsProvider(credentialsProvider);

      if (channelProvider != null) {
         subBuilder.setChannelProvider(channelProvider);
      }
      // Create a new subscriber for subscription.
      subscriber = subBuilder.build();
      subscriber.startAsync().awaitRunning();

      // Wait and stop async receiver.
      Thread.sleep(specification.getTimeoutMS() - (System.currentTimeMillis() - start));
      subscriber.stopAsync();

      return messages;
   }

   @Override
   public void close() throws IOException {
      if (subscriber != null && subscriber.isRunning()) {
         subscriber.stopAsync();
      }
   }

   /** Initialize Google Pub Sub consumer from test properties. */
   private void initializePubSubSubscriber() throws Exception {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      // Call matcher.find() to be able to use named expressions.
      matcher.find();

      String projectId = matcher.group("projectId");
      String topic = matcher.group("topic");
      String options = matcher.group("options");

      // Parse options if specified.
      if (options != null && !options.isBlank()) {
         optionsMap = ConsumptionTaskCommons.initializeOptionsMap(options);
      }

      if (hasOption(EMULATOR_HOST_OPTION)) {
         credentialsProvider = NoCredentialsProvider.create();
         ManagedChannel channel = ManagedChannelBuilder.forTarget(optionsMap.get(EMULATOR_HOST_OPTION)).usePlaintext()
               .build();
         channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));

      } else if (specification.getSecret() != null && specification.getSecret().getToken() != null) {
         // Build credential provider from secret token.
         byte[] decode = Base64.getDecoder().decode(specification.getSecret().getToken());
         ByteArrayInputStream is = new ByteArrayInputStream(decode);
         credentialsProvider = FixedCredentialsProvider
               .create(GoogleCredentials.fromStream(is).createScoped(CLOUD_OAUTH_SCOPE));
      } else {
         credentialsProvider = NoCredentialsProvider.create();
      }

      // Ensure connection is possible and subscription exists.
      TopicName topicName = TopicName.of(projectId, topic);
      subscriptionName = SubscriptionName.of(projectId, topic + SUBSCRIPTION_PREFIX);

      SubscriptionAdminSettings.Builder sasBuilder = SubscriptionAdminSettings.newBuilder()
            .setCredentialsProvider(credentialsProvider);
      if (channelProvider != null) {
         sasBuilder.setTransportChannelProvider(channelProvider);
      }
      SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(sasBuilder.build());

      try {
         subscriptionAdminClient.getSubscription(subscriptionName);
      } catch (NotFoundException nfe) {
         logger.infof("Subscription {%s} does not exist yet, creating it", subscriptionName);

         // Customize subscription to avoid retention and let google auto-cleanup it.
         // Put the durations to the minimum values accepted by Google cloud.
         Subscription subscriptionRequest = Subscription.newBuilder().setName(subscriptionName.toString())
               .setTopic(topicName.toString()).setPushConfig(PushConfig.getDefaultInstance()).setAckDeadlineSeconds(10)
               .setRetainAckedMessages(false).setMessageRetentionDuration(Duration.newBuilder().setSeconds(600).build())
               .setExpirationPolicy(
                     ExpirationPolicy.newBuilder().setTtl(Duration.newBuilder().setSeconds(24 * 3600L)).build())
               .setEnableMessageOrdering(false).setEnableExactlyOnceDelivery(false).build();
         subscriptionAdminClient.createSubscription(subscriptionRequest);
      } finally {
         subscriptionAdminClient.close();
      }
   }

   /**
    * Safe method for checking if an option has been set.
    * @param optionKey Check if that option is available in options map.
    * @return true if option is present, false if undefined.
    */
   protected boolean hasOption(String optionKey) {
      if (optionsMap != null) {
         return optionsMap.containsKey(optionKey);
      }
      return false;
   }
}
