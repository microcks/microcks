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
import io.github.microcks.domain.ServiceType;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.util.el.EvaluableRequest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AbstractRequestReplyHandler} protocol-agnostic logic.
 *
 * @author adamhicks
 */
class AbstractRequestReplyHandlerTest {

   @Test
   void findReplyForRequestMatchesOnJsonSubset() {
      EventMessage requestMessage = new EventMessage();
      requestMessage.setId("request-1");
      requestMessage.setName("SignupRequest");
      requestMessage.setContent("""
            {"email": "john@example.com", "username": "john"}""");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("reply-1");
      replyMessage.setName("SignupReply");
      replyMessage.setContent("""
            {"userId": "usr-123", "status": "success"}""");

      requestMessage.setReplyId("reply-1");

      TestableHandler handler = createHandler(List.of(requestMessage, replyMessage));

      EvaluableRequest incoming = new EvaluableRequest("""
            {"email": "john@example.com", "username": "john", "fullName": "John Doe"}""", null);

      EventMessage result = handler.findReplyForRequest(incoming);
      assertNotNull(result);
      assertEquals("reply-1", result.getId());
      assertTrue(result.getContent().contains("usr-123"));
   }

   @Test
   void findReplyForRequestReturnsNullOnNoMatch() {
      EventMessage requestMessage = new EventMessage();
      requestMessage.setId("request-1");
      requestMessage.setName("SignupRequest");
      requestMessage.setContent("""
            {"email": "john@example.com", "username": "john"}""");
      requestMessage.setReplyId("reply-1");

      EventMessage replyMessage = new EventMessage();
      replyMessage.setId("reply-1");
      replyMessage.setContent("""
            {"userId": "usr-123"}""");

      TestableHandler handler = createHandler(List.of(requestMessage, replyMessage));

      EvaluableRequest incoming = new EvaluableRequest("""
            {"email": "jane@example.com", "username": "jane"}""", null);

      EventMessage result = handler.findReplyForRequest(incoming);
      assertNull(result);
   }

   @Test
   void renderReplyContentWithoutTemplate() {
      EventMessage replyMessage = new EventMessage();
      replyMessage.setContent("""
            {"status": "ok"}""");

      TestableHandler handler = createHandler(List.of());
      EvaluableRequest request = new EvaluableRequest("{}", null);

      String result = handler.renderReplyContent(replyMessage, request);
      assertEquals("""
            {"status": "ok"}""", result);
   }

   @Test
   void renderReplyContentWithTemplateExpression() {
      EventMessage replyMessage = new EventMessage();
      replyMessage.setContent("""
            {"echo": "{{request.body}}"}""");

      TestableHandler handler = createHandler(List.of());
      EvaluableRequest request = new EvaluableRequest("hello", null);

      String result = handler.renderReplyContent(replyMessage, request);
      assertEquals("""
            {"echo": "hello"}""", result);
   }

   @Test
   void getReplyDestinationFromChannelAddress() {
      TestableHandler handler = createHandlerWithReplyInfo("my-reply-topic", null);

      EvaluableRequest request = new EvaluableRequest("{}", null);
      String destination = handler.getReplyDestination(request);

      assertEquals("my-reply-topic", destination);
   }

   @Test
   void getReplyDestinationFromAddressLocationThrowsUnsupportedOperation() {
      TestableHandler handler = createHandlerWithReplyInfo(null, "$message.header#/replyTo");

      EvaluableRequest request = new EvaluableRequest("{}", null);

      UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class,
            () -> handler.getReplyDestination(request));
      assertTrue(ex.getMessage().contains("addressLocation expressions are not supported"));
   }

   @Test
   void getReplyDestinationThrowsWhenNoReplyInfo() {
      Service service = new Service();
      service.setId("test-id");
      service.setName("Test");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("RECEIVE test");
      operation.setReply(null);

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, List.of());
      TestableHandler handler = new TestableHandler(definition, new Binding(BindingType.KAFKA));

      EvaluableRequest request = new EvaluableRequest("{}", null);

      IllegalStateException ex = assertThrows(IllegalStateException.class, () -> handler.getReplyDestination(request));
      assertTrue(ex.getMessage().contains("must have reply information"));
   }

   @Test
   void getReplyDestinationThrowsWhenNeitherChannelAddressNorAddressLocation() {
      TestableHandler handler = createHandlerWithReplyInfo(null, null);

      EvaluableRequest request = new EvaluableRequest("{}", null);

      IllegalStateException ex = assertThrows(IllegalStateException.class, () -> handler.getReplyDestination(request));
      assertTrue(ex.getMessage().contains("must specify a channelAddress"));
   }

   @Test
   void isRunningAndSetRunning() {
      TestableHandler handler = createHandler(List.of());

      assertFalse(handler.isRunning());
      handler.setRunning(true);
      assertTrue(handler.isRunning());
      handler.setRunning(false);
      assertFalse(handler.isRunning());
   }

   @Test
   void getMockDefinitionReturnsDefinition() {
      Service service = new Service();
      service.setId("test-id");
      service.setName("Test");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("RECEIVE test");

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, List.of());
      TestableHandler handler = new TestableHandler(definition, new Binding(BindingType.KAFKA));

      assertSame(definition, handler.getMockDefinition());
   }

   private TestableHandler createHandler(List<EventMessage> messages) {
      Service service = new Service();
      service.setId("test-id");
      service.setName("Test");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("RECEIVE test");

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress("test/reply");
      operation.setReply(replyInfo);

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, messages);
      return new TestableHandler(definition, new Binding(BindingType.KAFKA));
   }

   private TestableHandler createHandlerWithReplyInfo(String channelAddress, String addressLocation) {
      Service service = new Service();
      service.setId("test-id");
      service.setName("Test");
      service.setVersion("1.0.0");
      service.setType(ServiceType.EVENT);

      Operation operation = new Operation();
      operation.setName("RECEIVE test");

      ReplyInfo replyInfo = new ReplyInfo();
      replyInfo.setChannelAddress(channelAddress);
      replyInfo.setAddressLocation(addressLocation);
      operation.setReply(replyInfo);

      AsyncMockDefinition definition = new AsyncMockDefinition(service, operation, List.of());
      return new TestableHandler(definition, new Binding(BindingType.KAFKA));
   }

   private static class TestableHandler extends AbstractRequestReplyHandler {

      TestableHandler(AsyncMockDefinition mockDefinition, Binding binding) {
         super(mockDefinition, binding);
      }

      @Override
      public void start() throws Exception {
         setRunning(true);
      }

      @Override
      public void stop() {
         setRunning(false);
      }
   }
}
