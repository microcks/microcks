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

import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncTestSpecification;

import org.jboss.logging.Logger;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sns.model.Topic;
import software.amazon.awssdk.services.sns.model.UnsubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.DeleteQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An implementation of <code>MessageConsumptionTask</code> that consumes a queue on Amazon Simple Notification Service
 * (SNS). Endpoint URL should be specified using the following form:
 * <code>sns://{region}/{topic}[?option1=value1&amp;option2=value2]</code>
 * @author laurent
 */
public class AmazonSNSMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating acceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "sns://(?<region>[a-zA-Z0-9-]+)/(?<topic>[a-zA-Z0-9-_\\.]+)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   /** The endpoint URL option representing AWS endpoint override URL. */
   public static final String OVERRIDE_URL_OPTION = "overrideUrl";

   private static final String SUBSCRIPTION_PREFIX = "-microcks-test";

   private AsyncTestSpecification specification;

   protected String topic;

   protected Map<String, String> optionsMap;

   private AwsCredentialsProvider credentialsProvider;

   private SnsClient snsClient;

   private SqsClient sqsClient;

   private QueueCoordinates queue;

   private String subscriptionArn;

   /**
    * Creates a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public AmazonSNSMessageConsumptionTask(AsyncTestSpecification testSpecification) {
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
      long startTime = System.currentTimeMillis();
      if (snsClient == null) {
         initializeSubscription();
      }
      List<ConsumedMessage> messages = new ArrayList<>();

      long timeoutTime = startTime + specification.getTimeoutMS();
      while (System.currentTimeMillis() - startTime < specification.getTimeoutMS()) {
         // Start polling/receiving messages with a max wait time and a max number.
         ReceiveMessageRequest messageRequest = ReceiveMessageRequest.builder().queueUrl(queue.url())
               .maxNumberOfMessages(10).waitTimeSeconds((int) (timeoutTime - System.currentTimeMillis()) / 1000)
               .build();

         List<Message> receivedMessages = sqsClient.receiveMessage(messageRequest).messages();

         for (Message receivedMessage : receivedMessages) {
            // Build a ConsumedMessage from SQS message.
            ConsumedMessage message = new ConsumedMessage();
            message.setReceivedAt(System.currentTimeMillis());
            message.setHeaders(buildHeaders(receivedMessage.messageAttributes()));
            message.setPayload(receivedMessage.body().getBytes(StandardCharsets.UTF_8));
            messages.add(message);
         }
      }
      return messages;
   }

   @Override
   public void close() throws IOException {
      // Remove subscription on SNS side, then delete SQS queue.
      snsClient.unsubscribe(UnsubscribeRequest.builder().subscriptionArn(subscriptionArn).build());
      sqsClient.deleteQueue(DeleteQueueRequest.builder().queueUrl(queue.url()).build());
      // Close both clients.
      if (snsClient != null) {
         snsClient.close();
      }
      if (sqsClient != null) {
         sqsClient.close();
      }
   }

   /** Initialize Amazon SNS/SQS clients and SQS subscription from test properties. */
   private void initializeSubscription() throws Exception {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      // Call matcher.find() to be able to use named expressions.
      matcher.find();

      String region = matcher.group("region");
      topic = matcher.group("topic");
      String options = matcher.group("options");

      // Parse options if specified.
      if (options != null && !options.isBlank()) {
         optionsMap = ConsumptionTaskCommons.initializeOptionsMap(options);
      }

      // Build credential provider from secret username and password if any.
      if (specification.getSecret() != null && specification.getSecret().getUsername() != null
            && specification.getSecret().getPassword() != null) {
         String accessKeyId = specification.getSecret().getUsername();
         String secretKeyId = specification.getSecret().getPassword();
         credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretKeyId));
      } else {
         credentialsProvider = DefaultCredentialsProvider.create();
      }

      // Build the SNS client with provided region and credentials.
      SnsClientBuilder snsClientBuilder = SnsClient.builder().region(Region.of(region))
            .credentialsProvider(credentialsProvider);

      // Build the SQS client with provided region and credentials.
      SqsClientBuilder sqsClientBuilder = SqsClient.builder().region(Region.of(region))
            .credentialsProvider(credentialsProvider);

      // Override endpoint urls if provided.
      if (hasOption(OVERRIDE_URL_OPTION)) {
         String endpointOverride = optionsMap.get(OVERRIDE_URL_OPTION);
         if (endpointOverride.startsWith("http")) {
            URI endpointOverrideURI = new URI(endpointOverride);
            snsClientBuilder.endpointOverride(endpointOverrideURI);
            sqsClientBuilder.endpointOverride(endpointOverrideURI);
         }
      }

      snsClient = snsClientBuilder.build();
      sqsClient = sqsClientBuilder.build();

      // Ensure connection is possible and subscription of SQS endpoint exists.
      String topicArn = retrieveTopicArn();

      // Create a temporary subscription Queue and get its ARN.
      String subscriptionQueueName = topic + SUBSCRIPTION_PREFIX + "-" + specification.getTestResultId();
      queue = createQueue(subscriptionQueueName, topicArn);

      SubscribeRequest subscribeRequest = SubscribeRequest.builder().protocol("sqs").endpoint(queue.arn())
            .topicArn(topicArn).attributes(Map.of("RawMessageDelivery", "true")).returnSubscriptionArn(true).build();

      subscriptionArn = snsClient.subscribe(subscribeRequest).subscriptionArn();
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

   /**
    * Retrieve a topic ARN using its name (from internal {@code topic} property).
    * @return The topic ARN or null if not found.
    */
   private String retrieveTopicArn() {
      ListTopicsRequest listRequest = ListTopicsRequest.builder().build();
      ListTopicsResponse listResponse = snsClient.listTopics(listRequest);

      if (listResponse.hasTopics() && listResponse.topics().size() > 0) {
         for (Topic topicTopic : listResponse.topics()) {
            logger.infof("Found AWS SNS topic: %s", topicTopic.toString());
            if (topicTopic.topicArn().endsWith(":" + topic)) {
               return topicTopic.topicArn();
            }
         }
      }
      return null;
   }

   /**
    * Create a SQS Queue and retrieve its ARN.
    * @param queueName The name of queue to create
    * @return The unique ARN of the newly created queue
    */
   private QueueCoordinates createQueue(String queueName, String topicArn) {
      // 3 steps operation: 1st create a queue.
      CreateQueueRequest createQueueRequest = CreateQueueRequest.builder().queueName(queueName).build();
      String queueURL = sqsClient.createQueue(createQueueRequest).queueUrl();

      // Now read its attributes, requesting the ARN only.
      GetQueueAttributesResponse queueAttributes = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
            .queueUrl(queueURL).attributeNames(QueueAttributeName.QUEUE_ARN).build());
      String queueArn = queueAttributes.attributes().get(QueueAttributeName.QUEUE_ARN);

      // Finally set the queue policy to allow SNS topic to fanout to queue.
      sqsClient.setQueueAttributes(SetQueueAttributesRequest.builder().queueUrl(queueURL)
            .attributes(Map.of(QueueAttributeName.POLICY,
                  "{\n" + "  \"Statement\": [\n" + "    {\n" + "      \"Effect\": \"Allow\",\n"
                        + "      \"Principal\": {\n" + "        \"Service\": \"sns.amazonaws.com\"\n" + "      },\n"
                        + "      \"Action\": \"sqs:SendMessage\",\n" + "      \"Resource\": \"" + queueArn + "\",\n"
                        + "      \"Condition\": {\n" + "        \"ArnEquals\": {\n" + "          \"aws:SourceArn\": \""
                        + topicArn + "\"\n" + "        }\n" + "      }\n" + "    }\n" + "  ]\n" + "}"))
            .build());

      return new QueueCoordinates(queueName, queueURL, queueArn);
   }

   /** Build set of Microcks headers from SQS headers. */
   private Set<Header> buildHeaders(Map<String, MessageAttributeValue> messageAttributes) {
      if (messageAttributes == null || messageAttributes.isEmpty()) {
         return null;
      }
      Set<Header> results = new HashSet<>();
      for (Map.Entry<String, MessageAttributeValue> attributeEntry : messageAttributes.entrySet()) {
         Header result = new Header();
         result.setName(attributeEntry.getKey());
         result.setValues(Set.of(attributeEntry.getValue().stringValue()));
         results.add(result);
      }
      return results;
   }

   protected record QueueCoordinates(String name, String url, String arn) {
   }
}
