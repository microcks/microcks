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

import com.rabbitmq.client.*;
import io.github.microcks.domain.Secret;
import io.github.microcks.minion.async.AsyncTestSpecification;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * This is an integration test case using <a href="https://testcontainers.com/">Testcontainers</a> to test
 * {@link AMQPMessageConsumptionTask} class.
 * @author mcruzdev
 */
@Testcontainers
class AMQPMessageConsumptionTaskIT {

   private static final String RABBIT_MQ_PORT = "5672";
   private static final String QUEUE_NAME = "logs";
   private static final String EXCHANGE_DIRECT_NAME = "direct_logs";
   private static final String RABBIT_MQ_3_7_25 = "rabbitmq:3.7.25-management-alpine";

   @Container
   private static final RabbitMQContainer rabbitMQContainer = new RabbitMQContainer();

   static class RabbitMQContainer extends org.testcontainers.containers.RabbitMQContainer {
      public RabbitMQContainer() {
         super(DockerImageName.parse(RABBIT_MQ_3_7_25));
         this.setPortBindings(List.of("%s:%s".formatted(RABBIT_MQ_PORT, RABBIT_MQ_PORT)));
      }
   }

   @Test
   void shouldReceiveMessageOnQueueCorrectly() throws Exception {
      // arrange
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(2000L);
      asyncTestSpecification.setEndpointUrl("amqp://localhost:%s/q/%s".formatted(RABBIT_MQ_PORT, QUEUE_NAME));

      AMQPMessageConsumptionTask amqpMessageConsumptionTask = new AMQPMessageConsumptionTask(asyncTestSpecification);

      sendMessageIntoQueue();

      // act
      List<ConsumedMessage> messages = amqpMessageConsumptionTask.call();

      // assert
      Assertions.assertFalse(messages.isEmpty());
   }

   @Test
   void shouldReceiveMessageOnExchangeWithDirectTypeCorrectly() throws Exception {
      // arrange
      long secondsToWait = Duration.ofSeconds(3).toMillis();
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(secondsToWait);
      asyncTestSpecification.setEndpointUrl(
            "amqp://localhost:%s/d/%s?routingKey=info&durable=true".formatted(RABBIT_MQ_PORT, EXCHANGE_DIRECT_NAME));
      AMQPMessageConsumptionTask amqpMessageConsumptionTask = new AMQPMessageConsumptionTask(asyncTestSpecification);

      //    We need to wait the existence of binding from AMQPMessageConsumptionTask's side
      new Thread(() -> {
         try {
            Thread.sleep(secondsToWait);
            sendMessageIntoExchange(EXCHANGE_DIRECT_NAME, "direct", "info", true, null);
         } catch (IOException | TimeoutException | InterruptedException e) {
            throw new RuntimeException("Error while sending message to queue", e);
         }
      }).start();

      // act
      List<ConsumedMessage> messages = amqpMessageConsumptionTask.call();

      // assert
      Assertions.assertFalse(messages.isEmpty());
   }

   @Test
   void shouldReceiveMessageOnExchangeWithTopicTypeCorrectly() throws Exception {
      // arrange
      String exchangeName = "topic_logs";
      long secondsToWait = Duration.ofSeconds(3).toMillis();
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(secondsToWait + 1000);
      asyncTestSpecification
            .setEndpointUrl("amqp://localhost:%s/t/%s?durable=false".formatted(RABBIT_MQ_PORT, exchangeName));
      AMQPMessageConsumptionTask amqpMessageConsumptionTask = new AMQPMessageConsumptionTask(asyncTestSpecification);

      //    We need to wait the existence of binding from AMQPMessageConsumptionTask's side
      new Thread(() -> {
         try {
            Thread.sleep(secondsToWait);
            sendMessageIntoExchange(exchangeName, "topic", "", false, null);
         } catch (IOException | TimeoutException | InterruptedException e) {
            throw new RuntimeException("Error while sending message to queue", e);
         }
      }).start();

      // act
      List<ConsumedMessage> messages = amqpMessageConsumptionTask.call();

      // assert
      Assertions.assertFalse(messages.isEmpty());
   }

   @Test
   void shouldReceiveMessageOnExchangeWithHeadersTypeCorrectly() throws Exception {
      // arrange
      String exchangeName = "headers_logs";
      long secondsToWait = Duration.ofSeconds(3).toMillis();
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(secondsToWait + 1000);
      asyncTestSpecification
            .setEndpointUrl("amqp://localhost:%s/h/%s?h.severity=info".formatted(RABBIT_MQ_PORT, exchangeName));
      AMQPMessageConsumptionTask amqpMessageConsumptionTask = new AMQPMessageConsumptionTask(asyncTestSpecification);

      //    We need to wait the existence of binding from AMQPMessageConsumptionTask's side
      new Thread(() -> {
         try {
            Thread.sleep(secondsToWait);
            Map<String, Object> props = new HashMap<>();
            props.put("severity", "info");
            //        headers.put("x-match", "any");
            System.out.println("Sending ...");
            sendMessageIntoExchange(exchangeName, "headers", "", false, props);
         } catch (IOException | TimeoutException | InterruptedException e) {
            throw new RuntimeException("Error while sending message to queue", e);
         }
      }).start();

      // act
      List<ConsumedMessage> messages = amqpMessageConsumptionTask.call();

      // assert
      Assertions.assertFalse(messages.isEmpty());
   }

   @Test
   void shouldReceiveMessageOnExchangeWithHeadersWithoutItemsTypeCorrectly() throws Exception {
      // arrange
      String exchangeName = "headers_empty_logs";
      long secondsToWait = Duration.ofSeconds(3).toMillis();
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(secondsToWait + 1000);
      asyncTestSpecification.setEndpointUrl("amqp://localhost:%s/h/%s".formatted(RABBIT_MQ_PORT, exchangeName));
      AMQPMessageConsumptionTask amqpMessageConsumptionTask = new AMQPMessageConsumptionTask(asyncTestSpecification);

      //    We need to wait the existence of binding from AMQPMessageConsumptionTask's side
      new Thread(() -> {
         try {
            Thread.sleep(secondsToWait);
            Map<String, Object> headers = new HashMap<>();
            System.out.println("Sending ...");
            sendMessageIntoExchange(exchangeName, "headers", "", false, headers);
         } catch (IOException | TimeoutException | InterruptedException e) {
            throw new RuntimeException("Error while sending message to queue", e);
         }
      }).start();

      // act
      List<ConsumedMessage> messages = amqpMessageConsumptionTask.call();

      // assert
      Assertions.assertFalse(messages.isEmpty());
   }

   @Test
   void shouldReceiveMessageOnExchangeWithFanoutTypeCorrectly() throws Exception {
      // arrange
      String exchangeName = "fanout_logs";
      long secondsToWait = Duration.ofSeconds(3).toMillis();
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(secondsToWait + 1000);
      asyncTestSpecification.setEndpointUrl("amqp://localhost:%s/f/%s".formatted(RABBIT_MQ_PORT, exchangeName));
      AMQPMessageConsumptionTask amqpMessageConsumptionTask = new AMQPMessageConsumptionTask(asyncTestSpecification);

      //    We need to wait the existence of binding from AMQPMessageConsumptionTask's side
      new Thread(() -> {
         try {
            Thread.sleep(secondsToWait);
            System.out.println("Sending ...");
            sendMessageIntoExchange(exchangeName, "fanout", "", false, null);
         } catch (IOException | TimeoutException | InterruptedException e) {
            throw new RuntimeException("Error while sending message to queue", e);
         }
      }).start();

      // act
      List<ConsumedMessage> messages = amqpMessageConsumptionTask.call();

      // assert
      Assertions.assertFalse(messages.isEmpty());
   }

   @Test
   void shouldReceiveMessageOnExchangeWithSecretsCorrectly() throws Exception {
      // arrange
      String exchangeName = "fanout_secrets_logs";
      long secondsToWait = Duration.ofSeconds(3).toMillis();
      AsyncTestSpecification asyncTestSpecification = new AsyncTestSpecification();
      asyncTestSpecification.setTimeoutMS(secondsToWait + 1000);
      asyncTestSpecification.setEndpointUrl("amqp://localhost:%s/f/%s".formatted(RABBIT_MQ_PORT, exchangeName));
      Secret secret = new Secret();
      secret.setUsername("guest");
      secret.setPassword("guest");
      asyncTestSpecification.setSecret(secret);
      AMQPMessageConsumptionTask amqpMessageConsumptionTask = new AMQPMessageConsumptionTask(asyncTestSpecification);

      //    We need to wait the existence of binding from AMQPMessageConsumptionTask's side
      new Thread(() -> {
         try {
            Thread.sleep(secondsToWait);
            System.out.println("Sending ...");
            sendMessageIntoExchange(exchangeName, "fanout", "", false, null);
         } catch (IOException | TimeoutException | InterruptedException e) {
            throw new RuntimeException("Error while sending message to queue", e);
         }
      }).start();

      // act
      List<ConsumedMessage> messages = amqpMessageConsumptionTask.call();

      // assert
      Assertions.assertFalse(messages.isEmpty());
   }

   private static void sendMessageIntoQueue() throws IOException, TimeoutException {
      ConnectionFactory connectionFactory = new ConnectionFactory();
      connectionFactory.setHost("localhost");
      try (Connection connection = connectionFactory.newConnection()) {
         Channel channel = connection.createChannel();
         channel.queueDeclare(QUEUE_NAME, true, false, false, null);
         String message = "[INFO] Hello from Microcks";
         channel.basicPublish("", QUEUE_NAME, MessageProperties.PERSISTENT_TEXT_PLAIN,
               message.getBytes(StandardCharsets.UTF_8));
      }
   }

   private static void sendMessageIntoExchange(String exchangeName, String type, String routingKey, boolean durable,
         Map<String, Object> headers) throws IOException, TimeoutException {
      ConnectionFactory connectionFactory = new ConnectionFactory();
      connectionFactory.setHost("localhost");
      try (Connection connection = connectionFactory.newConnection()) {
         Channel channel = connection.createChannel();
         channel.exchangeDeclare(exchangeName, type, durable);
         String message = "[INFO] Hello from Microcks using exchange";

         AMQP.BasicProperties props = null;
         if (headers != null) {
            props = new AMQP.BasicProperties.Builder().headers(headers).build();
         }

         channel.basicPublish(exchangeName, routingKey, props, message.getBytes(StandardCharsets.UTF_8));
      }
   }
}
