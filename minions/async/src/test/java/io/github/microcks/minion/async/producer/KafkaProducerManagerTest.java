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

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.minion.async.AsyncMockDefinition;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * This is a test case for KafkaProducerManager.
 * @author laurent
 */
class KafkaProducerManagerTest {

   @Test
   void testGetTopicName() {
      KafkaProducerManager producerManager = new KafkaProducerManager();

      Service service = new Service();
      service.setName("Streetlights API");
      service.setVersion("0.1.0");

      Operation operation = new Operation();
      operation.setName("RECEIVE receiveLightMeasurement");
      operation.setMethod("RECEIVE");
      operation.setResourcePaths(Set.of("smartylighting.streetlights.1.0.event.lighting.measured"));
      service.addOperation(operation);

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("Sample");
      List<EventMessage> eventsMessages = List.of(eventMessage);

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, eventsMessages);

      String topicName = producerManager.getTopicName(definition, eventMessage);
      assertEquals("StreetlightsAPI-0.1.0-receiveLightMeasurement", topicName);
   }

   @Test
   void testGetDynamicTopicName() {
      KafkaProducerManager producerManager = new KafkaProducerManager();

      Service service = new Service();
      service.setName("Streetlights API");
      service.setVersion("0.1.0");

      Operation operation = new Operation();
      operation.setName("RECEIVE receiveLightMeasurement");
      operation.setMethod("RECEIVE");
      operation.setDispatcher("URI_PARTS");
      operation.setDispatcherRules("/streetlightId");
      operation.setResourcePaths(Set.of("smartylighting.streetlights.1.0.event.{streetlightId}.lighting.measured",
            "smartylighting.streetlights.1.0.event.da059782-3ad0-4e45-88ce-ef3392bc7797.lighting.measured"));
      service.addOperation(operation);

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("Sample");
      eventMessage.setDispatchCriteria("streetlightId=da059782-3ad0-4e45-88ce-ef3392bc7797");
      List<EventMessage> eventsMessages = List.of(eventMessage);

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, eventsMessages);

      String topicName = producerManager.getTopicName(definition, eventMessage);
      assertEquals(
            "StreetlightsAPI-0.1.0-smartylighting.streetlights.1.0.event.da059782-3ad0-4e45-88ce-ef3392bc7797.lighting.measured",
            topicName);
   }
}
