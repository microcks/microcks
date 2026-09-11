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
import io.github.microcks.domain.TestCasePhase;
import io.github.microcks.minion.async.AsyncTestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link AmazonSQSMessageConsumptionTask} class.
 * @author sebastien
 */
@Testcontainers
class AmazonSQSMessageConsumptionTaskIT {

   private static final Network NETWORK = Network.newNetwork();
   private static final String QUEUE_NAME = "test-queue";
   private static final String TEXT_MESSAGE_TEMPLATE = "{\"greeting\": \"Hello World!\", \"number\": %s}";

   @Container
   private static final LocalStackContainer localStackContainer = new LocalStackContainer(
         DockerImageName.parse("localstack/localstack:4.14")).withNetwork(NETWORK).withNetworkAliases("localstack")
               .withServices(LocalStackContainer.Service.SQS);

   private static SqsClient sqsClient;
   private static String queueUrl;

   @BeforeAll
   static void beforeAll() {
      // Create an SQS client connected to LocalStack.
      sqsClient = SqsClient.builder().endpointOverride(localStackContainer.getEndpoint())
            .region(Region.of(localStackContainer.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())))
            .build();

      // Create the SQS queue.
      queueUrl = sqsClient.createQueue(CreateQueueRequest.builder().queueName(QUEUE_NAME).build()).queueUrl();
   }

   @AfterAll
   static void afterAll() {
      if (sqsClient != null) {
         sqsClient.close();
      }
   }

   @Test
   void shouldReceiveMessageOnQueueCorrectly() throws Exception {
      // Arrange.
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(3000L);
      asyncTestSpecification.setTestResultId("sqs-test-result-id");

      Secret secret = new Secret();
      secret.setUsername(localStackContainer.getAccessKey());
      secret.setPassword(localStackContainer.getSecretKey());
      asyncTestSpecification.setSecret(secret);

      String endpointUrl = "sqs://" + localStackContainer.getRegion() + "/" + QUEUE_NAME + "?overrideUrl="
            + localStackContainer.getEndpoint();
      asyncTestSpecification.setEndpointUrl(endpointUrl);

      List<ConsumedMessage> messages = null;
      List<TestCasePhase> reportedPhases = Collections.synchronizedList(new ArrayList<>());

      // Act.
      try (AmazonSQSMessageConsumptionTask sqsConsumptionTask = new AmazonSQSMessageConsumptionTask(
            asyncTestSpecification); ExecutorService executorService = Executors.newFixedThreadPool(2);) {
         sqsConsumptionTask.setPhaseListener(reportedPhases::add);
         List<Future<List<ConsumedMessage>>> outputs = executorService
               .invokeAll(List.of(new Callable<List<ConsumedMessage>>() {
                  @Override
                  public List<ConsumedMessage> call() throws Exception {
                     // Wait a bit so that consumption task has actually started.
                     await().during(1000, TimeUnit.MILLISECONDS).until(() -> true);
                     sendTextMessagesOnQueue(1);
                     return Collections.emptyList();
                  }
               }, sqsConsumptionTask), asyncTestSpecification.getTimeoutMS() + 1000L, TimeUnit.MILLISECONDS);

         messages = outputs.get(1).get();
      }

      // Assert.
      Assertions.assertFalse(messages.isEmpty());
      Assertions.assertEquals(1, messages.size());
      ConsumedMessage message = messages.getFirst();
      Assertions.assertEquals(TEXT_MESSAGE_TEMPLATE.formatted(0),
            new String(message.getPayload(), StandardCharsets.UTF_8));
      // The real consumer should have reported it was connected and waiting for messages.
      Assertions.assertTrue(reportedPhases.contains(TestCasePhase.WAITING_FOR_MESSAGE),
            "The SQS consumer should have reported the WAITING_FOR_MESSAGE phase.");
   }

   private void sendTextMessagesOnQueue(int numberOfMessages) {
      for (int i = 0; i < numberOfMessages; i++) {
         sqsClient.sendMessage(
               SendMessageRequest.builder().queueUrl(queueUrl).messageBody(TEXT_MESSAGE_TEMPLATE.formatted(i)).build());
         await().pollDelay(250, TimeUnit.MILLISECONDS).untilAsserted(() -> assertTrue(true));
      }
   }
}
