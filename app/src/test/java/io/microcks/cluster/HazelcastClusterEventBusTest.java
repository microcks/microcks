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
package io.microcks.cluster;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@code HazelcastClusterEventBus}: two members of the same cluster, joined with tcp-ip, see the messages of
 * a topic in the same order, whoever publishes.
 * @author laurent
 */
class HazelcastClusterEventBusTest {

   private static HazelcastClusterEventBus member(int port, String members) {
      return new HazelcastClusterEventBus("tcp-ip", "microcks-test", port, java.util.Optional.of(members),
            java.util.Optional.empty(), "microcks", java.util.Optional.empty());
   }

   @Test
   void messagesAreDeliveredInOrderToAllMembers() throws Exception {
      String members = "127.0.0.1:5801,127.0.0.1:5802";
      HazelcastClusterEventBus first = member(5801, members);
      HazelcastClusterEventBus second = member(5802, members);
      try {
         List<String> receivedByFirst = new CopyOnWriteArrayList<>();
         List<String> receivedBySecond = new CopyOnWriteArrayList<>();
         first.subscribe("microcks-test-topic", receivedByFirst::add);
         second.subscribe("microcks-test-topic", receivedBySecond::add);

         for (int i = 0; i < 50; i++) {
            (i % 2 == 0 ? first : second).publish("microcks-test-topic", "m" + i);
         }

         long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
         while ((receivedByFirst.size() < 50 || receivedBySecond.size() < 50) && System.nanoTime() < deadline) {
            Thread.sleep(50);
         }
         assertEquals(50, receivedByFirst.size());
         assertEquals(receivedByFirst, receivedBySecond);
      } finally {
         first.shutdown();
         second.shutdown();
      }
   }

   @Test
   void unknownDiscoveryModeIsRejected() {
      assertThrows(IllegalArgumentException.class,
            () -> HazelcastClusterEventBus.buildConfig("multicast", "c", 5701, "", null, "microcks", null));
   }
}
