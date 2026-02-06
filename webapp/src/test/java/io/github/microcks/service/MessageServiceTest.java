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
package io.github.microcks.service;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.RequestReplyEvent;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.repository.EventMessageRepository;
import io.github.microcks.repository.RepositoryTestsConfiguration;
import io.github.microcks.util.IdBuilder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for MessageService class.
 * @author adamhicks
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringJUnitConfig(classes = RepositoryTestsConfiguration.class)
@TestPropertySource(locations = { "classpath:/config/test.properties" })
class MessageServiceTest {

   @Autowired
   private MessageService messageService;

   @Autowired
   private EventMessageRepository eventMessageRepository;

   @Test
   void testGetEventByOperation_UnidirectionalEvents() {
      // Setup: Create service and operation
      Service service = new Service();
      service.setName("Test Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE test/topic");
      service.addOperation(operation);

      String operationId = IdBuilder.buildOperationId(service, operation);

      // Create unidirectional event messages (no replyId)
      EventMessage event1 = new EventMessage();
      event1.setName("event1");
      event1.setContent("{\"data\": \"test1\"}");
      event1.setOperationId(operationId);
      eventMessageRepository.save(event1);

      EventMessage event2 = new EventMessage();
      event2.setName("event2");
      event2.setContent("{\"data\": \"test2\"}");
      event2.setOperationId(operationId);
      eventMessageRepository.save(event2);

      // Execute
      List<? extends Exchange> results = messageService.getEventByOperation(operationId);

      // Verify
      assertNotNull(results);
      assertEquals(2, results.size());
      assertTrue(results.get(0) instanceof UnidirectionalEvent);
      assertTrue(results.get(1) instanceof UnidirectionalEvent);

      UnidirectionalEvent unidirectional1 = (UnidirectionalEvent) results.get(0);
      assertEquals("event1", unidirectional1.getEventMessage().getName());

      UnidirectionalEvent unidirectional2 = (UnidirectionalEvent) results.get(1);
      assertEquals("event2", unidirectional2.getEventMessage().getName());
   }

   @Test
   void testGetEventByOperation_RequestReplyEvents() {
      // Setup: Create service and operation
      Service service = new Service();
      service.setName("Test Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE test/request-reply");
      service.addOperation(operation);

      String operationId = IdBuilder.buildOperationId(service, operation);

      // Create reply message first
      EventMessage replyMessage = new EventMessage();
      replyMessage.setName("reply1");
      replyMessage.setContent("{\"result\": \"success\"}");
      replyMessage.setOperationId(operationId);
      eventMessageRepository.save(replyMessage);

      // Create request message with replyId
      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("request1");
      requestMessage.setContent("{\"action\": \"process\"}");
      requestMessage.setOperationId(operationId);
      requestMessage.setReplyId(replyMessage.getId());
      eventMessageRepository.save(requestMessage);

      // Execute
      List<? extends Exchange> results = messageService.getEventByOperation(operationId);

      // Verify
      assertNotNull(results);
      assertEquals(1, results.size());
      assertTrue(results.get(0) instanceof RequestReplyEvent);

      RequestReplyEvent requestReply = (RequestReplyEvent) results.get(0);
      assertEquals("request1", requestReply.getRequestMessage().getName());
      assertEquals("reply1", requestReply.getReplyMessage().getName());
      assertEquals("{\"action\": \"process\"}", requestReply.getRequestMessage().getContent());
      assertEquals("{\"result\": \"success\"}", requestReply.getReplyMessage().getContent());
   }

   @Test
   void testGetEventByOperation_ReplyBeforeRequest() {
      // Setup: Create service and operation
      Service service = new Service();
      service.setName("Test Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE test/order");
      service.addOperation(operation);

      String operationId = IdBuilder.buildOperationId(service, operation);

      // Create and save reply message FIRST
      EventMessage replyMessage = new EventMessage();
      replyMessage.setName("orderReply");
      replyMessage.setContent("{\"status\": \"confirmed\"}");
      replyMessage.setOperationId(operationId);
      eventMessageRepository.save(replyMessage);

      // Create and save request message AFTER with reference to reply
      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("orderRequest");
      requestMessage.setContent("{\"order\": \"123\"}");
      requestMessage.setOperationId(operationId);
      requestMessage.setReplyId(replyMessage.getId());
      eventMessageRepository.save(requestMessage);

      // Execute
      List<? extends Exchange> results = messageService.getEventByOperation(operationId);

      // Verify - should still create RequestReplyEvent correctly
      assertNotNull(results);
      assertEquals(1, results.size());
      assertTrue(results.get(0) instanceof RequestReplyEvent);

      RequestReplyEvent requestReply = (RequestReplyEvent) results.get(0);
      assertEquals("orderRequest", requestReply.getRequestMessage().getName());
      assertEquals("orderReply", requestReply.getReplyMessage().getName());
   }

   @Test
   void testGetEventByOperation_MissingReply() {
      // Setup: Create service and operation
      Service service = new Service();
      service.setName("Test Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE test/missing-reply");
      service.addOperation(operation);

      String operationId = IdBuilder.buildOperationId(service, operation);

      // Create request message with replyId that doesn't exist
      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("requestWithMissingReply");
      requestMessage.setContent("{\"action\": \"test\"}");
      requestMessage.setOperationId(operationId);
      requestMessage.setReplyId("non-existent-id-12345");
      eventMessageRepository.save(requestMessage);

      // Execute
      List<? extends Exchange> results = messageService.getEventByOperation(operationId);

      // Verify - should skip the message and return empty list
      assertNotNull(results);
      assertEquals(0, results.size());
   }

   @Test
   void testGetEventByOperation_MixedEvents() {
      // Setup: Create service and operation
      Service service = new Service();
      service.setName("Test Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE test/mixed");
      service.addOperation(operation);

      String operationId = IdBuilder.buildOperationId(service, operation);

      // Create unidirectional event
      EventMessage unidirectional1 = new EventMessage();
      unidirectional1.setName("notification");
      unidirectional1.setContent("{\"type\": \"info\"}");
      unidirectional1.setOperationId(operationId);
      eventMessageRepository.save(unidirectional1);

      // Create reply message
      EventMessage replyMessage = new EventMessage();
      replyMessage.setName("commandReply");
      replyMessage.setContent("{\"result\": \"ok\"}");
      replyMessage.setOperationId(operationId);
      eventMessageRepository.save(replyMessage);

      // Create request message with replyId
      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("commandRequest");
      requestMessage.setContent("{\"command\": \"execute\"}");
      requestMessage.setOperationId(operationId);
      requestMessage.setReplyId(replyMessage.getId());
      eventMessageRepository.save(requestMessage);

      // Create another unidirectional event
      EventMessage unidirectional2 = new EventMessage();
      unidirectional2.setName("alert");
      unidirectional2.setContent("{\"type\": \"warning\"}");
      unidirectional2.setOperationId(operationId);
      eventMessageRepository.save(unidirectional2);

      // Execute
      List<? extends Exchange> results = messageService.getEventByOperation(operationId);

      // Verify - should have 3 exchanges: 2 unidirectional and 1 request-reply
      assertNotNull(results);
      assertEquals(3, results.size());

      // Count types
      long unidirectionalCount = results.stream().filter(e -> e instanceof UnidirectionalEvent).count();
      long requestReplyCount = results.stream().filter(e -> e instanceof RequestReplyEvent).count();

      assertEquals(2, unidirectionalCount);
      assertEquals(1, requestReplyCount);

      // Verify the request-reply event
      RequestReplyEvent requestReply = results.stream().filter(e -> e instanceof RequestReplyEvent)
            .map(e -> (RequestReplyEvent) e).findFirst().orElse(null);

      assertNotNull(requestReply);
      assertEquals("commandRequest", requestReply.getRequestMessage().getName());
      assertEquals("commandReply", requestReply.getReplyMessage().getName());
   }

   @Test
   void testGetEventByTestCase_RequestReplyEvents() {
      // Setup: Create test case ID
      String testCaseId = "test-123-456";

      // Create reply message
      EventMessage replyMessage = new EventMessage();
      replyMessage.setName("testReply");
      replyMessage.setContent("{\"result\": \"pass\"}");
      replyMessage.setTestCaseId(testCaseId);
      eventMessageRepository.save(replyMessage);

      // Create request message with replyId
      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("testRequest");
      requestMessage.setContent("{\"test\": \"data\"}");
      requestMessage.setTestCaseId(testCaseId);
      requestMessage.setReplyId(replyMessage.getId());
      eventMessageRepository.save(requestMessage);

      // Execute
      List<? extends Exchange> results = messageService.getEventByTestCase(testCaseId);

      // Verify
      assertNotNull(results);
      assertEquals(1, results.size());
      assertTrue(results.get(0) instanceof RequestReplyEvent);

      RequestReplyEvent requestReply = (RequestReplyEvent) results.get(0);
      assertEquals("testRequest", requestReply.getRequestMessage().getName());
      assertEquals("testReply", requestReply.getReplyMessage().getName());
   }

   @Test
   void testGetEventByOperation_MultipleRequestReplyPairs() {
      // Setup: Create service and operation
      Service service = new Service();
      service.setName("Test Service");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE test/multiple-pairs");
      service.addOperation(operation);

      String operationId = IdBuilder.buildOperationId(service, operation);

      // Create first pair
      EventMessage reply1 = new EventMessage();
      reply1.setName("reply1");
      reply1.setContent("{\"result\": \"1\"}");
      reply1.setOperationId(operationId);
      eventMessageRepository.save(reply1);

      EventMessage request1 = new EventMessage();
      request1.setName("request1");
      request1.setContent("{\"action\": \"1\"}");
      request1.setOperationId(operationId);
      request1.setReplyId(reply1.getId());
      eventMessageRepository.save(request1);

      // Create second pair
      EventMessage reply2 = new EventMessage();
      reply2.setName("reply2");
      reply2.setContent("{\"result\": \"2\"}");
      reply2.setOperationId(operationId);
      eventMessageRepository.save(reply2);

      EventMessage request2 = new EventMessage();
      request2.setName("request2");
      request2.setContent("{\"action\": \"2\"}");
      request2.setOperationId(operationId);
      request2.setReplyId(reply2.getId());
      eventMessageRepository.save(request2);

      // Execute
      List<? extends Exchange> results = messageService.getEventByOperation(operationId);

      // Verify
      assertNotNull(results);
      assertEquals(2, results.size());
      assertTrue(results.get(0) instanceof RequestReplyEvent);
      assertTrue(results.get(1) instanceof RequestReplyEvent);

      // Verify both pairs are correctly formed
      for (Exchange exchange : results) {
         RequestReplyEvent requestReply = (RequestReplyEvent) exchange;
         assertNotNull(requestReply.getRequestMessage());
         assertNotNull(requestReply.getReplyMessage());
         assertTrue(requestReply.getRequestMessage().getName().startsWith("request"));
         assertTrue(requestReply.getReplyMessage().getName().startsWith("reply"));
      }
   }
}
