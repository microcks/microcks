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
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.SchemaRegistry;
import io.github.microcks.minion.async.client.MicrocksAPIConnector;

import org.junit.jupiter.api.BeforeEach;
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
import software.amazon.awssdk.services.sqs.model.ListQueuesRequest;
import software.amazon.awssdk.services.sqs.model.ListQueuesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link AmazonSQSProducerManager} and {@link ProducerManager} classes.
 * @author laurent
 */
@Testcontainers
class AmazonSQSProducerManagerIT {

   private static final String QUEUE_NAME = "UsersignedupAPI-030-user-signedup";
   private static final Network NETWORK = Network.newNetwork();

   private SqsClient sqsClient = null;

   @Container
   private static final LocalStackContainer localStackContainer = new LocalStackContainer(
         DockerImageName.parse("localstack/localstack:latest")).withNetwork(NETWORK).withNetworkAliases("localstack")
               .withServices(LocalStackContainer.Service.SQS);

   @BeforeEach
   void beforeEach() {
      sqsClient = SqsClient.builder().endpointOverride(localStackContainer.getEndpoint())
            .region(Region.of(localStackContainer.getRegion()))
            .credentialsProvider(StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(localStackContainer.getAccessKey(), localStackContainer.getSecretKey())))
            .build();
   }

   @Test
   void testProduceMockMessages() throws Exception {
      // Arrange.
      String asyncAPIContent = Files.readString(
            Paths.get("target/test-classes/io/github/microcks/minion/async", "user-signedup-asyncapi-3.0.yaml"));

      // Prepare some event messages.
      EventMessage aliceEvent = new EventMessage();
      aliceEvent.setName("Alice");
      aliceEvent.setMediaType("application/json");
      aliceEvent.setContent("{\"fullName\": \"Alice\", \"email\": \"alice@acme.com\", \"age\": 30}");
      EventMessage bobEvent = new EventMessage();
      bobEvent.setName("Bob");
      bobEvent.setMediaType("application/json");
      bobEvent.setContent("{\"fullName\": \"Bod\", \"email\": \"bod@acme.com\", \"age\": 35}");

      // Prepare associated service and operation.
      Service service = new Service();
      service.setId("d3d5a3ed-13bf-493f-a06d-bf93392f420b");
      service.setName("User signed-up API");
      service.setVersion("0.3.0");
      service.setType(ServiceType.EVENT);

      Operation signedupOperation = new Operation();
      signedupOperation.setName("SUBSCRIBE user/signedup");
      service.setOperations(List.of(signedupOperation));

      // Assemble them into a repository.
      AsyncMockRepository mockRepository = new AsyncMockRepository();
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, signedupOperation,
            List.of(aliceEvent, bobEvent));
      mockRepository.storeMockDefinition(mockDefinition);

      MicrocksAPIConnector microcksAPIConnector = new FakeMicrocksAPIConnector("d3d5a3ed-13bf-493f-a06d-bf93392f420b",
            asyncAPIContent);
      SchemaRegistry schemaRegistry = new SchemaRegistry(microcksAPIConnector);
      schemaRegistry.updateRegistryForService("d3d5a3ed-13bf-493f-a06d-bf93392f420b");

      // Finally, arrange the objects under test.
      AmazonSQSProducerManager sqsProducerManager = new AmazonSQSProducerManager();
      sqsProducerManager.region = localStackContainer.getRegion();
      sqsProducerManager.credentialsType = AmazonCredentialsProviderType.ENV_VARIABLE;
      sqsProducerManager.endpointOverride = Optional.of(localStackContainer.getEndpoint());
      System.setProperty("aws.accessKeyId", localStackContainer.getAccessKey());
      System.setProperty("aws.secretAccessKey", localStackContainer.getSecretKey());
      sqsProducerManager.create();

      ProducerManager producerManager = new ProducerManager(mockRepository, schemaRegistry, null, null, null, null,
            null, sqsProducerManager, null);

      // Act.
      producerManager.produceSQSMockMessages(mockDefinition);

      // Wait a moment to be sure that minion has created the SQS queue.
      AtomicReference<String> queueUrl = new AtomicReference<>(null);
      SqsClient finalSqsClient = sqsClient;
      await().atMost(3, TimeUnit.SECONDS).pollDelay(500, TimeUnit.MILLISECONDS).pollDelay(500, TimeUnit.MILLISECONDS)
            .until(() -> {
               ListQueuesResponse listResponse = finalSqsClient
                     .listQueues(ListQueuesRequest.builder().queueNamePrefix(QUEUE_NAME).maxResults(1).build());
               if (!listResponse.queueUrls().isEmpty()) {
                  queueUrl.set(listResponse.queueUrls().get(0));
                  return true;
               }
               return false;
            });

      // Consume messages on queue during 2 seconds.
      List<String> messages = consumeMessagesFromQueue(sqsClient, String.valueOf(queueUrl), 2000);

      // Assert.
      assertFalse(messages.isEmpty());
      assertEquals(2, messages.size());

      for (String message : messages) {
         assertTrue("{\"fullName\": \"Alice\", \"email\": \"alice@acme.com\", \"age\": 30}".equals(message)
               || "{\"fullName\": \"Bod\", \"email\": \"bod@acme.com\", \"age\": 35}".equals(message));
      }
   }

   private static List<String> consumeMessagesFromQueue(SqsClient sqsClient, String queueUrl, long timeout) {
      List<String> messages = new ArrayList<>();

      long startTime = System.currentTimeMillis();
      long timeoutTime = startTime + timeout;
      while (System.currentTimeMillis() - startTime < timeout) {
         // Start polling/receiving messages with a max wait time and a max number.
         ReceiveMessageRequest messageRequest = ReceiveMessageRequest.builder().queueUrl(queueUrl)
               .maxNumberOfMessages(10).waitTimeSeconds((int) (timeoutTime - System.currentTimeMillis()) / 1000)
               .build();

         List<Message> receivedMessages = sqsClient.receiveMessage(messageRequest).messages();
         for (Message receivedMessage : receivedMessages) {
            messages.add(receivedMessage.body());
         }
      }
      return messages;
   }
}
