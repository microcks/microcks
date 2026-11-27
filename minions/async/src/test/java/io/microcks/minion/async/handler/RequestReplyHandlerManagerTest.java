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
package io.github.microcks.minion.async.handler;

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ReplyInfo;
import io.github.microcks.domain.Service;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.producer.KafkaProducerManager;
import io.github.microcks.minion.async.SchemaRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for RequestReplyHandlerManager class. Note: This is a simple test without actual Kafka integration. Full
 * integration tests require a running Kafka instance.
 * 
 * @author adamhicks
 */
class RequestReplyHandlerManagerTest {

   private AsyncMockRepository mockRepository;
   private KafkaRequestReplyHandlerFactory kafkaHandlerFactory;
   private RequestReplyHandlerManager handlerManager;

   @BeforeEach
   void setUp() {
      // Create simple in-memory implementations for testing
      mockRepository = new AsyncMockRepository();
      kafkaHandlerFactory = new TestKafkaHandlerFactory();
      handlerManager = new RequestReplyHandlerManager(mockRepository, kafkaHandlerFactory);

      // Inject supported bindings via reflection (since it's normally from
      // ConfigProperty)
      try {
         var field = RequestReplyHandlerManager.class.getDeclaredField("supportedBindings");
         field.setAccessible(true);
         field.set(handlerManager, new String[] { "KAFKA" });
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Test
   void testStartAllHandlers_NoDefinitions() {
      // Execute
      handlerManager.startAllHandlers();

      // Verify
      assertEquals(0, handlerManager.getHandlerCount());
   }

   @Test
   void testStartAllHandlers_WithKafkaDefinition() {
      // Setup
      AsyncMockDefinition definition = createRequestReplyMockDefinition("kafka");
      mockRepository.storeMockDefinition(definition);

      // Execute
      handlerManager.startAllHandlers();

      // Verify
      assertEquals(1, handlerManager.getHandlerCount());
   }

   @Test
   void testStopHandlersForService() {
      // Setup
      AsyncMockDefinition definition1 = createRequestReplyMockDefinition("kafka");
      definition1.getOwnerService().setId("service-1");
      mockRepository.storeMockDefinition(definition1);

      AsyncMockDefinition definition2 = createRequestReplyMockDefinition("kafka");
      definition2.getOwnerService().setId("service-2");
      definition2.getOperation().setName("SUBSCRIBE test/request2");
      mockRepository.storeMockDefinition(definition2);

      handlerManager.startAllHandlers();
      assertEquals(2, handlerManager.getHandlerCount());

      // Execute - stop handlers for service-1
      handlerManager.stopHandlersForService("service-1");

      // Verify - only service-2 handler should remain
      assertEquals(1, handlerManager.getHandlerCount());
   }

   @Test
   void testStopAllHandlers() {
      // Setup
      AsyncMockDefinition definition = createRequestReplyMockDefinition("kafka");
      mockRepository.storeMockDefinition(definition);

      handlerManager.startAllHandlers();
      assertEquals(1, handlerManager.getHandlerCount());

      // Execute
      handlerManager.stopAllHandlers();

      // Verify
      assertEquals(0, handlerManager.getHandlerCount());
   }

   @Test
   void testStartHandlerForDefinition_DuplicatePrevented() {
      // Setup
      AsyncMockDefinition definition = createRequestReplyMockDefinition("kafka");

      // Execute - start twice
      handlerManager.startHandlerForDefinition(definition);
      handlerManager.startHandlerForDefinition(definition);

      // Verify - only one handler should be created
      assertEquals(1, handlerManager.getHandlerCount());
   }

   // Helper methods and test implementations

   private AsyncMockDefinition createRequestReplyMockDefinition(String binding) {
      Service service = new Service();
      service.setId("test-service-id");
      service.setName("TestService");
      service.setVersion("1.0.0");

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE test/request");

      // Add binding
      Binding bindingDef = new Binding();
      Map<String, Binding> bindings = new HashMap<>();
      bindings.put(binding.toUpperCase(), bindingDef);
      operation.setBindings(bindings);

      // Add reply info to make it a request-reply operation
      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress("test/reply");
      operation.setReply(replyInfo);

      // Create event messages
      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("request1");
      requestMessage.setContent("{\"action\": \"test\"}");

      List<EventMessage> eventMessages = List.of(requestMessage);

      return new AsyncMockDefinition(service, operation, eventMessages);
   }

   /**
    * Simple test implementation of KafkaRequestReplyHandlerFactory that creates test handlers.
    */
   private static class TestKafkaHandlerFactory extends KafkaRequestReplyHandlerFactory {
      public TestKafkaHandlerFactory() {
         super(null, null);
      }

      @Override
      public KafkaRequestReplyHandler createHandler(AsyncMockDefinition definition, Binding binding) {
         // Return a test implementation that doesn't connect to Kafka
         return new TestKafkaRequestReplyHandler(definition, binding, null, null, null);
      }
   }

   /**
    * Simple test implementation that extends KafkaRequestReplyHandler but doesn't connect to Kafka.
    */
   private static class TestKafkaRequestReplyHandler extends KafkaRequestReplyHandler {
      private boolean running = false;

      public TestKafkaRequestReplyHandler(AsyncMockDefinition mockDefinition, Binding binding,
            KafkaProducerManager producerManager, SchemaRegistry schemaRegistry,
            org.eclipse.microprofile.config.Config config) {
         super(mockDefinition, binding, producerManager, schemaRegistry, config);
      }

      @Override
      public void start() {
         // Don't actually start Kafka consumer in tests
         running = true;
      }

      @Override
      public void stop() {
         running = false;
      }

      @Override
      public boolean isRunning() {
         return running;
      }
   }
}
