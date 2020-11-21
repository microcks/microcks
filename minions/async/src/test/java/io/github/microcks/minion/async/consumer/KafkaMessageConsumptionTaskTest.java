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
 * This is a test case for KafkaMessageConsumptionTask.
 * @author laurent
 */
public class KafkaMessageConsumptionTaskTest {

   @Test
   public void testAcceptEndpoint() {
      AsyncTestSpecification specification = new AsyncTestSpecification();
      KafkaMessageConsumptionTask task = new KafkaMessageConsumptionTask(specification);

      assertTrue(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://localhost/testTopic"));

      assertTrue(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://localhost:9092/testTopic"));

      assertTrue(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://localhost/testTopic?securityProtocol=SASL_PLAINTEXT"));;

      assertTrue(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://localhost:9094/testTopic?securityProtocol=SASL_PLAINTEXT"));

      assertTrue(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://my-cluster-kafka-bootstrap-kafka.apps.cluster-943b.943b.example.com:443/UsersignedupAPI_0.1.2_user-signedup"));
   }

   @Test
   public void testAcceptEndpointFailures() {
      AsyncTestSpecification specification = new AsyncTestSpecification();
      KafkaMessageConsumptionTask task = new KafkaMessageConsumptionTask(specification);

      assertFalse(KafkaMessageConsumptionTask
            .acceptEndpoint("localhost:9092/testTopic"));

      assertFalse(KafkaMessageConsumptionTask
            .acceptEndpoint("ssl://localhost:9092/testTopic"));

      assertFalse(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://localhost"));

      assertFalse(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://localhost:port"));

      assertFalse(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://localhost:port/testTopic"));
   }
}
