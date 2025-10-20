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
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

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
 * Amazon WS SQS implementation of producer for async event messages.
 * @author laurent
 */
@ApplicationScoped
public class AmazonSQSProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   private SqsClient sqsClient;

   private final ConcurrentHashMap<String, String> queueUrls = new ConcurrentHashMap<>();

   AwsCredentialsProvider credentialsProvider;

   @ConfigProperty(name = "amazonsqs.region")
   String region;

   @ConfigProperty(name = "amazonsqs.credentials-type")
   AmazonCredentialsProviderType credentialsType;

   @ConfigProperty(name = "amazonsqs.credentials-profile-name")
   String credentialsProfileName;

   @ConfigProperty(name = "amazonsqs.credentials-profile-location")
   String credentialsProfileLocation;

   @ConfigProperty(name = "amazonsqs.endpoint-override")
   Optional<URI> endpointOverride;

   /**
    * Initialize the AWS SQS connection post construction.
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

         // Now create the SQS client with credential provider.
         SqsClientBuilder sqsClientBuilder = SqsClient.builder().region(Region.of(region))
               .credentialsProvider(credentialsProvider);

         if (endpointOverride.filter(URI::isAbsolute).isPresent()) {
            sqsClientBuilder.endpointOverride(endpointOverride.get());
         }
         sqsClient = sqsClientBuilder.build();
      } catch (Exception e) {
         logger.errorf("Cannot connect to AWS SQS region %s", region);
         logger.errorf("Connection exception: %s", e.getMessage());
         throw e;
      }
   }

   /**
    * Publish a message on specified AWS SQS queue.
    * @param queue   The short name of queue within the configured region
    * @param value   The message payload
    * @param headers The headers if any (as rendered by renderEventMessageHeaders() method)
    */
   public void publishMessage(String queue, String value, Map<String, MessageAttributeValue> headers) {
      logger.infof("Publishing on queue {%s}, message: %s ", queue, value);

      try {
         if (queueUrls.get(queue) == null) {
            // Ensure queue exists on AWS by trying to get it in the list.
            ListQueuesRequest listRequest = ListQueuesRequest.builder().queueNamePrefix(queue).maxResults(1).build();
            ListQueuesResponse listResponse = sqsClient.listQueues(listRequest);

            if (listResponse.hasQueueUrls()) {
               logger.infof("Found AWS SQS queue: %s", listResponse.queueUrls().get(0));
               queueUrls.put(queue, listResponse.queueUrls().get(0));
            } else {
               queueUrls.put(queue, createQueueAndGetURL(queue));
            }
         }

         // Retrieve queue URL from local defs and publish message.
         String queueUrl = queueUrls.get(queue);
         sqsClient.sendMessage(mr -> mr.queueUrl(queueUrl).messageBody(value).messageAttributes(headers).build());
      } catch (Throwable t) {
         logger.warnf("Message sending has thrown an exception", t);
         // As it may be relative to queue being deleted and recreated so having different id
         // than previous and thus url, we should clean our cache.
         queueUrls.remove(queue);
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
    * Compute a queue name from async mock definition.
    * @param definition   The mock definition
    * @param eventMessage The event message to get dynamic part from
    * @return The short name of a SQS queue
    */
   public String getQueueName(AsyncMockDefinition definition, EventMessage eventMessage) {
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

   private String createQueueAndGetURL(String queueName) {
      logger.infof("Creating new AWS SQS queue: %s", queueName);
      CreateQueueRequest createQueueRequest = CreateQueueRequest.builder().queueName(queueName).build();
      return sqsClient.createQueue(createQueueRequest).queueUrl();
   }
}
