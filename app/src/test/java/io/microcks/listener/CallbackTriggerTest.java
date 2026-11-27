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
package io.github.microcks.listener;

import io.github.microcks.domain.CallbackInfo;
import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Parameter;
import io.github.microcks.domain.Request;
import io.github.microcks.event.CallbackTriggerEvent;
import io.github.microcks.event.HttpServletRequestSnapshot;
import io.github.microcks.repository.RequestRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * This is a test case for the CallbackTrigger class.
 * @author laurent
 */
@ExtendWith(MockitoExtension.class)
class CallbackTriggerTest {

   @Mock
   private RequestRepository requestRepository;

   @Mock
   private ApplicationContext applicationContext;

   @InjectMocks
   private CallbackTrigger callbackTrigger;

   @BeforeEach
   void setup() {
      callbackTrigger.defaultDelay = 250;
   }

   @Test
   void testOnApplicationEventWithNoCallbackInfos() {
      // Operation with no callback infos.
      Operation operation = new Operation();
      operation.setName("POST /test");

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/test", Map.of(), Map.of(), null);
      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot);

      // Should return early without calling the repository.
      callbackTrigger.onApplicationEvent(event);

      verifyNoInteractions(requestRepository);
      verifyNoInteractions(applicationContext);
   }

   @Test
   void testOnApplicationEventWithEmptyCallbackInfos() {
      // Operation with empty callback infos map.
      Operation operation = new Operation();
      operation.setName("POST /test");
      operation.setCallbackInfso(new HashMap<>());

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/test", Map.of(), Map.of(), null);
      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot);

      callbackTrigger.onApplicationEvent(event);

      verifyNoInteractions(requestRepository);
      verifyNoInteractions(applicationContext);
   }

   @Test
   void testOnApplicationEventExtractsUrlFromQueryParameter() {
      // Setup operation with callback info using query parameter expression.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      // Setup snapshot with query parameter.
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      // Setup callback request to be returned by repository.
      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback request");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"status\":\"done\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot);

      // Execute - this will trigger the callback sending asynchronously.
      // The Thread.sleep(3s) is part of the implementation, we interrupt the thread to speed up.
      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         // Give it time to reach the repository call, then let it finish.
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      // Verify the repository was called with correct operationId.
      verify(requestRepository).findByOperationIdAndName(eq("serviceId-POST /subscribe"), eq("response"));
   }

   @Test
   void testOnApplicationEventExtractsUrlFromHeader() {
      // Setup operation with callback info using header expression.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.header.X-Callback-Url}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      // Setup snapshot with header value.
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe",
            Map.of("X-Callback-Url", List.of("http://consumer.example.com/callback")), Map.of(), null);

      // Setup callback request.
      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback request");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"status\":\"done\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      // Verify the repository was called.
      verify(requestRepository).findByOperationIdAndName(eq("serviceId-POST /subscribe"), eq("response"));
   }

   @Test
   void testOnApplicationEventExtractsUrlFromBody() {
      // Setup operation with callback info using body JSON pointer expression.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.body#/callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      // Setup snapshot with body containing callbackUrl.
      String body = "{\"callbackUrl\":\"http://consumer.example.com/callback\",\"data\":\"test\"}";
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(), Map.of(), body);

      // Setup callback request.
      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback request");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"status\":\"done\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      // Verify the repository was called.
      verify(requestRepository).findByOperationIdAndName(eq("serviceId-POST /subscribe"), eq("response"));
   }

   @Test
   void testOnApplicationEventNoCallbackUrlFound() {
      // Setup operation with callback info using query parameter but no matching param in request.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      // Snapshot without the expected query parameter.
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(), Map.of(), null);

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot);

      // Should log warning and not call the repository.
      callbackTrigger.onApplicationEvent(event);

      verifyNoInteractions(requestRepository);
   }

   @Test
   void testOnApplicationEventNoMatchingCallbackRequest() {
      // Setup operation with callback info.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      // Return requests without matching callbackName.
      Request otherRequest = new Request();
      otherRequest.setName("other request");
      otherRequest.setCallbackName("otherCallback");
      otherRequest.setContent("{\"data\":\"other\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(otherRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot);

      callbackTrigger.onApplicationEvent(event);

      // Should call repository but no further action since no matching callback request.
      verify(requestRepository).findByOperationIdAndName(anyString(), anyString());
      verifyNoInteractions(applicationContext);
   }

   @Test
   void testOnApplicationEventSelectsCallbackByOrder() {
      // Setup operation with multiple callback infos ordered.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");

      CallbackInfo firstCallback = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      firstCallback.setOrder(1);

      CallbackInfo secondCallback = new CallbackInfo("{$request.header.X-Callback}", "PUT");
      secondCallback.setOrder(2);

      // Use LinkedHashMap to have predictable insertion order (different from sort order).
      Map<String, CallbackInfo> callbackInfos = new LinkedHashMap<>();
      callbackInfos.put("secondEvent", secondCallback);
      callbackInfos.put("firstEvent", firstCallback);
      operation.setCallbackInfso(callbackInfos);

      // Provide query parameter for the first callback (order=1).
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      // Setup callback request matching the first callback by name.
      Request callbackRequest = new Request();
      callbackRequest.setName("firstEvent callback");
      callbackRequest.setCallbackName("firstEvent");
      callbackRequest.setContent("{\"status\":\"first\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      // Step 0 should select the callback with order=1 (the first in sorted order).
      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      verify(requestRepository).findByOperationIdAndName(eq("serviceId-POST /subscribe"), eq("response"));
   }

   @Test
   void testOnApplicationEventReEmitsEventForNextCallback() {
      // Setup operation with 2 callback infos.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");

      CallbackInfo firstCallback = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      firstCallback.setOrder(1);

      CallbackInfo secondCallback = new CallbackInfo("{$request.query.callbackUrl}", "PUT");
      secondCallback.setOrder(2);

      operation.addCallbackInfo("firstEvent", firstCallback);
      operation.addCallbackInfo("secondEvent", secondCallback);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      // Setup callback request.
      Request callbackRequest = new Request();
      callbackRequest.setName("firstEvent callback");
      callbackRequest.setCallbackName("firstEvent");
      callbackRequest.setContent("{\"status\":\"first\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      // Start at step 0 with 2 callbacks → should re-emit for step 1.
      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      // Verify that a new event was published for the next step.
      ArgumentCaptor<CallbackTriggerEvent> eventCaptor = ArgumentCaptor.forClass(CallbackTriggerEvent.class);
      verify(applicationContext).publishEvent(eventCaptor.capture());

      CallbackTriggerEvent reEmittedEvent = eventCaptor.getValue();
      assertEquals(1, reEmittedEvent.getStep());
      assertEquals("serviceId", reEmittedEvent.getServiceId());
      assertEquals("response", reEmittedEvent.getResponseName());
   }

   @Test
   void testOnApplicationEventDoesNotReEmitForLastCallback() {
      // Setup operation with only 1 callback info.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");

      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"status\":\"done\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      // Should NOT re-emit event since there is only 1 callback.
      verifyNoInteractions(applicationContext);
   }

   @Test
   void testOnApplicationEventWithBodyJsonPointerArrayExtraction() {
      // Setup operation with callback info using body JSON pointer to an array.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.body#/urls}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      // Setup snapshot with body containing an array at /urls.
      String body = "{\"urls\":[\"http://first.example.com\",\"http://second.example.com\"]}";
      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(), Map.of(), body);

      // The extracted URL will be a serialized JSON array, which is not a valid URL.
      // The repository should still be called but the HTTP request will fail (that's fine for the test).
      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"status\":\"done\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot);

      // The sendCallbackRequest will fail with invalid URL but that's expected.
      // We just verify the repository interaction.
      Thread testThread = new Thread(() -> {
         try {
            callbackTrigger.onApplicationEvent(event);
         } catch (Exception e) {
            // Expected - serialized array is not a valid URL.
         }
      });
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      verify(requestRepository).findByOperationIdAndName(anyString(), anyString());
   }

   @Test
   void testOnApplicationEventWithCallbackRequestHeaders() {
      // Setup operation with callback info.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      // Setup callback request with headers.
      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"status\":\"done\"}");
      callbackRequest.setHeaders(Set.of(new Header("Content-Type", Set.of("application/json")),
            new Header("Accept", Set.of("application/json"))));

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      // Verify repository was called - the HTTP send is fire-and-forget.
      verify(requestRepository).findByOperationIdAndName(anyString(), anyString());
   }

   @Test
   void testOnApplicationEventWithCallbackRequestQueryParameters() {
      // Setup operation with callback info.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      // Setup callback request with query parameters.
      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"status\":\"done\"}");

      Parameter param = new Parameter();
      param.setName("token");
      param.setValue("abc123");
      callbackRequest.setQueryParameters(List.of(param));

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      verify(requestRepository).findByOperationIdAndName(anyString(), anyString());
   }

   @Test
   void testOnApplicationEventWithGetMethod() {
      // Setup operation with callback info using GET method.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "GET");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      // Setup callback request (GET should have no body).
      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent(null);

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      verify(requestRepository).findByOperationIdAndName(anyString(), anyString());
   }

   @Test
   void testOnApplicationEventWithNullMethod() {
      // Setup operation with callback info with null method (defaults to POST).
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", null);
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"data\":\"test\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      verify(requestRepository).findByOperationIdAndName(anyString(), anyString());
   }

   @Test
   void testOnApplicationEventWithTemplateContent() {
      // Setup operation with callback info.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(), Map.of("callbackUrl",
            new String[] { "http://consumer.example.com/callback" }, "userId", new String[] { "42" }),
            "{\"name\":\"test\"}");

      // Setup callback request with template expression in content.
      Request callbackRequest = new Request();
      callbackRequest.setName("onEvent callback");
      callbackRequest.setCallbackName("onEvent");
      callbackRequest.setContent("{\"user\":\"{{request.params[userId]}}\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString())).thenReturn(List.of(callbackRequest));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      Thread testThread = new Thread(() -> callbackTrigger.onApplicationEvent(event));
      testThread.start();
      try {
         testThread.join(10000);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }

      verify(requestRepository).findByOperationIdAndName(anyString(), anyString());
      // After ListenerCommons.renderContent, the template should have been evaluated.
      // The content of callbackRequest should now be rendered.
      assertEquals("{\"user\":\"42\"}", callbackRequest.getContent());
   }

   @Test
   void testOnApplicationEventWithCallbackNullName() {
      // Setup operation with callback info.
      Operation operation = new Operation();
      operation.setName("POST /subscribe");
      CallbackInfo callbackInfo = new CallbackInfo("{$request.query.callbackUrl}", "POST");
      callbackInfo.setOrder(0);
      operation.addCallbackInfo("onEvent", callbackInfo);

      HttpServletRequestSnapshot snapshot = new HttpServletRequestSnapshot("/subscribe", Map.of(),
            Map.of("callbackUrl", new String[] { "http://consumer.example.com/callback" }), null);

      // Return a request with null callbackName - should not match.
      Request requestWithoutCallbackName = new Request();
      requestWithoutCallbackName.setName("regular request");
      requestWithoutCallbackName.setContent("{\"data\":\"test\"}");

      when(requestRepository.findByOperationIdAndName(anyString(), anyString()))
            .thenReturn(List.of(requestWithoutCallbackName));

      CallbackTriggerEvent event = new CallbackTriggerEvent(this, "serviceId", operation, "response", snapshot, 0);

      callbackTrigger.onApplicationEvent(event);

      verify(requestRepository).findByOperationIdAndName(anyString(), anyString());
      // Should not re-emit since no matching callback request was found.
      verifyNoInteractions(applicationContext);
   }
}

