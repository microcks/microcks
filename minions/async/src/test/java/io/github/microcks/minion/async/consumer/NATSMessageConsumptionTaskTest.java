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

import io.github.microcks.minion.async.AsyncTestSpecification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for NATSMessageConsumptionTask.
 * @author laurent
 */
public class NATSMessageConsumptionTaskTest {

   @Test
   public void testAcceptEndpoint() {
      AsyncTestSpecification specification = new AsyncTestSpecification();
      NATSMessageConsumptionTask task = new NATSMessageConsumptionTask(specification);

      assertTrue(NATSMessageConsumptionTask.acceptEndpoint("nats://localhost:4222/testTopic"));

      assertTrue(NATSMessageConsumptionTask.acceptEndpoint("nats://mynats.acme.com/testTopic"));
   }

   @Test
   public void testAcceptEndpointFailures() {
      AsyncTestSpecification specification = new AsyncTestSpecification();
      NATSMessageConsumptionTask task = new NATSMessageConsumptionTask(specification);

      assertFalse(NATSMessageConsumptionTask.acceptEndpoint("ssl://localhost:1883/testTopic"));

      assertFalse(NATSMessageConsumptionTask.acceptEndpoint("mqtt://localhost:1883"));

      assertFalse(NATSMessageConsumptionTask.acceptEndpoint("nats://localhost:port/testTopic"));
   }
}
