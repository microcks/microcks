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

import io.github.microcks.domain.Secret;
import io.github.microcks.minion.async.AsyncTestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link AmazonSNSMessageConsumptionTask} class.
 * @author laurent
 */
@Testcontainers
class AmazonSNSMessageConsumptionTaskIT {

   private static final Network NETWORK = Network.newNetwork();
   private static final String TOPIC_NAME = "test-topic";
   private static final String FIFO_TOPIC_NAME = "test-topic.fifo";
   private static final String TEXT_MESSAGE_TEMPLATE = "{\"greeting\": \"Hello World!\", \"number\": %s}";

   @Container
   private static final LocalStackContainer localStackContainer = new LocalStackContainer(
         DockerImageName.parse("localstack/localstack:4.14")).withNetwork(NETWORK).withNetworkAliases("localstack")
               .withServices(LocalStackContainer.Service.SNS, LocalStackContainer.Service.SQS);

   private static SnsClient snsClient;
   private static String topicArn;
   private static String fifoTopicArn;

   @BeforeAll
   static void beforeAll() {
      // Create an SNS client connected to LocalStack.
      snsClient = SnsClient.builder().endpointOverride(localStackContainer.getEndpoint())
            .region(Region.of(localStackContainer.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())))
            .build();

      // Create the SNS topic.
      topicArn = snsClient.createTopic(CreateTopicRequest.builder().name(TOPIC_NAME).build()).topicArn();
      fifoTopicArn = snsClient.createTopic(CreateTopicRequest.builder().name(FIFO_TOPIC_NAME)
            .attributes(Map.of("FifoTopic", "true", "ContentBasedDeduplication", "true")).build()).topicArn();
   }

   @AfterAll
   static void afterAll() {
      if (snsClient != null) {
         snsClient.close();
      }
   }

   @ParameterizedTest
   @ValueSource(strings = { TOPIC_NAME, FIFO_TOPIC_NAME })
   void shouldReceiveMessageOnTopicCorrectly(String topicName) throws Exception {
      // Arrange.
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(3000L);
      asyncTestSpecification.setTestResultId("sns-test-result-id");

      Secret secret = new Secret();
      secret.setUsername(localStackContainer.getAccessKey());
      secret.setPassword(localStackContainer.getSecretKey());
      asyncTestSpecification.setSecret(secret);

      String endpointUrl = "sns://" + localStackContainer.getRegion() + "/" + topicName + "?overrideUrl="
            + localStackContainer.getEndpoint();
      asyncTestSpecification.setEndpointUrl(endpointUrl);

      List<ConsumedMessage> messages = null;
      List<io.github.microcks.domain.TestCasePhase> reportedPhases = Collections.synchronizedList(new ArrayList<>());

      // Act.
      try (AmazonSNSMessageConsumptionTask snsConsumptionTask = new AmazonSNSMessageConsumptionTask(
            asyncTestSpecification); ExecutorService executorService = Executors.newFixedThreadPool(2);) {
         snsConsumptionTask.setPhaseListener(reportedPhases::add);
         List<Future<List<ConsumedMessage>>> outputs = executorService
               .invokeAll(List.of(new Callable<List<ConsumedMessage>>() {
                  @Override
                  public List<ConsumedMessage> call() throws Exception {
                     // Wait a bit so that consumption task has actually started.
                     await().during(1000, TimeUnit.MILLISECONDS).until(() -> true);
                     sendTextMessagesOnTopic(1, topicName);
                     return Collections.emptyList();
                  }
               }, snsConsumptionTask), asyncTestSpecification.getTimeoutMS() + 1000L, TimeUnit.MILLISECONDS);

         messages = outputs.get(1).get();
      }

      // Assert.
      Assertions.assertFalse(messages.isEmpty());
      Assertions.assertEquals(1, messages.size());
      ConsumedMessage message = messages.getFirst();
      Assertions.assertEquals(TEXT_MESSAGE_TEMPLATE.formatted(0),
            new String(message.getPayload(), StandardCharsets.UTF_8));
      // The real consumer should have reported it was connected and waiting for messages.
      Assertions.assertTrue(reportedPhases.contains(io.github.microcks.domain.TestCasePhase.WAITING_FOR_MESSAGE),
            "The SNS consumer should have reported the WAITING_FOR_MESSAGE phase.");
   }

   private void sendTextMessagesOnTopic(int numberOfMessages, String topicName) {
      // Find correct SNS topic to use.
      boolean isFifo = topicName.endsWith(".fifo");
      String arn = isFifo ? fifoTopicArn : topicArn;

      for (int i = 0; i < numberOfMessages; i++) {
         PublishRequest.Builder publishRequestBuilder = PublishRequest.builder().topicArn(arn)
               .message(TEXT_MESSAGE_TEMPLATE.formatted(i));
         if (isFifo) {
            publishRequestBuilder.messageGroupId("test-group");
         }
         snsClient.publish(publishRequestBuilder.build());
         await().pollDelay(250, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(true));
      }
   }
}

