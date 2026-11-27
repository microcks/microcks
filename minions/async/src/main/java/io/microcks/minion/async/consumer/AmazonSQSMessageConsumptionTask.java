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
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

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
 * An implementation of <code>MessageConsumptionTask</code> that consumes a queue on Amazon Simple Queue Service (SQS).
 * Endpoint URL should be specified using the following form:
 * <code>sqs://{region}/{queue}[?option1=value1&amp;option2=value2]</code>
 * @author laurent
 */
public class AmazonSQSMessageConsumptionTask implements MessageConsumptionTask {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   /** The string for Regular Expression that helps validating acceptable endpoints. */
   public static final String ENDPOINT_PATTERN_STRING = "sqs://(?<region>[a-zA-Z0-9-]+)/(?<queue>[a-zA-Z0-9-_\\.]+)(\\?(?<options>.+))?";
   /** The Pattern for matching groups within the endpoint regular expression. */
   public static final Pattern ENDPOINT_PATTERN = Pattern.compile(ENDPOINT_PATTERN_STRING);

   /** The endpoint URL option representing AWS endpoint override URL. */
   public static final String OVERRIDE_URL_OPTION = "overrideUrl";

   private AsyncTestSpecification specification;

   protected String queue;

   protected Map<String, String> optionsMap;

   private AwsCredentialsProvider credentialsProvider;

   private SqsClient client;

   /**
    * Creates a new consumption task from an Async test specification.
    * @param testSpecification The specification holding endpointURL and timeout.
    */
   public AmazonSQSMessageConsumptionTask(AsyncTestSpecification testSpecification) {
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
      // As initialization can be long (circa 500 ms), we have to remove this time from wait time.
      long startTime = System.currentTimeMillis();
      if (client == null) {
         initializeSQSClient();
      }
      List<ConsumedMessage> messages = new ArrayList<>();

      // Find the correct queue url.
      String queueUrl = retrieveQueueURL();
      if (queueUrl == null) {
         logger.errorf("Unable to find the SQS queue URL for queue named '%s'", queue);
         throw new IOException("Unable to find the SQS queue URL for queue " + queue);
      }

      long timeoutTime = startTime + specification.getTimeoutMS();
      while (System.currentTimeMillis() - startTime < specification.getTimeoutMS()) {
         // Start polling/receiving messages with a max wait time and a max number.
         ReceiveMessageRequest messageRequest = ReceiveMessageRequest.builder().queueUrl(queueUrl)
               .maxNumberOfMessages(10).waitTimeSeconds((int) (timeoutTime - System.currentTimeMillis()) / 1000)
               .build();

         List<Message> receivedMessages = client.receiveMessage(messageRequest).messages();

         for (Message receivedMessage : receivedMessages) {
            // Build a ConsumedMessage from SQS message.
            ConsumedMessage message = new ConsumedMessage();
            message.setReceivedAt(System.currentTimeMillis());
            message.setHeaders(buildHeaders(receivedMessage.messageAttributes()));
            message.setPayload(receivedMessage.body().getBytes(StandardCharsets.UTF_8));
            messages.add(message);
         }
      }
      client.close();
      return messages;
   }

   @Override
   public void close() throws IOException {
      if (client != null) {
         client.close();
      }
   }

   /** Initialize Amazon SQS client from test properties. */
   private void initializeSQSClient() throws Exception {
      Matcher matcher = ENDPOINT_PATTERN.matcher(specification.getEndpointUrl().trim());
      // Call matcher.find() to be able to use named expressions.
      matcher.find();

      String region = matcher.group("region");
      queue = matcher.group("queue");
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

      // Build the SQS client with provided region and credentials.
      SqsClientBuilder builder = SqsClient.builder().region(Region.of(region)).credentialsProvider(credentialsProvider);

      if (hasOption(OVERRIDE_URL_OPTION)) {
         String endpointOverride = optionsMap.get(OVERRIDE_URL_OPTION);
         if (endpointOverride.startsWith("http")) {
            builder.endpointOverride(new URI(endpointOverride));
         }
      }

      client = builder.build();
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
    * Retrieve a queue URL using its name (from internal {@code queue} property).
    * @return The queue URL or null if not found.
    */
   private String retrieveQueueURL() {
      ListQueuesRequest listRequest = ListQueuesRequest.builder().queueNamePrefix(queue).maxResults(1).build();
      ListQueuesResponse listResponse = client.listQueues(listRequest);

      if (listResponse.hasQueueUrls()) {
         logger.infof("Found AWS SQS queue: %s", listResponse.queueUrls().get(0));
         return listResponse.queueUrls().get(0);
      }
      return null;
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
}
