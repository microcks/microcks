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

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.BindingType;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TriggerInfo;
import io.github.microcks.event.AsyncAPITriggerCommand;
import io.github.microcks.event.RequestSnapshot;
import io.github.microcks.event.ResponseSnapshot;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import io.github.microcks.minion.async.SchemaRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * This is a unit test case for ProducerManager logic in isolation from protocol-specific producer managers. All
 * protocol producers are mocked so that we can verify the routing, filtering, and template rendering logic that lives
 * inside ProducerManager itself.
 * @author laurent
 */
class ProducerManagerTest {

   private AsyncMockRepository mockRepository;
   private SchemaRegistry schemaRegistry;

   private KafkaProducerManager kafkaProducerManager;
   private MQTTProducerManager mqttProducerManager;
   private NATSProducerManager natsProducerManager;
   private AMQPProducerManager amqpProducerManager;
   private GooglePubSubProducerManager googlePubSubProducerManager;
   private AmazonSQSProducerManager amazonSQSProducerManager;
   private AmazonSNSProducerManager amazonSNSProducerManager;

   private ProducerManager producerManager;

   @BeforeEach
   void setUp() throws Exception {
      mockRepository = mock(AsyncMockRepository.class);
      schemaRegistry = mock(SchemaRegistry.class);

      kafkaProducerManager = mock(KafkaProducerManager.class);
      mqttProducerManager = mock(MQTTProducerManager.class);
      natsProducerManager = mock(NATSProducerManager.class);
      amqpProducerManager = mock(AMQPProducerManager.class);
      googlePubSubProducerManager = mock(GooglePubSubProducerManager.class);
      amazonSQSProducerManager = mock(AmazonSQSProducerManager.class);
      amazonSNSProducerManager = mock(AmazonSNSProducerManager.class);

      producerManager = new ProducerManager(mockRepository, schemaRegistry,
            new ProducerManager.ProducerDependencies(kafkaProducerManager, mqttProducerManager, natsProducerManager,
                  amqpProducerManager, googlePubSubProducerManager, amazonSQSProducerManager, amazonSNSProducerManager),
            null);

      // Set the supportedBindings field via reflection (normally injected by Quarkus).
      Field supportedBindingsField = ProducerManager.class.getDeclaredField("supportedBindings");
      supportedBindingsField.setAccessible(true);
      supportedBindingsField.set(producerManager,
            new String[] { "KAFKA", "MQTT", "NATS", "WS", "AMQP", "GOOGLEPUBSUB", "SQS", "SNS" });
   }

   // -----------------------------------------------------------------------
   // Tests for static utility method: getDestinationOperationPart
   // -----------------------------------------------------------------------

   @Test
   void testGetDestinationOperationPart_simpleOperationName() {
      Operation operation = new Operation();
      operation.setName("user-signedup");

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("Sample");

      String result = ProducerManager.getDestinationOperationPart(operation, eventMessage);
      assertEquals("user-signedup", result);
   }

   @Test
   void testGetDestinationOperationPart_removesSubscribeAction() {
      Operation operation = new Operation();
      operation.setName("SUBSCRIBE user-signedup");

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("Sample");

      String result = ProducerManager.getDestinationOperationPart(operation, eventMessage);
      assertEquals("user-signedup", result);
   }

   @Test
   void testGetDestinationOperationPart_removesPublishAction() {
      Operation operation = new Operation();
      operation.setName("PUBLISH user-signedup");

      EventMessage eventMessage = new EventMessage();
      String result = ProducerManager.getDestinationOperationPart(operation, eventMessage);
      assertEquals("user-signedup", result);
   }

   @Test
   void testGetDestinationOperationPart_removesSendAction() {
      Operation operation = new Operation();
      operation.setName("SEND user-signedup");

      EventMessage eventMessage = new EventMessage();
      String result = ProducerManager.getDestinationOperationPart(operation, eventMessage);
      assertEquals("user-signedup", result);
   }

   @Test
   void testGetDestinationOperationPart_removesReceiveAction() {
      Operation operation = new Operation();
      operation.setName("RECEIVE receiveLightMeasurement");

      EventMessage eventMessage = new EventMessage();
      String result = ProducerManager.getDestinationOperationPart(operation, eventMessage);
      assertEquals("receiveLightMeasurement", result);
   }

   @Test
   void testGetDestinationOperationPart_uriPartsDispatcher() {
      Operation operation = new Operation();
      operation.setName("RECEIVE receiveLightMeasurement");
      operation.setDispatcher("URI_PARTS");
      operation.setResourcePaths(Set.of("smartylighting.streetlights.1.0.event.{streetlightId}.lighting.measured",
            "smartylighting.streetlights.1.0.event.abc123.lighting.measured"));

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("Sample");
      eventMessage.setDispatchCriteria("streetlightId=abc123");

      String result = ProducerManager.getDestinationOperationPart(operation, eventMessage);
      assertEquals("smartylighting.streetlights.1.0.event.abc123.lighting.measured", result);
   }

   @Test
   void testGetDestinationOperationPart_uriPartsMultiplePlaceholders() {
      Operation operation = new Operation();
      operation.setName("SUBSCRIBE my-channel");
      operation.setDispatcher("URI_PARTS");
      operation.setResourcePaths(Set.of("events.{region}.{env}"));

      EventMessage eventMessage = new EventMessage();
      eventMessage.setDispatchCriteria("region=eu-west/env=prod");

      String result = ProducerManager.getDestinationOperationPart(operation, eventMessage);
      assertEquals("events.eu-west.prod", result);
   }

   @Test
   void testGetDestinationOperationPart_uriPartsWithNullDispatchCriteria() {
      Operation operation = new Operation();
      operation.setName("my-channel");
      operation.setDispatcher("URI_PARTS");
      operation.setResourcePaths(Set.of("events.{region}"));

      EventMessage eventMessage = new EventMessage();
      eventMessage.setDispatchCriteria(null);

      // Placeholders should remain unresolved when dispatch criteria is null.
      String result = ProducerManager.getDestinationOperationPart(operation, eventMessage);
      assertEquals("events.{region}", result);
   }

   // -----------------------------------------------------------------------
   // Tests for routing logic: produceAsyncMockMessagesAt
   // -----------------------------------------------------------------------

   @Test
   void testProduceAsyncMockMessagesAt_routesToKafka() {
      AsyncMockDefinition definition = buildMockDefinition("KAFKA", "Simple static content");
      when(mockRepository.getMockDefinitionsByFrequency(10L)).thenReturn(Set.of(definition));
      when(kafkaProducerManager.getTopicName(any(), any())).thenReturn("test-topic");

      producerManager.produceAsyncMockMessagesAt(10L);

      verify(kafkaProducerManager).getTopicName(eq(definition), any(EventMessage.class));
      verify(kafkaProducerManager).publishMessage(eq("test-topic"), anyString(), eq("Simple static content"), any());
      // Ensure other producers were NOT called.
      verifyNoInteractions(mqttProducerManager, natsProducerManager, amqpProducerManager, googlePubSubProducerManager,
            amazonSQSProducerManager, amazonSNSProducerManager);
   }

   @Test
   void testProduceAsyncMockMessagesAt_routesToMQTT() {
      AsyncMockDefinition definition = buildMockDefinition("MQTT", "MQTT content");
      when(mockRepository.getMockDefinitionsByFrequency(20L)).thenReturn(Set.of(definition));
      when(mqttProducerManager.getTopicName(any(), any())).thenReturn("mqtt-topic");

      producerManager.produceAsyncMockMessagesAt(20L);

      verify(mqttProducerManager).publishMessage(eq("mqtt-topic"), eq("MQTT content"));
      verifyNoInteractions(kafkaProducerManager, natsProducerManager);
   }

   @Test
   void testProduceAsyncMockMessagesAt_routesToNATS() {
      AsyncMockDefinition definition = buildMockDefinition("NATS", "NATS content");
      when(mockRepository.getMockDefinitionsByFrequency(30L)).thenReturn(Set.of(definition));
      when(natsProducerManager.getTopicName(any(), any())).thenReturn("nats-topic");

      producerManager.produceAsyncMockMessagesAt(30L);

      verify(natsProducerManager).publishMessage(eq("nats-topic"), eq("NATS content"), any());
      verifyNoInteractions(kafkaProducerManager, mqttProducerManager);
   }

   @Test
   void testProduceAsyncMockMessagesAt_routesToAMQP() {
      AsyncMockDefinition definition = buildMockDefinition("AMQP", "AMQP content");
      // Set binding details needed for AMQP.
      Binding amqpBinding = definition.getOperation().getBindings().get("AMQP");
      amqpBinding.setDestinationType("queue");
      amqpBinding.setRoutingKey("my-routing-key");

      when(mockRepository.getMockDefinitionsByFrequency(40L)).thenReturn(Set.of(definition));
      when(amqpProducerManager.getDestinationName(any(), any())).thenReturn("amqp-queue");

      producerManager.produceAsyncMockMessagesAt(40L);

      verify(amqpProducerManager).publishMessage(eq("queue"), eq("amqp-queue"), eq("my-routing-key"),
            eq("AMQP content"), any());
   }

   @Test
   void testProduceAsyncMockMessagesAt_routesToGooglePubSub() {
      AsyncMockDefinition definition = buildMockDefinition("GOOGLEPUBSUB", "PubSub content");
      when(mockRepository.getMockDefinitionsByFrequency(50L)).thenReturn(Set.of(definition));
      when(googlePubSubProducerManager.getTopicName(any(), any())).thenReturn("pubsub-topic");

      producerManager.produceAsyncMockMessagesAt(50L);

      verify(googlePubSubProducerManager).publishMessage(eq("pubsub-topic"), eq("PubSub content"), anyMap());
   }

   @Test
   void testProduceAsyncMockMessagesAt_routesToSQS() {
      AsyncMockDefinition definition = buildMockDefinition("SQS", "SQS content");
      when(mockRepository.getMockDefinitionsByFrequency(60L)).thenReturn(Set.of(definition));
      when(amazonSQSProducerManager.getQueueName(any(), any())).thenReturn("sqs-queue");

      producerManager.produceAsyncMockMessagesAt(60L);

      verify(amazonSQSProducerManager).publishMessage(eq("sqs-queue"), eq("SQS content"), anyMap());
   }

   @Test
   void testProduceAsyncMockMessagesAt_routesToSNS() {
      AsyncMockDefinition definition = buildMockDefinition("SNS", "SNS content");
      when(mockRepository.getMockDefinitionsByFrequency(70L)).thenReturn(Set.of(definition));
      when(amazonSNSProducerManager.getTopicName(any(), any())).thenReturn("sns-topic");

      producerManager.produceAsyncMockMessagesAt(70L);

      verify(amazonSNSProducerManager).publishMessage(eq("sns-topic"), eq("SNS content"), anyMap());
   }

   @Test
   void testProduceAsyncMockMessagesAt_unsupportedBindingIsIgnored() throws Exception {
      // Override supportedBindings to only include KAFKA.
      Field supportedBindingsField = ProducerManager.class.getDeclaredField("supportedBindings");
      supportedBindingsField.setAccessible(true);
      supportedBindingsField.set(producerManager, new String[] { "KAFKA" });

      AsyncMockDefinition definition = buildMockDefinition("MQTT", "Should not be sent");
      when(mockRepository.getMockDefinitionsByFrequency(10L)).thenReturn(Set.of(definition));

      producerManager.produceAsyncMockMessagesAt(10L);

      // MQTT binding is not supported, so nothing should be called.
      verifyNoInteractions(mqttProducerManager, kafkaProducerManager);
   }

   // -----------------------------------------------------------------------
   // Tests for message filtering: pure vs contextualized messages
   // -----------------------------------------------------------------------

   @Test
   void testProduceAsyncMockMessagesAt_filtersPureMessagesOnly() {
      // Build a definition with mixed messages: one pure, one contextualized.
      Service service = new Service();
      service.setId("svc-1");
      service.setName("TestService");
      service.setVersion("1.0.0");

      Operation operation = new Operation();
      operation.setName("user-signedup");
      operation.setBindings(Map.of("KAFKA", new Binding(BindingType.KAFKA)));
      service.addOperation(operation);

      EventMessage pureMessage = new EventMessage();
      pureMessage.setName("Pure");
      pureMessage.setContent("{\"user\": \"john\"}");
      pureMessage.setMediaType("application/json");

      EventMessage contextualizedMessage = new EventMessage();
      contextualizedMessage.setName("Contextualized");
      contextualizedMessage.setContent("{\"echo\": \"{{ request.body }}\"}");
      contextualizedMessage.setMediaType("application/json");

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation,
            List.of(pureMessage, contextualizedMessage));
      when(mockRepository.getMockDefinitionsByFrequency(10L)).thenReturn(Set.of(definition));
      when(kafkaProducerManager.getTopicName(any(), any())).thenReturn("test-topic");

      producerManager.produceAsyncMockMessagesAt(10L);

      // Only the pure message should be published (not containing "request." or "response.").
      verify(kafkaProducerManager, times(1)).publishMessage(anyString(), anyString(), anyString(), any());
      verify(kafkaProducerManager).publishMessage(anyString(), anyString(), eq("{\"user\": \"john\"}"), any());
   }

   @Test
   void testProduceAsyncMockMessagesAt_noMessagesWhenAllContextualized() {
      Service service = new Service();
      service.setId("svc-2");
      service.setName("TestService");
      service.setVersion("1.0.0");

      Operation operation = new Operation();
      operation.setName("event-op");
      operation.setBindings(Map.of("NATS", new Binding(BindingType.NATS)));
      service.addOperation(operation);

      EventMessage ctxMsg1 = new EventMessage();
      ctxMsg1.setName("Ctx1");
      ctxMsg1.setContent("{\"data\": \"{{ request.body }}\"}");
      ctxMsg1.setMediaType("application/json");

      EventMessage ctxMsg2 = new EventMessage();
      ctxMsg2.setName("Ctx2");
      ctxMsg2.setContent("{\"result\": \"{{ response.body }}\"}");
      ctxMsg2.setMediaType("application/json");

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, List.of(ctxMsg1, ctxMsg2));
      when(mockRepository.getMockDefinitionsByFrequency(10L)).thenReturn(Set.of(definition));

      producerManager.produceAsyncMockMessagesAt(10L);

      // No pure messages, so nothing should be published.
      verifyNoInteractions(natsProducerManager);
   }

   @Test
   void testProduceAsyncMockMessagesAt_ignoresNullContentMessages() {
      Service service = new Service();
      service.setId("svc-null");
      service.setName("TestService");
      service.setVersion("1.0.0");

      Operation operation = new Operation();
      operation.setName("event-op");
      operation.setBindings(Map.of("KAFKA", new Binding(BindingType.KAFKA)));
      service.addOperation(operation);

      EventMessage nullContentMessage = new EventMessage();
      nullContentMessage.setName("NullContent");
      nullContentMessage.setMediaType("application/json");

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, List.of(nullContentMessage));
      when(mockRepository.getMockDefinitionsByFrequency(10L)).thenReturn(Set.of(definition));

      assertDoesNotThrow(() -> producerManager.produceAsyncMockMessagesAt(10L));
      verifyNoInteractions(kafkaProducerManager);
   }

   // -----------------------------------------------------------------------
   // Tests for template rendering (renderEventMessageContent)
   // -----------------------------------------------------------------------

   @Test
   void testProduceAsyncMockMessagesAt_staticContentPassedAsIs() {
      AsyncMockDefinition definition = buildMockDefinition("KAFKA", "{\"name\": \"World\"}");
      when(mockRepository.getMockDefinitionsByFrequency(10L)).thenReturn(Set.of(definition));
      when(kafkaProducerManager.getTopicName(any(), any())).thenReturn("topic");

      producerManager.produceAsyncMockMessagesAt(10L);

      verify(kafkaProducerManager).publishMessage(eq("topic"), anyString(), eq("{\"name\": \"World\"}"), any());
   }

   @Test
   void testProduceAsyncMockMessagesAt_templateExpressionIsRendered() {
      // Use the {{ randomInt(1,100) }} expression that should be rendered by the template engine.
      AsyncMockDefinition definition = buildMockDefinition("KAFKA", "{\"id\": {{ randomInt(1, 100) }}}");
      when(mockRepository.getMockDefinitionsByFrequency(10L)).thenReturn(Set.of(definition));
      when(kafkaProducerManager.getTopicName(any(), any())).thenReturn("topic");

      producerManager.produceAsyncMockMessagesAt(10L);

      // Capture the rendered content to verify it has been processed.
      verify(kafkaProducerManager).publishMessage(eq("topic"), anyString(),
            argThat((String content) -> !content.contains("randomInt") && content.startsWith("{\"id\": ")), any());
   }

   // -----------------------------------------------------------------------
   // Tests for trigger logic: triggerAsyncMockMessages
   // -----------------------------------------------------------------------

   @Test
   void testTriggerAsyncMockMessages_routesToCorrectBinding() {
      // Build mock definition with a contextualized message containing "request.".
      Service service = new Service();
      service.setId("svc-trigger");
      service.setName("TriggerService");
      service.setVersion("2.0.0");

      Operation asyncOperation = new Operation();
      asyncOperation.setName("order-placed");
      asyncOperation.setBindings(Map.of("KAFKA", new Binding(BindingType.KAFKA)));
      service.addOperation(asyncOperation);

      EventMessage ctxMessage = new EventMessage();
      ctxMessage.setName("CtxMsg");
      ctxMessage.setContent("{\"orderId\": \"{{ request.body/orderId }}\"}");
      ctxMessage.setMediaType("application/json");

      AsyncMockDefinition definition = new AsyncMockDefinition(service, asyncOperation, List.of(ctxMessage));
      when(mockRepository.getMockDefinitionsByServiceAndVersion("TriggerService", "2.0.0"))
            .thenReturn(Set.of(definition));
      when(kafkaProducerManager.getTopicName(any(), any())).thenReturn("trigger-topic");

      // Build the trigger command.
      Operation triggerOperation = new Operation();
      triggerOperation.setName("POST /orders");
      TriggerInfo triggerInfo = new TriggerInfo("TriggerService", "2.0.0", "order-placed");
      triggerOperation.setTriggerInfos(List.of(triggerInfo));

      RequestSnapshot request = new RequestSnapshot("/orders", Map.of("Content-Type", List.of("application/json")),
            Collections.emptyMap(), "{\"orderId\": \"12345\"}");
      ResponseSnapshot response = new ResponseSnapshot(Map.of(), "{\"status\": \"created\"}");

      AsyncAPITriggerCommand command = new AsyncAPITriggerCommand("svc-trigger", triggerOperation, request, response,
            System.currentTimeMillis());

      producerManager.triggerAsyncMockMessages(command);

      // The contextualized message should be published for the Kafka binding.
      verify(kafkaProducerManager).publishMessage(eq("trigger-topic"), anyString(), anyString(), any());
   }

   @Test
   void testTriggerAsyncMockMessages_ignoresPureMessages() {
      // Build mock definition with a pure message (no "request." or "response.").
      Service service = new Service();
      service.setId("svc-pure");
      service.setName("PureService");
      service.setVersion("1.0.0");

      Operation asyncOperation = new Operation();
      asyncOperation.setName("notification");
      asyncOperation.setBindings(Map.of("MQTT", new Binding(BindingType.MQTT)));
      service.addOperation(asyncOperation);

      EventMessage pureMsg = new EventMessage();
      pureMsg.setName("PureMsg");
      pureMsg.setContent("{\"msg\": \"hello\"}");
      pureMsg.setMediaType("application/json");

      AsyncMockDefinition definition = new AsyncMockDefinition(service, asyncOperation, List.of(pureMsg));
      when(mockRepository.getMockDefinitionsByServiceAndVersion("PureService", "1.0.0")).thenReturn(Set.of(definition));

      Operation triggerOperation = new Operation();
      triggerOperation.setName("POST /notify");
      TriggerInfo triggerInfo = new TriggerInfo("PureService", "1.0.0", "notification");
      triggerOperation.setTriggerInfos(List.of(triggerInfo));

      RequestSnapshot request = new RequestSnapshot("{}");
      ResponseSnapshot response = new ResponseSnapshot("{}");
      AsyncAPITriggerCommand command = new AsyncAPITriggerCommand("svc-pure", triggerOperation, request, response,
            System.currentTimeMillis());

      producerManager.triggerAsyncMockMessages(command);

      // Pure messages should NOT be triggered (only contextualized messages are).
      verifyNoInteractions(mqttProducerManager);
   }

   @Test
   void testTriggerAsyncMockMessages_noDefinitionFoundDoesNothing() {
      when(mockRepository.getMockDefinitionsByServiceAndVersion(anyString(), anyString()))
            .thenReturn(Collections.emptySet());

      Operation triggerOperation = new Operation();
      triggerOperation.setName("POST /something");
      TriggerInfo triggerInfo = new TriggerInfo("UnknownService", "1.0.0", "some-op");
      triggerOperation.setTriggerInfos(List.of(triggerInfo));

      RequestSnapshot request = new RequestSnapshot("{}");
      ResponseSnapshot response = new ResponseSnapshot("{}");
      AsyncAPITriggerCommand command = new AsyncAPITriggerCommand("unknown-svc", triggerOperation, request, response,
            System.currentTimeMillis());

      // Should complete without errors, no producer invoked.
      producerManager.triggerAsyncMockMessages(command);

      verifyNoInteractions(kafkaProducerManager, mqttProducerManager, natsProducerManager, amqpProducerManager,
            googlePubSubProducerManager, amazonSQSProducerManager, amazonSNSProducerManager);
   }

   // -----------------------------------------------------------------------
   // Tests for multiple messages publishing
   // -----------------------------------------------------------------------

   @Test
   void testProduceAsyncMockMessagesAt_publishesAllPureMessages() {
      Service service = new Service();
      service.setId("svc-multi");
      service.setName("MultiService");
      service.setVersion("1.0.0");

      Operation operation = new Operation();
      operation.setName("multi-event");
      operation.setBindings(Map.of("KAFKA", new Binding(BindingType.KAFKA)));
      service.addOperation(operation);

      EventMessage msg1 = new EventMessage();
      msg1.setName("Msg1");
      msg1.setContent("{\"seq\": 1}");
      msg1.setMediaType("application/json");

      EventMessage msg2 = new EventMessage();
      msg2.setName("Msg2");
      msg2.setContent("{\"seq\": 2}");
      msg2.setMediaType("application/json");

      EventMessage msg3 = new EventMessage();
      msg3.setName("Msg3");
      msg3.setContent("{\"seq\": 3}");
      msg3.setMediaType("application/json");

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, List.of(msg1, msg2, msg3));
      when(mockRepository.getMockDefinitionsByFrequency(10L)).thenReturn(Set.of(definition));
      when(kafkaProducerManager.getTopicName(any(), any())).thenReturn("topic");

      producerManager.produceAsyncMockMessagesAt(10L);

      // All 3 pure messages should be published.
      verify(kafkaProducerManager, times(3)).publishMessage(eq("topic"), anyString(), anyString(), any());
   }

   // -----------------------------------------------------------------------
   // Helper methods
   // -----------------------------------------------------------------------

   /**
    * Build a simple AsyncMockDefinition with one pure event message and a single binding.
    */
   private AsyncMockDefinition buildMockDefinition(String bindingName, String messageContent) {
      Service service = new Service();
      service.setId("test-svc-" + bindingName);
      service.setName("TestService");
      service.setVersion("1.0.0");

      Operation operation = new Operation();
      operation.setName("test-operation");
      operation.setBindings(Map.of(bindingName, new Binding(BindingType.valueOf(bindingName))));
      service.addOperation(operation);

      EventMessage eventMessage = new EventMessage();
      eventMessage.setName("TestMessage");
      eventMessage.setContent(messageContent);
      eventMessage.setMediaType("application/json");

      return new AsyncMockDefinition(service, operation, List.of(eventMessage));
   }
}

