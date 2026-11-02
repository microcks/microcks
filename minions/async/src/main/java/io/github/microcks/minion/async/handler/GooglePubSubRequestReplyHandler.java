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
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.producer.GooglePubSubProducerManager;
import io.github.microcks.util.el.EvaluableRequest;
import io.github.microcks.util.el.TemplateEngine;
import io.github.microcks.util.el.TemplateEngineFactory;

import com.google.api.gax.rpc.NotFoundException;
import com.google.cloud.pubsub.v1.MessageReceiver;
import com.google.cloud.pubsub.v1.Subscriber;
import com.google.cloud.pubsub.v1.SubscriptionAdminClient;
import com.google.cloud.pubsub.v1.SubscriptionAdminSettings;
import com.google.cloud.pubsub.v1.TopicAdminClient;
import com.google.cloud.pubsub.v1.TopicAdminSettings;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.SubscriptionName;
import com.google.pubsub.v1.TopicName;
import org.eclipse.microprofile.config.Config;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Google Cloud PubSub implementation of request-reply handler. Listens for incoming request messages on a PubSub
 * subscription bound to the operation request topic and sends correlated replies on the reply topic.
 * @author laurent
 */
public class GooglePubSubRequestReplyHandler implements RequestReplyHandler {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** Suffix appended to the request topic name to build a dedicated subscription for this handler. */
   private static final String SUBSCRIPTION_SUFFIX = "-microcks-reply";

   private final AsyncMockDefinition mockDefinition;
   private final Binding binding;
   private final GooglePubSubProducerManager producerManager;
   private final SchemaRegistry schemaRegistry;
   private final Config config;

   private Subscriber subscriber;
   private final AtomicBoolean running = new AtomicBoolean(false);

   /**
    * Create a new Google PubSub request-reply handler.
    *
    * @param mockDefinition  The mock definition containing operation and messages
    * @param binding         The PubSub binding information
    * @param producerManager The producer manager for sending replies
    * @param schemaRegistry  The schema registry for message schemas
    * @param config          The application configuration
    */
   public GooglePubSubRequestReplyHandler(AsyncMockDefinition mockDefinition, Binding binding,
         GooglePubSubProducerManager producerManager, SchemaRegistry schemaRegistry, Config config) {
      this.mockDefinition = mockDefinition;
      this.binding = binding;
      this.producerManager = producerManager;
      this.schemaRegistry = schemaRegistry;
      this.config = config;
   }

   @Override
   public void start() throws Exception {
      if (running.get()) {
         logger.warn("Handler already running");
         return;
      }

      logger.infof("Starting Google PubSub request-reply handler for %s - %s",
            mockDefinition.getOwnerService().getName() + ":" + mockDefinition.getOwnerService().getVersion(),
            mockDefinition.getOperation().getName());

      String requestTopic = getRequestTopic();
      SubscriptionName subscriptionName = ensureSubscriptionExists(requestTopic);

      MessageReceiver receiver = (message, consumer) -> {
         try {
            handleRequest(message.getData().toByteArray(), message.getAttributesMap());
         } catch (Exception e) {
            logger.errorf(e, "Error while handling request message on %s", requestTopic);
         } finally {
            consumer.ack();
         }
      };

      Subscriber.Builder subBuilder = Subscriber
            .newBuilder(ProjectSubscriptionName.of(subscriptionName.getProject(), subscriptionName.getSubscription()),
                  receiver)
            .setCredentialsProvider(producerManager.getCredentialsProvider());
      if (producerManager.getChannelProvider() != null) {
         subBuilder.setChannelProvider(producerManager.getChannelProvider());
      }
      subscriber = subBuilder.build();
      subscriber.startAsync().awaitRunning();

      running.set(true);
      logger.infof("Google PubSub request-reply handler started for %s", requestTopic);
   }

   @Override
   public void stop() {
      if (!running.get()) {
         return;
      }

      logger.infof("Stopping Google PubSub request-reply handler for %s", getRequestTopic());

      running.set(false);
      if (subscriber != null) {
         subscriber.stopAsync();
         try {
            subscriber.awaitTerminated(5, TimeUnit.SECONDS);
         } catch (Exception e) {
            logger.warn("Interrupted while waiting for subscriber to stop", e);
         }
      }

      logger.infof("Google PubSub request-reply handler stopped for %s", getRequestTopic());
   }

   @Override
   public boolean isRunning() {
      return running.get();
   }

   @Override
   public AsyncMockDefinition getMockDefinition() {
      return mockDefinition;
   }

   /** Handle a single request message and send the corresponding reply. */
   private void handleRequest(byte[] rawPayload, Map<String, String> attributes) {
      logger.debugf("Received request message on %s", getRequestTopic());

      String requestBody = new String(rawPayload, StandardCharsets.UTF_8);
      EvaluableRequest evaluableRequest = new EvaluableRequest(requestBody, null);
      evaluableRequest.setHeaders(attributes);

      // Resolve the correlation ID from the request attributes, following the AsyncAPI correlation ID convention.
      String correlationId = CorrelationIdSupport.resolveCorrelationId(attributes);
      logger.debugf("Using correlationId '%s' for this request-reply exchange", correlationId);

      EventMessage replyMessage = findReplyForRequest(evaluableRequest);
      if (replyMessage == null) {
         logger.warnf("No matching reply message found for request, skipping");
         return;
      }

      String replyContent = renderReplyContent(replyMessage, evaluableRequest);
      String replyTopic = getReplyTopic(replyMessage, attributes, requestBody);

      logger.debugf("Sending reply to %s", replyTopic);
      Map<String, String> replyHeaders = buildReplyHeaders(replyMessage, correlationId);
      producerManager.publishMessage(replyTopic, replyContent, replyHeaders);
   }

   /**
    * Find the reply message that corresponds to the incoming request. Matches requests to replies using the replyId
    * field. Request messages have their replyId set to point to their corresponding reply message's ID.
    */
   private EventMessage findReplyForRequest(EvaluableRequest evaluableRequest) {
      EventMessage matchingRequest = mockDefinition.getEventMessages().stream().filter(msg -> msg.getReplyId() != null)
            .filter(msg -> EventData.matchesExpectedMessage(msg, evaluableRequest.getBody())).findFirst().orElse(null);

      if (matchingRequest == null) {
         logger.debugf("No request message definition matches incoming request");
         return null;
      }

      String replyId = matchingRequest.getReplyId();
      return mockDefinition.getEventMessages().stream().filter(msg -> replyId.equals(msg.getId())).findFirst()
            .orElse(null);
   }

   /** Render reply content with any templating. */
   private String renderReplyContent(EventMessage replyMessage, EvaluableRequest evaluableRequest) {
      String content = replyMessage.getContent();

      if (content.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
         logger.debug("Reply message contains dynamic EL expression, rendering it...");
         TemplateEngine engine = TemplateEngineFactory.getTemplateEngine();
         engine.getContext().setVariable("request", evaluableRequest);

         try {
            content = engine.getValue(content);
         } catch (Throwable t) {
            logger.errorf(t, "Failed to evaluate template '%s'", content);
         }
      }

      return content;
   }

   /** Build attributes for reply message, propagating the correlation ID from the request. */
   private Map<String, String> buildReplyHeaders(EventMessage replyMessage, String correlationId) {
      Map<String, String> headers = new HashMap<>();

      // Propagate the correlation ID so consumers can match this reply to their original request.
      headers.put(CorrelationIdSupport.CORRELATION_ID_HEADER, correlationId);

      if (replyMessage.getHeaders() != null) {
         for (Header domainHeader : replyMessage.getHeaders()) {
            if (domainHeader.getValues() != null && !domainHeader.getValues().isEmpty()) {
               headers.put(domainHeader.getName(), domainHeader.getValues().iterator().next());
            }
         }
      }

      return headers;
   }

   /** Get the request topic name. */
   private String getRequestTopic() {
      return producerManager.getTopicName(mockDefinition, mockDefinition.getEventMessages().get(0));
   }

   /**
    * Get the reply topic name, resolving a dynamic {@code addressLocation} runtime expression against the incoming
    * request attributes/payload if specified, otherwise falling back to the static {@code channelAddress}.
    */
   private String getReplyTopic(EventMessage replyMessage, Map<String, String> attributes, String payload) {
      if (mockDefinition.getOperation().getReply() == null) {
         throw new IllegalStateException(
               "Request-reply operation must have reply information, but none found for operation: "
                     + mockDefinition.getOperation().getName());
      }

      String addressLocation = mockDefinition.getOperation().getReply().getAddressLocation();
      if (addressLocation != null) {
         String resolved = RuntimeExpressionResolver.resolve(addressLocation, attributes, payload);
         if (resolved != null && !resolved.isBlank()) {
            logger.debugf("Resolved dynamic reply topic '%s' from addressLocation expression '%s'", resolved,
                  addressLocation);
            return resolved;
         }
         throw new IllegalStateException(
               "Could not resolve reply topic from addressLocation expression: " + addressLocation);
      }

      if (mockDefinition.getOperation().getReply().getChannelAddress() != null) {
         return mockDefinition.getOperation().getReply().getChannelAddress();
      }

      throw new IllegalStateException("Reply information must specify either channelAddress or addressLocation.");
   }

   /** Ensure the request topic and a dedicated subscription for this handler exist, creating them if needed. */
   private SubscriptionName ensureSubscriptionExists(String topic) throws Exception {
      String project = producerManager.getProject();
      TopicName topicName = TopicName.of(project, topic);
      SubscriptionName subscriptionName = SubscriptionName.of(project, topic + SUBSCRIPTION_SUFFIX);

      TopicAdminSettings.Builder tasBuilder = TopicAdminSettings.newBuilder()
            .setCredentialsProvider(producerManager.getCredentialsProvider());
      if (producerManager.getChannelProvider() != null) {
         tasBuilder.setTransportChannelProvider(producerManager.getChannelProvider());
      }
      try (TopicAdminClient topicAdminClient = TopicAdminClient.create(tasBuilder.build())) {
         try {
            topicAdminClient.getTopic(topicName);
         } catch (NotFoundException nfe) {
            logger.infof("Topic {%s} does not exist yet, creating it", topicName);
            topicAdminClient.createTopic(topicName);
         }
      }

      SubscriptionAdminSettings.Builder sasBuilder = SubscriptionAdminSettings.newBuilder()
            .setCredentialsProvider(producerManager.getCredentialsProvider());
      if (producerManager.getChannelProvider() != null) {
         sasBuilder.setTransportChannelProvider(producerManager.getChannelProvider());
      }
      try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(sasBuilder.build())) {
         try {
            subscriptionAdminClient.getSubscription(subscriptionName);
         } catch (NotFoundException nfe) {
            logger.infof("Subscription {%s} does not exist yet, creating it", subscriptionName);
            subscriptionAdminClient.createSubscription(subscriptionName, topicName, PushConfig.getDefaultInstance(),
                  10);
         }
      }

      return subscriptionName;
   }
}
