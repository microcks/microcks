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

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.Topic;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import java.net.URI;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Amazon WS SNS implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class AmazonSNSProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private SnsClient snsClient;

   private final ConcurrentHashMap<String, String> topicArns = new ConcurrentHashMap<>();

   AwsCredentialsProvider credentialsProvider;

   @ConfigProperty(name = "amazonsns.region")
   String region;

   @ConfigProperty(name = "amazonsns.credentials-type")
   AmazonCredentialsProviderType credentialsType;

   @ConfigProperty(name = "amazonsns.credentials-profile-name")
   String credentialsProfileName;

   @ConfigProperty(name = "amazonsns.credentials-profile-location")
   String credentialsProfileLocation;

   @ConfigProperty(name = "amazonsns.endpoint-override")
   Optional<URI> endpointOverride;

   /**
    * Initialize the AWS SNS connection post construction.
    * @throws Exception If connection to SQS cannot be done.
    */
   @PostConstruct
   public void create() throws Exception {
      try {
         switch (credentialsType) {
            case ENV_VARIABLE:
               AwsCredentialsProvider[] credentialsProviders = new AwsCredentialsProvider[] {
                     SystemPropertyCredentialsProvider.create(), EnvironmentVariableCredentialsProvider.create() };
               credentialsProvider = AwsCredentialsProviderChain.builder().reuseLastProviderEnabled(true)
                     .credentialsProviders(credentialsProviders).build();
               break;
            case PROFILE:
               if (credentialsProfileLocation != null && !credentialsProfileLocation.isEmpty()) {
                  ProfileFile profileFile = ProfileFile.builder().type(ProfileFile.Type.CREDENTIALS)
                        .content(Paths.get(credentialsProfileLocation)).build();

                  credentialsProvider = ProfileCredentialsProvider.builder().profileFile(profileFile)
                        .profileName(credentialsProfileName).build();
               }
               break;
            case WEB_IDENTITY:
               credentialsProvider = WebIdentityTokenFileCredentialsProvider.builder()
                     .asyncCredentialUpdateEnabled(true).build();
               break;
            case POD_IDENTITY:
               credentialsProvider = ContainerCredentialsProvider.builder().asyncCredentialUpdateEnabled(true).build();
               break;
         }

         // Default if not set.
         if (credentialsProvider == null) {
            credentialsProvider = DefaultCredentialsProvider.create();
         }

         // Now create the SNS client with credential provider.
         SnsClientBuilder snsClientBuilder = SnsClient.builder().region(Region.of(region))
               .credentialsProvider(credentialsProvider);

         if (endpointOverride.filter(URI::isAbsolute).isPresent()) {
            snsClientBuilder.endpointOverride(endpointOverride.get());
         }

         snsClient = snsClientBuilder.build();
      } catch (Exception e) {
         logger.errorf("Cannot connect to AWS SNS region %s", region);
         logger.errorf("Connection exception: %s", e.getMessage());
         throw e;
      }
   }

   /**
    * Publish a message on specified AWS SNS topic.
    * @param topic   The short name of topic within the configured region
    * @param value   The message payload
    * @param headers The headers if any (as rendered by renderEventMessageHeaders() method)
    */
   public void publishMessage(String topic, String value, Map<String, MessageAttributeValue> headers) {
      logger.infof("Publishing on topic {%s}, message: %s ", topic, value);
      try {
         if (topicArns.get(topic) == null) {
            boolean exists = false;
            // Ensure topic exists on AWS by trying to get it in the list.
            ListTopicsRequest listRequest = ListTopicsRequest.builder().build();
            ListTopicsResponse listResponse = snsClient.listTopics(listRequest);

            if (listResponse.hasTopics() && !listResponse.topics().isEmpty()) {
               logger.infof("listResponse.hasTopics(): %d", listResponse.topics().size());
               for (Topic topicTopic : listResponse.topics()) {
                  logger.infof("Found AWS SNS topic: %s", topicTopic.toString());
                  if (topicTopic.topicArn().endsWith(":" + topic)) {
                     topicArns.put(topic, topicTopic.topicArn());
                     exists = true;
                     break;
                  }
               }
            }

            if (!exists) {
               topicArns.put(topic, createTopicAndGetArn(topic));
            }
         }

         // Retrieve topic ARN from local defs and publish message.
         String topicArn = topicArns.get(topic);
         snsClient.publish(pr -> pr.topicArn(topicArn).message(value).messageAttributes(headers).build());
      } catch (Throwable t) {
         logger.warnf("Message sending has thrown an exception", t);
         // As it may be relative to queue being deleted and recreated so having different id
         // than previous and thus url, we should clean our cache.
         topicArns.remove(topic);
      }
   }

   /**
    * Render Microcks headers using the template engine.
    * @param engine  The template engine to reuse (because we do not want to initialize and manage a context at the
    *                AmazonSQSProducerManager level.)
    * @param headers The Microcks event message headers definition.
    * @return A map of rendered headers of Amazon SQS message sender.
    */
   public Map<String, MessageAttributeValue> renderEventMessageHeaders(TemplateEngine engine, Set<Header> headers) {
      if (headers != null && !headers.isEmpty()) {
         return headers.stream().collect(Collectors.toMap(Header::getName, header -> {
            String firstValue = header.getValues().stream().findFirst().get();
            String finaleValue = firstValue;
            if (firstValue.contains(TemplateEngine.DEFAULT_EXPRESSION_PREFIX)) {
               try {
                  finaleValue = engine.getValue(firstValue);
               } catch (Throwable t) {
                  logger.error("Failing at evaluating template " + firstValue, t);
               }
            }
            return MessageAttributeValue.builder().stringValue(finaleValue).dataType("String").build();
         }));
      }
      return null;
   }

   /**
    * Compute a topic name from async mock definition.
    * @param definition   The mock definition
    * @param eventMessage The event message to get dynamic part from
    * @return The short name of a SNS topic
    */
   public String getTopicName(AsyncMockDefinition definition, EventMessage eventMessage) {
      logger.debugf("AsyncAPI Operation {%s}", definition.getOperation().getName());

      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "");
      serviceName = serviceName.replace("-", "");

      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" ", "");
      versionName = versionName.replace(".", "");

      // Produce operation name part of topic name.
      String operationName = ProducerManager.getDestinationOperationPart(definition.getOperation(), eventMessage);

      // Aggregate the 3 parts using '-' as delimiter.
      return serviceName + "-" + versionName + "-" + operationName.replace("/", "-");
   }

   private String createTopicAndGetArn(String topicName) {
      logger.infof("Creating new AWS SNS topic: %s", topicName);
      CreateTopicRequest createTopicRequest = CreateTopicRequest.builder().name(topicName).build();
      return snsClient.createTopic(createTopicRequest).topicArn();
   }
}
