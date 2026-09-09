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
      manager.amqpServer = "broker.example.com:5671";

      assertEquals("amqps://broker.example.com:5671", manager.buildAmqpUri());
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
      manager.amqpServer = "amqps://broker.example.com:5671";

      assertEquals("amqps://broker.example.com:5671", manager.buildAmqpUri());
   }
}
