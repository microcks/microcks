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
import io.github.microcks.domain.BindingType;
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
   private RequestReplyHandlerManager handlerManager;

   @BeforeEach
   void setUp() throws Exception {
      mockRepository = new AsyncMockRepository();
      handlerManager = new RequestReplyHandlerManager(mockRepository);

      setField(handlerManager, "supportedBindings", new String[] { "KAFKA" });

      TestKafkaHandlerFactory testFactory = new TestKafkaHandlerFactory();
      setField(handlerManager, "resolvedFactories", List.of(testFactory));
   }

   @Test
   void testStartAllHandlers_NoDefinitions() {
      handlerManager.startAllHandlers();
      assertEquals(0, handlerManager.getHandlerCount());
   }

   @Test
   void testStartAllHandlers_WithKafkaDefinition() {
      AsyncMockDefinition definition = createRequestReplyMockDefinition("KAFKA");
      mockRepository.storeMockDefinition(definition);

      handlerManager.startAllHandlers();

      assertEquals(1, handlerManager.getHandlerCount());
   }

   @Test
   void testStopHandlersForService() {
      AsyncMockDefinition definition1 = createRequestReplyMockDefinition("KAFKA");
      definition1.getOwnerService().setId("service-1");
      mockRepository.storeMockDefinition(definition1);

      AsyncMockDefinition definition2 = createRequestReplyMockDefinition("KAFKA");
      definition2.getOwnerService().setId("service-2");
      definition2.getOperation().setName("SUBSCRIBE test/request2");
      mockRepository.storeMockDefinition(definition2);

      handlerManager.startAllHandlers();
      assertEquals(2, handlerManager.getHandlerCount());

      handlerManager.stopHandlersForService("service-1");

      assertEquals(1, handlerManager.getHandlerCount());
   }

   @Test
   void testStopAllHandlers() {
      AsyncMockDefinition definition = createRequestReplyMockDefinition("KAFKA");
      mockRepository.storeMockDefinition(definition);

      handlerManager.startAllHandlers();
      assertEquals(1, handlerManager.getHandlerCount());

      handlerManager.stopAllHandlers();

      assertEquals(0, handlerManager.getHandlerCount());
   }

   @Test
   void testStartHandlerForDefinition_DuplicatePrevented() {
      AsyncMockDefinition definition = createRequestReplyMockDefinition("KAFKA");

      handlerManager.startHandlerForDefinition(definition);
      handlerManager.startHandlerForDefinition(definition);

      assertEquals(1, handlerManager.getHandlerCount());
   }

   @Test
   void testResolveFactoryReturnsCorrectFactory() throws Exception {
      RequestReplyHandlerFactory factory = invokeResolveFactory(handlerManager, BindingType.KAFKA);
      assertNotNull(factory, "Should resolve KAFKA factory");
      assertTrue(factory instanceof TestKafkaHandlerFactory, "Should be the test Kafka factory");
   }

   @Test
   void testResolveFactoryReturnsNullForUnsupportedBinding() throws Exception {
      RequestReplyHandlerFactory factory = invokeResolveFactory(handlerManager, BindingType.MQTT);
      assertNull(factory, "Should not resolve MQTT factory when none is registered");
   }

   @Test
   void testStartHandlerForDefinition_WarnsWhenNoFactoryFound() throws Exception {
      AsyncMockDefinition definition = createRequestReplyMockDefinition("MQTT");
      mockRepository.storeMockDefinition(definition);

      setField(handlerManager, "supportedBindings", new String[] { "MQTT" });

      handlerManager.startHandlerForDefinition(definition);

      assertEquals(0, handlerManager.getHandlerCount());
   }

   private AsyncMockDefinition createRequestReplyMockDefinition(String binding) {
      Service service = new Service();
      service.setId("test-service-id");
      service.setName("TestService");
      service.setVersion("1.0.0");

      Operation operation = new Operation();
      operation.setName("SUBSCRIBE test/request");

      Binding bindingDef = new Binding();
      Map<String, Binding> bindings = new HashMap<>();
      bindings.put(binding.toUpperCase(), bindingDef);
      operation.setBindings(bindings);

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress("test/reply");
      operation.setReply(replyInfo);

      EventMessage requestMessage = new EventMessage();
      requestMessage.setName("request1");
      requestMessage.setContent("{\"action\": \"test\"}");

      List<EventMessage> eventMessages = List.of(requestMessage);

      return new AsyncMockDefinition(service, operation, eventMessages);
   }

   private static void setField(Object target, String fieldName, Object value) throws Exception {
      var field = target.getClass().getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
   }

   private static RequestReplyHandlerFactory invokeResolveFactory(RequestReplyHandlerManager manager,
         BindingType bindingType) throws Exception {
      var method = RequestReplyHandlerManager.class.getDeclaredMethod("resolveFactory", BindingType.class);
      method.setAccessible(true);
      return (RequestReplyHandlerFactory) method.invoke(manager, bindingType);
   }

   @RequestReplyHandlerFactoryQualifier(BindingType.KAFKA)
   private static class TestKafkaHandlerFactory implements RequestReplyHandlerFactory {
      @Override
      public KafkaRequestReplyHandler createHandler(AsyncMockDefinition definition, Binding binding) {
         return new TestKafkaRequestReplyHandler(definition, binding, null, null, null);
      }
   }

   private static class TestKafkaRequestReplyHandler extends KafkaRequestReplyHandler {
      private boolean running = false;

      public TestKafkaRequestReplyHandler(AsyncMockDefinition mockDefinition, Binding binding,
            KafkaProducerManager producerManager, SchemaRegistry schemaRegistry,
            org.eclipse.microprofile.config.Config config) {
         super(mockDefinition, binding, producerManager, schemaRegistry, config);
      }

      @Override
      public void start() {
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
