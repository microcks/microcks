/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.minion.async.consumer;

import io.github.microcks.minion.async.AsyncTestSpecification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for MQTTMessageConsumptionTask.
 * @author laurent
 */
public class MQTTMessageConsumptionTaskTest {

   @Test
   public void testAcceptEndpoint() {
      AsyncTestSpecification specification = new AsyncTestSpecification();
      MQTTMessageConsumptionTask task = new MQTTMessageConsumptionTask(specification);

      assertTrue(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost/testTopic"));

      assertTrue(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost:1883/testTopic"));

      assertTrue(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost:1883/topic/with/path/elements"));

      assertTrue(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost:1883/topic/with/path/elements?option1=value1"));

      assertTrue(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost/testTopic/option1=value1"));

      assertTrue(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost:1883/testTopic/option1=value1"));

      assertTrue(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://my-cluster-activemq.apps.cluster-943b.943b.example.com:1883/testTopic/option1=value1"));
   }

   @Test
   public void testAcceptEndpointFailures() {
      AsyncTestSpecification specification = new AsyncTestSpecification();
      MQTTMessageConsumptionTask task = new MQTTMessageConsumptionTask(specification);

      assertFalse(MQTTMessageConsumptionTask
            .acceptEndpoint("localhost:1883/testTopic"));

      assertFalse(MQTTMessageConsumptionTask
            .acceptEndpoint("ssl://localhost:1883/testTopic"));

      assertFalse(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost"));

      assertFalse(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost:1883"));

      assertFalse(MQTTMessageConsumptionTask
            .acceptEndpoint("mqtt://localhost:port/testTopic"));
   }
}
