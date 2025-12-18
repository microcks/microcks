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

import java.util.Map;
import java.util.regex.Matcher;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for KafkaMessageConsumptionTask.
 * @author laurent
 */
class KafkaMessageConsumptionTaskTest {

   @Test
   void testAcceptEndpoint() {
      assertTrue(KafkaMessageConsumptionTask.acceptEndpoint("kafka://localhost/testTopic"));

      assertTrue(KafkaMessageConsumptionTask.acceptEndpoint("kafka://localhost:9092/testTopic"));

      assertTrue(
            KafkaMessageConsumptionTask.acceptEndpoint("kafka://localhost/testTopic?securityProtocol=SASL_PLAINTEXT"));

      assertTrue(KafkaMessageConsumptionTask
            .acceptEndpoint("kafka://localhost:9094/testTopic?securityProtocol=SASL_PLAINTEXT"));

      assertTrue(KafkaMessageConsumptionTask.acceptEndpoint(
            "kafka://my-cluster-kafka-bootstrap-kafka.apps.cluster-943b.943b.example.com:443/UsersignedupAPI_0.1.2_user-signedup"));
   }

   @Test
   void testAcceptEndpointFailures() {
      assertFalse(KafkaMessageConsumptionTask.acceptEndpoint("localhost:9092/testTopic"));

      assertFalse(KafkaMessageConsumptionTask.acceptEndpoint("ssl://localhost:9092/testTopic"));

      assertFalse(KafkaMessageConsumptionTask.acceptEndpoint("kafka://localhost"));

      assertFalse(KafkaMessageConsumptionTask.acceptEndpoint("kafka://localhost:port"));

      assertFalse(KafkaMessageConsumptionTask.acceptEndpoint("kafka://localhost:port/testTopic"));
   }

   @Test
   void testEndpointPattern() {
      Matcher matcher = KafkaMessageConsumptionTask.ENDPOINT_PATTERN
            .matcher("kafka://localhost:9092/UsersignedupAPI_0.1.2_user-signedup?registryUrl=http://localhost:8888");
      // Call matcher.find() to be able to use named expressions.
      matcher.find();
      String endpointBrokerUrl = matcher.group("brokerUrl");
      String endpointTopic = matcher.group("topic");
      String options = matcher.group("options");

      assertEquals("localhost:9092", endpointBrokerUrl);
      assertEquals("UsersignedupAPI_0.1.2_user-signedup", endpointTopic);
      assertEquals("registryUrl=http://localhost:8888", options);
   }

   @Test
   void testInitializeOptionsMap() {
      AsyncTestSpecification specification = new AsyncTestSpecification();
      specification.setEndpointUrl(
            "kafka://localhost/testTopic?registryUrl=http://localhost:8888&registryUsername=reg-user&registryAuthCredSource=USER_INFO&startOffset=100");
      String options = "registryUrl=http://localhost:8888&registryUsername=reg-user&registryAuthCredSource=USER_INFO&startOffset=100";

      Map<String, String> optionsMap = ConsumptionTaskCommons.initializeOptionsMap(options);

      assertNotNull(optionsMap);
      assertEquals("http://localhost:8888", optionsMap.get(KafkaMessageConsumptionTask.REGISTRY_URL_OPTION));
      assertEquals("reg-user", optionsMap.get(KafkaMessageConsumptionTask.REGISTRY_USERNAME_OPTION));
      assertEquals("USER_INFO", optionsMap.get(KafkaMessageConsumptionTask.REGISTRY_AUTH_CREDENTIALS_SOURCE));
      assertEquals("100", optionsMap.get(KafkaMessageConsumptionTask.START_OFFSET));
   }

}
