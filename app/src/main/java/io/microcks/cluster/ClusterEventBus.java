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

import java.util.function.Consumer;

/**
 * A minimal publish/subscribe bus between the replicas of a Microcks cluster. Every message published on a topic is
 * delivered to the subscribers of <b>all</b> replicas, the publisher included, and in the same order everywhere.
 * Messages are not persisted: a replica or a minion that was not connected misses them and resynchronizes at startup.
 * @author laurent
 */
public interface ClusterEventBus {

   /**
    * Publish a message to all the members of the cluster.
    * @param topic   The name of the topic
    * @param payload The message payload (a JSON document)
    */
   void publish(String topic, String payload);

   /**
    * Subscribe to a topic.
    * @param topic    The name of the topic
    * @param listener The consumer called for each message, sequentially and in order
    */
   void subscribe(String topic, Consumer<String> listener);
}
