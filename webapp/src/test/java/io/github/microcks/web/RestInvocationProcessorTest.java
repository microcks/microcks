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
package io.github.microcks.web;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceStateRepository;
import io.github.microcks.service.OpenTelemetryResolverService;
import io.github.microcks.service.ProxyService;
import io.github.microcks.util.DispatchStyles;

import io.opentelemetry.api.OpenTelemetry;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RestInvocationProcessor Unit Tests")
class RestInvocationProcessorTest {

   private RestInvocationProcessor processor;
   private ResponseRepository responseRepo;
   private ProxyService proxyService;
   private HttpServletRequest request;

   @BeforeEach
   void setup() {
      // Mock all the dependencies needed by the processor.
      ServiceStateRepository stateRepo = mock(ServiceStateRepository.class);
      responseRepo = mock(ResponseRepository.class);
      ApplicationContext appContext = mock(ApplicationContext.class);
      proxyService = mock(ProxyService.class);
      request = mock(HttpServletRequest.class);
      OpenTelemetryResolverService otelResolver = mock(OpenTelemetryResolverService.class);
      OpenTelemetry openTelemetry = OpenTelemetry.noop();
      when(otelResolver.getOpenTelemetry()).thenReturn(openTelemetry);
      processor = new RestInvocationProcessor(stateRepo, responseRepo, appContext, proxyService, otelResolver);
      ReflectionTestUtils.setField(processor, "enableBinaryResponseDecode", true);
   }

   /** Helper method to build a mock response object. */
   private Response createMockResponse(String mediaType, String content) {
      var response = new Response();
      response.setStatus("200");
      response.setMediaType(mediaType);
      response.setContent(content);
      return response;
   }

   /** Helper method to build a mock invocation context. */
   private MockInvocationContext createMockContext(String operationName, String method, String resourcePath) {
      var service = new Service();
      service.setId("service-id");

      var operation = new Operation();
      operation.setName(operationName);
      operation.setMethod(method);

      return new MockInvocationContext(service, operation, resourcePath);
   }

   @Nested
   @DisplayName("Standard Mock Response Handling")
   class StandardResponseTests {

      @Test
      @DisplayName("should return a matching UTF-8 response from repository")
      void shouldReturnUtf8Response() {
         // Arrange
         var context = createMockContext("GET /api/test", "GET", "/api/test");
         var mockResponse = createMockResponse("application/json", "{\"message\": \"Hello\"}");

         when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/test"));
         when(request.getHeader("Accept")).thenReturn("application/json");
         when(responseRepo.findByOperationIdAndDispatchCriteria(any(), any())).thenReturn(List.of(mockResponse));

         // Act
         var result = processor.processInvocation(context, System.currentTimeMillis(), null, null, Map.of(), request);

         // Assert
         assertEquals(HttpStatus.OK, result.status());
         assertNotNull(result.content());
         assertEquals("{\"message\": \"Hello\"}", new String(result.content()));
         assertEquals("application/json;charset=UTF-8",
               Objects.requireNonNull(result.headers().getContentType()).toString());
      }

      @Test
      @DisplayName("should return a binary response from data URI")
      void shouldReturnBinaryResponseFromDataUri() {
         // Arrange
         var context = createMockContext("GET /file", "GET", "/file");
         byte[] binaryData = "binary-content".getBytes();
         String dataUri = "data:application/octet-stream;base64," + Base64.getEncoder().encodeToString(binaryData);
         var mockResponse = createMockResponse("application/octet-stream", dataUri);

         when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/file"));
         when(responseRepo.findByOperationIdAndDispatchCriteria(any(), any())).thenReturn(List.of(mockResponse));

         // Act
         var result = processor.processInvocation(context, System.currentTimeMillis(), null, null, Map.of(), request);

         // Assert
         assertEquals(HttpStatus.OK, result.status());
         assertArrayEquals(binaryData, result.content());
      }

      @Test
      @DisplayName("should select first response when Accept header does not match any")
      void shouldFallbackToFirstResponseOnAcceptMismatch() {
         // Arrange
         var context = createMockContext("GET /api/options", "GET", "/api/options");
         var xmlResponse = createMockResponse("application/xml", "<message>XML</message>");
         var jsonResponse = createMockResponse("application/json", "{\"message\":\"JSON\"}");

         when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/api/options"));
         when(request.getHeader("Accept")).thenReturn("text/plain"); // This Accept header matches neither response.
         when(responseRepo.findByOperationIdAndDispatchCriteria(any(), any()))
               .thenReturn(List.of(xmlResponse, jsonResponse));

         // Act
         var result = processor.processInvocation(context, System.currentTimeMillis(), null, null, Map.of(), request);

         // Assert
         assertEquals(HttpStatus.OK, result.status());
         // It should pick the first one in the list (XML).
         assertEquals("<message>XML</message>", new String(result.content()));
         assertEquals("application/xml;charset=UTF-8",
               Objects.requireNonNull(result.headers().getContentType()).toString());
      }
   }

   @Nested
   @DisplayName("Error and Fallback Handling")
   class ErrorTests {

      @Test
      @DisplayName("should return 400 Bad Request when dispatcher is set but no response matches")
      void shouldReturnBadRequestWhenDispatcherFindsNoMatch() {
         // Arrange
         var context = createMockContext("POST /no/match", "POST", "/no/match");
         // Set a dispatcher to trigger the specific logic branch for handled mismatches.
         context.operation().setDispatcher(DispatchStyles.URI_PARAMS);
         context.operation().setDispatcherRules("id");

         when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/no/match"));
         when(request.getQueryString()).thenReturn("id=123");

         // Mock repository to find no matching response.
         when(responseRepo.findByOperationIdAndDispatchCriteria(any(), any())).thenReturn(Collections.emptyList());
         when(responseRepo.findByOperationIdAndName(any(), any())).thenReturn(Collections.emptyList());

         // Act
         var result = processor.processInvocation(context, System.currentTimeMillis(), null, "{}", Map.of(), request);

         // Assert
         assertEquals(HttpStatus.BAD_REQUEST, result.status());
         assertTrue(new String(result.content()).contains("does not exist!"));
      }

      @Test
      @DisplayName("should return 400 Bad Request when no dispatcher and no responses exist")
      void shouldReturnBadRequestWhenNoResponseCanBeFound() {
         // Arrange
         var context = createMockContext("GET /nothing", "GET", "/nothing");
         // No dispatcher is set, so the processor will try a simple lookup.

         when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/nothing"));
         // Mock all repository lookups to return empty lists.
         when(responseRepo.findByOperationIdAndDispatchCriteria(any(), any())).thenReturn(Collections.emptyList());
         when(responseRepo.findByOperationIdAndName(any(), any())).thenReturn(Collections.emptyList());
         when(responseRepo.findByOperationId(any())).thenReturn(Collections.emptyList());

         // Act
         var result = processor.processInvocation(context, System.currentTimeMillis(), null, null, Map.of(), request);

         // Assert
         // This tests the final fallback in the method.
         assertEquals(HttpStatus.BAD_REQUEST, result.status());
         assertNull(result.content());
      }
   }

   @Nested
   @DisplayName("Proxy Fallback Handling")
   class ProxyTests {

      @Test
      @DisplayName("should proxy request when ProxyFallback dispatcher is used")
      void shouldProxyRequestWithProxyFallback() {
         // Arrange
         var context = createMockContext("GET /proxy", "GET", "/proxy");
         context.operation().setDispatcher("PROXY_FALLBACK");
         context.operation().setDispatcherRules("""
               {
                 "dispatcher": "URI_PARTS",
                 "dispatcherRules": "name",
                 "proxyUrl": "http://external.net/myService/v1/"
               }
               """);

         var proxyResponse = new ResponseEntity<>("from proxy".getBytes(), HttpStatus.OK);
         when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/proxy"));
         when(proxyService.callExternal(any(), eq(HttpMethod.GET), any(), isNull())).thenReturn(proxyResponse);

         // Act
         var result = processor.processInvocation(context, System.currentTimeMillis(), null, null, Map.of(), request);

         // Assert
         assertEquals(HttpStatus.OK, result.status());
         assertArrayEquals("from proxy".getBytes(), result.content());
         verify(proxyService).callExternal(any(), eq(HttpMethod.GET), any(), isNull());
      }
   }
}
