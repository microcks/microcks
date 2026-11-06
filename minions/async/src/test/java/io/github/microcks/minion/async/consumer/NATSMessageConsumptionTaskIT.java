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

import io.github.microcks.domain.TestCasePhase;
import io.github.microcks.minion.async.AsyncTestSpecification;

import io.nats.client.Connection;
import io.nats.client.Nats;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link NATSMessageConsumptionTask} class.
 * @author sebastien
 */
@Testcontainers
class NATSMessageConsumptionTaskIT {

   private static final int NATS_PORT = 4222;
   private static final String TOPIC_NAME = "test-topic";
   private static final String TEXT_MESSAGE_TEMPLATE = "{\"greeting\": \"Hello World!\", \"number\": %s}";

   @Container
   private static final GenericContainer<?> natsContainer = new GenericContainer<>(
         DockerImageName.parse("nats:2.14.0-alpine")).withExposedPorts(NATS_PORT)
               .waitingFor(Wait.forLogMessage(".*Server is ready.*\\n", 1));

   @Test
   void shouldReceiveMessageOnTopicCorrectly() throws Exception {
      // Arrange.
      String brokerUrl = "localhost:" + natsContainer.getMappedPort(NATS_PORT);
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(2000L);
      asyncTestSpecification.setEndpointUrl("nats://" + brokerUrl + "/" + TOPIC_NAME);

      NATSMessageConsumptionTask natsConsumptionTask = new NATSMessageConsumptionTask(asyncTestSpecification);
      List<TestCasePhase> reportedPhases = Collections.synchronizedList(new ArrayList<>());
      natsConsumptionTask.setPhaseListener(reportedPhases::add);

      // Act.
      ExecutorService executorService = Executors.newFixedThreadPool(2);
      List<Future<List<ConsumedMessage>>> outputs = executorService
            .invokeAll(List.of(new Callable<List<ConsumedMessage>>() {
               @Override
               public List<ConsumedMessage> call() throws Exception {
                  // Wait a bit so that the consumer is actually subscribed before publishing.
                  await().during(750, TimeUnit.MILLISECONDS).until(() -> true);
                  sendTextMessageOnTopic(brokerUrl);
                  return Collections.emptyList();
               }
            }, natsConsumptionTask), asyncTestSpecification.getTimeoutMS() + 1000L, TimeUnit.MILLISECONDS);

      List<ConsumedMessage> messages = outputs.get(1).get();

      // Assert.
      Assertions.assertFalse(messages.isEmpty());
      Assertions.assertEquals(1, messages.size());
      Assertions.assertEquals(TEXT_MESSAGE_TEMPLATE.formatted(0),
            new String(messages.get(0).getPayload(), StandardCharsets.UTF_8));
      // The real consumer should have reported it was connected and waiting for messages.
      Assertions.assertTrue(reportedPhases.contains(TestCasePhase.WAITING_FOR_MESSAGE),
            "The NATS consumer should have reported the WAITING_FOR_MESSAGE phase.");
   }

   private void sendTextMessageOnTopic(String brokerUrl) throws Exception {
      try (Connection publisher = Nats.connect("nats://" + brokerUrl)) {
         publisher.publish(TOPIC_NAME, TEXT_MESSAGE_TEMPLATE.formatted(0).getBytes(StandardCharsets.UTF_8));
         publisher.flush(java.time.Duration.ofSeconds(1));
      }
   }
}
