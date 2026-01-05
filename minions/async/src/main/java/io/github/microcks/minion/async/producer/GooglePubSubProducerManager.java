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
import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.TemplateEngine;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.api.gax.core.CredentialsProvider;
import com.google.api.gax.core.ExecutorProvider;
import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.core.InstantiatingExecutorProvider;
import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.pubsub.v1.Publisher;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.TopicName;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Google Cloud PubSub implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class GooglePubSubProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private static final String CLOUD_OAUTH_SCOPE = "https://www.googleapis.com/auth/cloud-platform";

   /**
    * As {@link Publisher} is bound to topic, default would be to create it at each invocation. We'll use this cache,
    * that enforces only one {@link Publisher} per PubSub topic exists.
    */
   private final ConcurrentHashMap<String, Publisher> publishers = new ConcurrentHashMap<>();

   /**
    * As {@link Publisher} is by default associated to its own executor, we have to override this to avoid wasting
    * resources. As the push of mock messages is sequential, 1 thread is enough.
    */
   private final ExecutorProvider executorProvider = InstantiatingExecutorProvider.newBuilder()
         .setExecutorThreadCount(1).build();

   CredentialsProvider credentialsProvider;
   TransportChannelProvider channelProvider;

   @ConfigProperty(name = "googlepubsub.project")
   String project;

   @ConfigProperty(name = "googlepubsub.service-account-location")
   String serviceAccountLocation;

   String emulatorHostPort;

   /**
    * Initialize the PubSub connection post construction.
    * @throws Exception If connection to PubSub cannot be done.
    */
   @PostConstruct
   public void create() throws Exception {
      try {
         String hostPort = emulatorHostPort != null ? emulatorHostPort : System.getenv("PUBSUB_EMULATOR_HOST");
         if (hostPort != null && !hostPort.isEmpty()) {
            logger.infof("Using Google PubSub emulator at %s", hostPort);

            ManagedChannel channel = ManagedChannelBuilder.forTarget(hostPort).usePlaintext().build();
            channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
            credentialsProvider = NoCredentialsProvider.create();

         } else if (serviceAccountLocation != null && !serviceAccountLocation.isEmpty()) {

            FileInputStream is = new FileInputStream(serviceAccountLocation);
            credentialsProvider = FixedCredentialsProvider
                  .create(GoogleCredentials.fromStream(is).createScoped(CLOUD_OAUTH_SCOPE));
         } else {
            credentialsProvider = NoCredentialsProvider.create();
         }
      } catch (Exception e) {
         logger.errorf("Cannot read Google Cloud credentials %s", serviceAccountLocation);
         throw e;
      }
   }

   /**
    * Publish a message on specified PubSub topic.
    * @param topic   The short name of topic within the configured project
    * @param value   The message payload
    * @param headers The headers if any (as rendered by renderEventMessageHeaders() method)
    */
   public void publishMessage(String topic, String value, Map<String, String> headers) {
      logger.infof("Publishing on topic {%s}, message: %s ", topic, value);

      try {
         if (publishers.get(topic) == null) {
            // Ensure topic exists on PubSub.
            TopicName topicName = TopicName.of(project, topic);

            TopicAdminSettings.Builder tasBuilder = TopicAdminSettings.newBuilder()
                  .setCredentialsProvider(credentialsProvider);
            if (channelProvider != null) {
               tasBuilder.setTransportChannelProvider(channelProvider);
            }

            TopicAdminClient topicAdminClient = TopicAdminClient.create(tasBuilder.build());

            ensureTopicExists(topicAdminClient, topicName);
         }

         // Build a message for corresponding publisher.
         Publisher publisher = getPublisher(topic);
         ByteString data = ByteString.copyFromUtf8(value);
         PubsubMessage pubsubMessage = PubsubMessage.newBuilder().setData(data).putAllAttributes(headers).build();

         // Publish the message.
         ApiFuture<String> messageIdFuture = publisher.publish(pubsubMessage);
         ApiFutures.addCallback(messageIdFuture, new ApiFutureCallback<>() {
            // Wait for message submission and log the result
            public void onSuccess(String messageId) {
               logger.debugv("Published with message id {0}", messageId);
            }

            public void onFailure(Throwable t) {
               logger.debugv("Failed to publish: {0}", t);
            }
         }, MoreExecutors.directExecutor());
      } catch (IOException ioe) {
         logger.warnf("Message sending has thrown an exception", ioe);
      }
   }

   /**
    * Render Microcks headers using the template engine.
    * @param engine  The template engine to reuse (because we do not want to initialize and manage a context at the
    *                GooglePubSubProducerManager level.)
    * @param headers The Microcks event message headers definition.
    * @return A map of rendered headers for GCP Publisher.
    */
   public Map<String, String> renderEventMessageHeaders(TemplateEngine engine, Set<Header> headers) {
      if (headers != null && !headers.isEmpty()) {
         return headers.stream().collect(Collectors.toMap(Header::getName, header -> {
            String firstValue = header.getValues().stream().findFirst().get();
            if (firstValue.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
               try {
                  return engine.getValue(firstValue);
               } catch (Exception e) {
                  logger.error("Failing at evaluating template " + firstValue, e);
                  return firstValue;
               }
            }
            return firstValue;
         }));
      }
      return Collections.emptyMap();
   }

   /**
    * Compute a topic name from async mock definition.
    * @param definition   The mock definition
    * @param eventMessage The event message to get dynamic part from
    * @return The short name of a PubSub topic
    */
   public String getTopicName(AsyncMockDefinition definition, EventMessage eventMessage) {
      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");

      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" ", "");

      // Produce operation name part of topic name.
      String operationName = ProducerManager.getDestinationOperationPart(definition.getOperation(), eventMessage);
      operationName = operationName.replace('/', '-');

      // Aggregate the 3 parts using '_' as delimiter.
      return serviceName + "-" + versionName + "-" + operationName;
   }

   private void ensureTopicExists(TopicAdminClient topicAdminClient, TopicName topicName) {
      try {
         topicAdminClient.getTopic(topicName);
      } catch (NotFoundException nfe) {
         logger.infof("Topic {%s} does not exist yet, creating it", topicName);
         topicAdminClient.createTopic(topicName);
      }
   }

   private Publisher getPublisher(String topic) throws IOException {
      try {
         return publishers.computeIfAbsent(topic, this::createPublisher);
      } catch (RuntimeException re) {
         // Just unwrap the underlying IOException...
         throw (IOException) re.getCause();
      }
   }

   private Publisher createPublisher(String topic) {
      try {
         Publisher.Builder pubBuilder = Publisher.newBuilder(TopicName.of(project, topic))
               .setExecutorProvider(executorProvider).setCredentialsProvider(credentialsProvider);
         if (channelProvider != null) {
            pubBuilder = pubBuilder.setChannelProvider(channelProvider);
         }
         return pubBuilder.build();
      } catch (IOException ioe) {
         logger.errorf("IOException while creating a Publisher for topic %s", topic);
         throw new RuntimeException("IOException while creating a Publisher for topic ", ioe);
      }
   }
}
