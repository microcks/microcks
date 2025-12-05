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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for AmazonSNSMessageConsumptionTask.
 * @author laurent
 */
class AmazonSNSMessageConsumptionTaskTest {

   @Test
   void testAcceptEndpoint() {
      assertTrue(AmazonSNSMessageConsumptionTask.acceptEndpoint("sns://us-east-1/topicName"));
      assertTrue(AmazonSNSMessageConsumptionTask.acceptEndpoint("sns://eu-west-3/example-topic.fifo"));
      assertTrue(AmazonSNSMessageConsumptionTask
            .acceptEndpoint("sns://us-east-1/topicName?overrideUrl=http://localhost:4566"));
   }

   @Test
   void testAcceptEndpointFailures() {
      assertFalse(AmazonSNSMessageConsumptionTask.acceptEndpoint("awssns://us-east-1/topicName"));
      assertFalse(AmazonSNSMessageConsumptionTask.acceptEndpoint("sns://us-east-1/topicName/subTopic"));
   }
}
