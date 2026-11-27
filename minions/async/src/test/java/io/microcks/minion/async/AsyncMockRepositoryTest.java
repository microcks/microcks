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
package io.github.microcks.minion.async;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ReplyInfo;
import io.github.microcks.domain.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for AsyncMockRepository class.
 * @author adamhicks
 */
class AsyncMockRepositoryTest {

   private AsyncMockRepository repository;

   @BeforeEach
   void setUp() {
      repository = new AsyncMockRepository();
   }

   @Test
   void testStoreMockDefinition() {
      // Setup
      Service service = createService("Service1", "1.0.0", "service-1");
      Operation operation = createOperation("SUBSCRIBE test/topic", 1000L);
      List<EventMessage> messages = createEventMessages(2);
      AsyncMockDefinition mockDefinition = new AsyncMockDefinition(service, operation, messages);

      // Execute
      repository.storeMockDefinition(mockDefinition);

      // Verify
      Set<AsyncMockDefinition> definitions = repository.getMocksDefinitions();
      assertEquals(1, definitions.size());
      assertTrue(definitions.contains(mockDefinition));
   }

   @Test
   void testStoreMockDefinition_UpdateExisting() {
      // Setup - store initial definition
      Service service = createService("Service1", "1.0.0", "service-1");
      Operation operation = createOperation("SUBSCRIBE test/topic", 1000L);
      List<EventMessage> messages = createEventMessages(2);
      AsyncMockDefinition mockDefinition1 = new AsyncMockDefinition(service, operation, messages);
      repository.storeMockDefinition(mockDefinition1);

      // Create updated definition (same service and operation, but different messages)
      List<EventMessage> updatedMessages = createEventMessages(3);
      AsyncMockDefinition mockDefinition2 = new AsyncMockDefinition(service, operation, updatedMessages);

      // Execute
      repository.storeMockDefinition(mockDefinition2);

      // Verify - should have only one definition (the updated one)
      Set<AsyncMockDefinition> definitions = repository.getMocksDefinitions();
      assertEquals(1, definitions.size());
      AsyncMockDefinition stored = definitions.iterator().next();
      assertEquals(3, stored.getEventMessages().size());
   }

   @Test
   void testRemoveMockDefinitions() {
      // Setup - add multiple definitions with different service IDs
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition mockDef1 = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      Service service2 = createService("Service2", "1.0.0", "service-2");
      Operation operation2 = createOperation("SUBSCRIBE test/topic2", 2000L);
      AsyncMockDefinition mockDef2 = new AsyncMockDefinition(service2, operation2, createEventMessages(1));

      repository.storeMockDefinition(mockDef1);
      repository.storeMockDefinition(mockDef2);

      // Execute - remove definitions for service-1
      repository.removeMockDefinitions("service-1");

      // Verify
      Set<AsyncMockDefinition> definitions = repository.getMocksDefinitions();
      assertEquals(1, definitions.size());
      assertFalse(definitions.contains(mockDef1));
      assertTrue(definitions.contains(mockDef2));
   }

   @Test
   void testGetMockDefinitionsFrequencies() {
      // Setup - add definitions with different frequencies
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition mockDef1 = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      Service service2 = createService("Service2", "1.0.0", "service-2");
      Operation operation2 = createOperation("SUBSCRIBE test/topic2", 2000L);
      AsyncMockDefinition mockDef2 = new AsyncMockDefinition(service2, operation2, createEventMessages(1));

      Service service3 = createService("Service3", "1.0.0", "service-3");
      Operation operation3 = createOperation("SUBSCRIBE test/topic3", 1000L);
      AsyncMockDefinition mockDef3 = new AsyncMockDefinition(service3, operation3, createEventMessages(1));

      repository.storeMockDefinition(mockDef1);
      repository.storeMockDefinition(mockDef2);
      repository.storeMockDefinition(mockDef3);

      // Execute
      Set<Long> frequencies = repository.getMockDefinitionsFrequencies();

      // Verify
      assertEquals(2, frequencies.size());
      assertTrue(frequencies.contains(1000L));
      assertTrue(frequencies.contains(2000L));
   }

   @Test
   void testGetMockDefinitionsFrequencies_ExcludesRequestReply() {
      // Setup - add both unidirectional and request-reply definitions
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition unidirectionalDef = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      Service service2 = createService("Service2", "1.0.0", "service-2");
      Operation operation2 = createRequestReplyOperation("SUBSCRIBE test/request", 2000L);
      AsyncMockDefinition requestReplyDef = new AsyncMockDefinition(service2, operation2, createEventMessages(1));

      repository.storeMockDefinition(unidirectionalDef);
      repository.storeMockDefinition(requestReplyDef);

      // Execute
      Set<Long> frequencies = repository.getMockDefinitionsFrequencies();

      // Verify - should only include unidirectional operation frequency
      assertEquals(1, frequencies.size());
      assertTrue(frequencies.contains(1000L));
      assertFalse(frequencies.contains(2000L));
   }

   @Test
   void testGetMockDefinitionsByFrequency() {
      // Setup - add definitions with different frequencies
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition mockDef1 = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      Service service2 = createService("Service2", "1.0.0", "service-2");
      Operation operation2 = createOperation("SUBSCRIBE test/topic2", 2000L);
      AsyncMockDefinition mockDef2 = new AsyncMockDefinition(service2, operation2, createEventMessages(1));

      Service service3 = createService("Service3", "1.0.0", "service-3");
      Operation operation3 = createOperation("SUBSCRIBE test/topic3", 1000L);
      AsyncMockDefinition mockDef3 = new AsyncMockDefinition(service3, operation3, createEventMessages(1));

      repository.storeMockDefinition(mockDef1);
      repository.storeMockDefinition(mockDef2);
      repository.storeMockDefinition(mockDef3);

      // Execute
      Set<AsyncMockDefinition> defsWithFreq1000 = repository.getMockDefinitionsByFrequency(1000L);
      Set<AsyncMockDefinition> defsWithFreq2000 = repository.getMockDefinitionsByFrequency(2000L);

      // Verify
      assertEquals(2, defsWithFreq1000.size());
      assertTrue(defsWithFreq1000.contains(mockDef1));
      assertTrue(defsWithFreq1000.contains(mockDef3));

      assertEquals(1, defsWithFreq2000.size());
      assertTrue(defsWithFreq2000.contains(mockDef2));
   }

   @Test
   void testGetMockDefinitionsByFrequency_ExcludesRequestReply() {
      // Setup - add both unidirectional and request-reply definitions with same frequency
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition unidirectionalDef = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      Service service2 = createService("Service2", "1.0.0", "service-2");
      Operation operation2 = createRequestReplyOperation("SUBSCRIBE test/request", 1000L);
      AsyncMockDefinition requestReplyDef = new AsyncMockDefinition(service2, operation2, createEventMessages(1));

      repository.storeMockDefinition(unidirectionalDef);
      repository.storeMockDefinition(requestReplyDef);

      // Execute
      Set<AsyncMockDefinition> definitions = repository.getMockDefinitionsByFrequency(1000L);

      // Verify - should only include unidirectional definition
      assertEquals(1, definitions.size());
      assertTrue(definitions.contains(unidirectionalDef));
      assertFalse(definitions.contains(requestReplyDef));
   }

   @Test
   void testGetMockDefinitionsByServiceAndVersion() {
      // Setup
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition mockDef1 = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      Service service1v2 = createService("Service1", "2.0.0", "service-1-v2");
      Operation operation1v2 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition mockDef1v2 = new AsyncMockDefinition(service1v2, operation1v2, createEventMessages(1));

      Service service2 = createService("Service2", "1.0.0", "service-2");
      Operation operation2 = createOperation("SUBSCRIBE test/topic2", 2000L);
      AsyncMockDefinition mockDef2 = new AsyncMockDefinition(service2, operation2, createEventMessages(1));

      repository.storeMockDefinition(mockDef1);
      repository.storeMockDefinition(mockDef1v2);
      repository.storeMockDefinition(mockDef2);

      // Execute
      Set<AsyncMockDefinition> service1Defs = repository.getMockDefinitionsByServiceAndVersion("Service1", "1.0.0");
      Set<AsyncMockDefinition> service1v2Defs = repository.getMockDefinitionsByServiceAndVersion("Service1", "2.0.0");
      Set<AsyncMockDefinition> service2Defs = repository.getMockDefinitionsByServiceAndVersion("Service2", "1.0.0");

      // Verify
      assertEquals(1, service1Defs.size());
      assertTrue(service1Defs.contains(mockDef1));

      assertEquals(1, service1v2Defs.size());
      assertTrue(service1v2Defs.contains(mockDef1v2));

      assertEquals(1, service2Defs.size());
      assertTrue(service2Defs.contains(mockDef2));
   }

   @Test
   void testGetRequestReplyMockDefinitions() {
      // Setup - add both unidirectional and request-reply definitions
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition unidirectionalDef1 = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      Service service2 = createService("Service2", "1.0.0", "service-2");
      Operation operation2 = createRequestReplyOperation("SUBSCRIBE test/request1", 2000L);
      AsyncMockDefinition requestReplyDef1 = new AsyncMockDefinition(service2, operation2, createEventMessages(1));

      Service service3 = createService("Service3", "1.0.0", "service-3");
      Operation operation3 = createOperation("SUBSCRIBE test/topic2", 3000L);
      AsyncMockDefinition unidirectionalDef2 = new AsyncMockDefinition(service3, operation3, createEventMessages(1));

      Service service4 = createService("Service4", "1.0.0", "service-4");
      Operation operation4 = createRequestReplyOperation("SUBSCRIBE test/request2", 4000L);
      AsyncMockDefinition requestReplyDef2 = new AsyncMockDefinition(service4, operation4, createEventMessages(1));

      repository.storeMockDefinition(unidirectionalDef1);
      repository.storeMockDefinition(requestReplyDef1);
      repository.storeMockDefinition(unidirectionalDef2);
      repository.storeMockDefinition(requestReplyDef2);

      // Execute
      Set<AsyncMockDefinition> requestReplyDefinitions = repository.getRequestReplyMockDefinitions();

      // Verify
      assertEquals(2, requestReplyDefinitions.size());
      assertTrue(requestReplyDefinitions.contains(requestReplyDef1));
      assertTrue(requestReplyDefinitions.contains(requestReplyDef2));
      assertFalse(requestReplyDefinitions.contains(unidirectionalDef1));
      assertFalse(requestReplyDefinitions.contains(unidirectionalDef2));
   }

   @Test
   void testGetRequestReplyMockDefinitions_EmptyWhenNoRequestReply() {
      // Setup - add only unidirectional definitions
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic1", 1000L);
      AsyncMockDefinition unidirectionalDef = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      repository.storeMockDefinition(unidirectionalDef);

      // Execute
      Set<AsyncMockDefinition> requestReplyDefinitions = repository.getRequestReplyMockDefinitions();

      // Verify
      assertTrue(requestReplyDefinitions.isEmpty());
   }

   @Test
   void testGetRequestReplyMockDefinitions_MultipleWithSameService() {
      // Setup - add multiple request-reply operations for same service
      Service service = createService("Service1", "1.0.0", "service-1");

      Operation operation1 = createRequestReplyOperation("SUBSCRIBE test/request1", 1000L);
      AsyncMockDefinition requestReplyDef1 = new AsyncMockDefinition(service, operation1, createEventMessages(1));

      Operation operation2 = createRequestReplyOperation("SUBSCRIBE test/request2", 2000L);
      AsyncMockDefinition requestReplyDef2 = new AsyncMockDefinition(service, operation2, createEventMessages(1));

      repository.storeMockDefinition(requestReplyDef1);
      repository.storeMockDefinition(requestReplyDef2);

      // Execute
      Set<AsyncMockDefinition> requestReplyDefinitions = repository.getRequestReplyMockDefinitions();

      // Verify
      assertEquals(2, requestReplyDefinitions.size());
      assertTrue(requestReplyDefinitions.contains(requestReplyDef1));
      assertTrue(requestReplyDefinitions.contains(requestReplyDef2));
   }

   @Test
   void testRequestReplyDefinition_IsRequestReplyTrue() {
      // Setup
      Service service = createService("Service1", "1.0.0", "service-1");
      Operation operation = createRequestReplyOperation("SUBSCRIBE test/request", 1000L);
      AsyncMockDefinition mockDef = new AsyncMockDefinition(service, operation, createEventMessages(1));

      // Verify
      assertTrue(mockDef.isRequestReply(), "Definition with ReplyInfo should be marked as request-reply");
   }

   @Test
   void testUnidirectionalDefinition_IsRequestReplyFalse() {
      // Setup
      Service service = createService("Service1", "1.0.0", "service-1");
      Operation operation = createOperation("SUBSCRIBE test/topic", 1000L);
      AsyncMockDefinition mockDef = new AsyncMockDefinition(service, operation, createEventMessages(1));

      // Verify
      assertFalse(mockDef.isRequestReply(), "Definition without ReplyInfo should not be marked as request-reply");
   }

   @Test
   void testGetMocksDefinitions_MixedRequestReplyAndUnidirectional() {
      // Setup - add a mix of definitions
      Service service1 = createService("Service1", "1.0.0", "service-1");
      Operation operation1 = createOperation("SUBSCRIBE test/topic", 1000L);
      AsyncMockDefinition unidirectionalDef = new AsyncMockDefinition(service1, operation1, createEventMessages(1));

      Service service2 = createService("Service2", "1.0.0", "service-2");
      Operation operation2 = createRequestReplyOperation("SUBSCRIBE test/request", 2000L);
      AsyncMockDefinition requestReplyDef = new AsyncMockDefinition(service2, operation2, createEventMessages(1));

      repository.storeMockDefinition(unidirectionalDef);
      repository.storeMockDefinition(requestReplyDef);

      // Execute
      Set<AsyncMockDefinition> allDefinitions = repository.getMocksDefinitions();

      // Verify
      assertEquals(2, allDefinitions.size());
      assertTrue(allDefinitions.contains(unidirectionalDef));
      assertTrue(allDefinitions.contains(requestReplyDef));
   }

   // Helper methods

   private Service createService(String name, String version, String id) {
      Service service = new Service();
      service.setName(name);
      service.setVersion(version);
      service.setId(id);
      return service;
   }

   private Operation createOperation(String name, Long defaultDelay) {
      Operation operation = new Operation();
      operation.setName(name);
      operation.setDefaultDelay(defaultDelay);
      return operation;
   }

   private Operation createRequestReplyOperation(String name, Long defaultDelay) {
      Operation operation = createOperation(name, defaultDelay);
      // Set reply info to mark this as a request-reply operation
      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress("test/reply");
      operation.setReply(replyInfo);
      return operation;
   }

   private List<EventMessage> createEventMessages(int count) {
      List<EventMessage> messages = new ArrayList<>();
      for (int i = 0; i < count; i++) {
         EventMessage message = new EventMessage();
         message.setName("message" + i);
         message.setContent("{\"data\": \"value" + i + "\"}");
         messages.add(message);
      }
      return messages;
   }
}
