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

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for AMQPMessageConsumptionTask.
 * @author laurent
 */
public class AMQPMessageConsumptionTaskTest {

   @Test
   public void testAcceptEndpoint() {

      assertTrue(AMQPMessageConsumptionTask.acceptEndpoint("amqp://localhost/q/testQueue"));

      assertTrue(AMQPMessageConsumptionTask.acceptEndpoint("amqp://localhost/vHost/q/testQueue"));

      assertTrue(AMQPMessageConsumptionTask.acceptEndpoint("amqp://localhost:5671/q/testQueue"));

      assertTrue(AMQPMessageConsumptionTask.acceptEndpoint("amqp://localhost:5671/vHost/q/testQueue"));

      assertTrue(AMQPMessageConsumptionTask.acceptEndpoint("amqp://localhost:5671/q/testQueue/with/path/elements"));

      assertTrue(
            AMQPMessageConsumptionTask.acceptEndpoint("amqp://localhost:5671/vHost/q/testQueue/with/path/elements"));

      assertTrue(AMQPMessageConsumptionTask.acceptEndpoint("amqp://localhost:5671/f/testExchange?durable=true"));

      assertTrue(AMQPMessageConsumptionTask
            .acceptEndpoint("amqp://localhost:5671/t/testExchange?durable=true&routingKey=samples.*"));

      assertTrue(AMQPMessageConsumptionTask
            .acceptEndpoint("amqp://localhost:5671/vHost/t/testExchange?durable=true&routingKey=samples.*"));
   }

   @Test
   public void testAcceptEndpointFailures() {

      assertFalse(AMQPMessageConsumptionTask.acceptEndpoint("amqp://localhost/x/testQueue"));

      assertFalse(AMQPMessageConsumptionTask.acceptEndpoint("rabbit://localhost/x/testQueue"));
   }

   //@Test
   public void testConsumptionFromQueue() {
      try {
         ConnectionFactory factory = new ConnectionFactory();
         factory.setUri("amqp://localhost");
         factory.setUsername("microcks");
         factory.setPassword("microcks");

         Connection connection = factory.newConnection();
         Channel channel = connection.createChannel();

         String queueName = "my-queue";

         String consumerTag = channel.basicConsume(queueName, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                  byte[] body) throws IOException {
               System.err.println("Received a new AMQP Message: " + new String(body));
               channel.basicAck(envelope.getDeliveryTag(), false);
            }
         });

         Thread.sleep(10000L);

         channel.basicCancel(consumerTag);
         channel.close();
         connection.close();
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }

   //@Test
   public void testConsumptionFromExchange() {
      try {
         ConnectionFactory factory = new ConnectionFactory();
         factory.setUri("amqp://localhost");
         factory.setUsername("microcks");
         factory.setPassword("microcks");

         Connection connection = factory.newConnection();
         Channel channel = connection.createChannel();

         String exchangeName = "my-exchange";

         channel.exchangeDeclare(exchangeName, "fanout", true);
         String queueName = channel.queueDeclare().getQueue();
         channel.queueBind(queueName, exchangeName, "");

         String consumerTag = channel.basicConsume(queueName, false, new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                  byte[] body) throws IOException {
               System.err.println("Received a new AMQP Message: " + new String(body));
               channel.basicAck(envelope.getDeliveryTag(), false);
            }
         });

         Thread.sleep(10000L);

         channel.basicCancel(consumerTag);
         channel.close();
         connection.close();
      } catch (Throwable t) {
         t.printStackTrace();
      }
   }
}
