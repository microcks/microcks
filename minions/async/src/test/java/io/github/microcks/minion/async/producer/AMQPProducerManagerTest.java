package io.github.microcks.minion.async.producer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link AMQPProducerManager}.
 */
class AMQPProducerManagerTest {

   @Test
   void shouldBuildDefaultAmqpUri() {
      AMQPProducerManager manager = new AMQPProducerManager();
      manager.amqpServer = "rabbitmq";

      assertEquals("amqp://rabbitmq", manager.buildAmqpUri());
   }

   @Test
   void shouldBuildAmqpUriForStandardPort() {
      AMQPProducerManager manager = new AMQPProducerManager();
      manager.amqpServer = "rabbitmq:5672";

      assertEquals("amqp://rabbitmq:5672", manager.buildAmqpUri());
   }

   @Test
   void shouldBuildAmqpsUriForTlsPort() {
      AMQPProducerManager manager = new AMQPProducerManager();
      manager.amqpServer = "rabbitmq:5671";

      assertEquals("amqps://rabbitmq:5671", manager.buildAmqpUri());
   }

   @Test
   void shouldKeepExplicitAmqpUri() {
      AMQPProducerManager manager = new AMQPProducerManager();
      manager.amqpServer = "amqp://rabbitmq:5672";

      assertEquals("amqp://rabbitmq:5672", manager.buildAmqpUri());
   }

   @Test
   void shouldKeepExplicitAmqpsUri() {
      AMQPProducerManager manager = new AMQPProducerManager();
      manager.amqpServer = "amqps://rabbitmq:5671";

      assertEquals("amqps://rabbitmq:5671", manager.buildAmqpUri());
   }
}
