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
      assertEquals("amqp://rabbitmq", AMQPProducerManager.resolveAmqpUri("rabbitmq"));
   }

   @Test
   void shouldBuildAmqpUriForStandardPort() {
      assertEquals("amqp://rabbitmq:5672", AMQPProducerManager.resolveAmqpUri("rabbitmq:5672"));
   }

   @Test
   void shouldBuildAmqpsUriForTlsPort() {
      assertEquals("amqps://broker.example.com:5671", AMQPProducerManager.resolveAmqpUri("broker.example.com:5671"));
   }

   @Test
   void shouldKeepExplicitAmqpUri() {
      assertEquals("amqp://rabbitmq:5672", AMQPProducerManager.resolveAmqpUri("amqp://rabbitmq:5672"));
   }

   @Test
   void shouldKeepExplicitAmqpsUri() {
      assertEquals("amqps://broker.example.com:5671",
            AMQPProducerManager.resolveAmqpUri("amqps://broker.example.com:5671"));
   }
}
