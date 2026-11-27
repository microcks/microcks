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
package io.microcks.web.arazzo;

import io.microcks.domain.Header;
import io.microcks.domain.Operation;
import io.microcks.domain.Service;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.service.arazzo.ArazzoSessionService;
import io.microcks.util.http.HttpHeaders;
import io.microcks.util.http.HttpStatusCode;
import io.microcks.web.GrpcResponseResult;
import io.microcks.web.MockInvocationContext;
import io.microcks.web.ResponseResult;

import io.grpc.Metadata;
import io.grpc.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests of the recording of the mock invocations naming a verification session, as the mocks received and answered
 * them.
 * @author SebastienDegodez
 */
class ArazzoSessionRecorderTest {

   private static final String ORDERS = "/rest/Order+API/1.0.0/orders";

   private final ArazzoSessionService arazzoSessionService = mock(ArazzoSessionService.class);
   private final ArazzoSessionRecorder recorder = new ArazzoSessionRecorder(arazzoSessionService);

   @Test
   void aRestInvocationNamingASessionIsRecordedAsReceivedAndAnswered() {
      PostedRequest request = new PostedRequest(ORDERS, "dryRun=false");
      request.withHeader("x-microcks-session", "s-1");
      request.withHeader("Accept", "application/json");
      HttpHeaders responseHeaders = new HttpHeaders();
      responseHeaders.add("Content-Type", "application/json");

      recorder.recordRest(placeOrder(), request, "{\"customerId\": \"c-7\"}", new ResponseResult(
            new HttpStatusCode(201), responseHeaders, "{\"id\": \"o-42\"}".getBytes(StandardCharsets.UTF_8)));

      ArazzoSessionCall call = recorded("s-1");
      assertEquals("dryRun=false", call.getQueryString());
      assertEquals(
            List.of("Order API", "1.0.0", "POST /orders", "POST", "/orders",
                  "http://localhost/rest/Order+API/1.0.0/orders?dryRun=false", "{\"customerId\": \"c-7\"}", "201",
                  "{\"id\": \"o-42\"}"),
            List.of(call.getServiceName(), call.getServiceVersion(), call.getOperationName(), call.getMethod(),
                  call.getPath(), call.getRequestUrl(), call.getRequestBody(), String.valueOf(call.getResponseStatus()),
                  call.getResponseBody()));
      assertEquals(List.of("Accept=[application/json]", "x-microcks-session=[s-1]"),
            described(call.getRequestHeaders()));
      assertEquals(List.of("Content-Type=[application/json]"), described(call.getResponseHeaders()));
   }

   @Test
   void aRestInvocationWithoutQueryNorResponseContentIsRecordedWithoutThem() {
      PostedRequest request = new PostedRequest(ORDERS, null);
      request.withHeader("x-microcks-session", "s-1");

      recorder.recordRest(placeOrder(), request, null, new ResponseResult(new HttpStatusCode(204), null, null));

      ArazzoSessionCall call = recorded("s-1");
      assertEquals("http://localhost/rest/Order+API/1.0.0/orders 204",
            call.getRequestUrl() + " " + call.getResponseStatus());
      assertNull(call.getResponseBody());
      assertEquals(List.of(), described(call.getResponseHeaders()));
   }

   @Test
   void aRestInvocationNamingASessionInItsBaggageIsRecorded() {
      PostedRequest request = new PostedRequest(ORDERS, null);
      request.withHeader("baggage", "tenant=bakery");
      request.withHeader("baggage", "microcks-session=s-2");

      recorder.recordRest(placeOrder(), request, null, new ResponseResult(new HttpStatusCode(201), null, null));

      assertEquals("POST /orders", recorded("s-2").getOperationName());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "x-microcks-session | s-3", "baggage | microcks-session=s-3" })
   void aGrpcInvocationNamingASessionInItsMetadataIsRecorded(String key, String value) {
      Metadata metadata = new Metadata();
      metadata.put(Metadata.Key.of(key, Metadata.ASCII_STRING_MARSHALLER), value);

      recorder.recordGrpc(reserveStockOverGrpc(), metadata, "{\"orderId\": \"o-42\"}",
            new GrpcResponseResult(Status.OK, "{\"id\": \"r-9\"}", null));

      ArazzoSessionCall call = recorded("s-3");
      assertEquals(
            List.of("Stock Service", "v1", "ReserveStock", "GRPC", "ReserveStock", "{\"orderId\": \"o-42\"}", "0",
                  "{\"id\": \"r-9\"}", "[" + key + "=[" + value + "]]"),
            List.of(call.getServiceName(), call.getServiceVersion(), call.getOperationName(), call.getMethod(),
                  call.getPath(), call.getRequestBody(), String.valueOf(call.getResponseStatus()),
                  call.getResponseBody(), described(call.getRequestHeaders()).toString()));
   }

   @Test
   void aFailedGrpcInvocationIsRecordedWithItsStatusAndDescription() {
      Metadata metadata = new Metadata();
      metadata.put(Metadata.Key.of("x-microcks-session", Metadata.ASCII_STRING_MARSHALLER), "s-3");

      recorder.recordGrpc(reserveStockOverGrpc(), metadata, "{}",
            new GrpcResponseResult(Status.NOT_FOUND, null, "No response found"));

      ArazzoSessionCall call = recorded("s-3");
      assertEquals("5 No response found", call.getResponseStatus() + " " + call.getResponseBody());
   }

   @Test
   void aGrpcInvocationNamingNoSessionIsNotRecorded() {
      recorder.recordGrpc(reserveStockOverGrpc(), new Metadata(), "{}", new GrpcResponseResult(Status.OK, "{}", null));
      recorder.recordGrpc(reserveStockOverGrpc(), null, "{}", new GrpcResponseResult(Status.OK, "{}", null));

      verify(arazzoSessionService, never()).recordCall(anyString(), any());
   }

   private static MockInvocationContext reserveStockOverGrpc() {
      Service service = new Service();
      service.setName("Stock Service");
      service.setVersion("v1");
      Operation operation = new Operation();
      operation.setName("ReserveStock");
      return new MockInvocationContext(service, operation, null);
   }

   @ParameterizedTest
   @CsvSource(nullValues = "null", value = { "null", "''", "'  '" })
   void aRestInvocationNamingNoSessionIsNotRecorded(String sessionId) {
      PostedRequest request = new PostedRequest(ORDERS, null);
      if (sessionId != null) {
         request.withHeader("x-microcks-session", sessionId);
      }

      recorder.recordRest(placeOrder(), request, null, new ResponseResult(new HttpStatusCode(201), null, null));

      verify(arazzoSessionService, never()).recordCall(anyString(), any());
   }

   /** Headers have no equality of their own: each is described by its name and values, sorted. */
   private static List<String> described(Set<Header> headers) {
      return headers.stream().map(header -> header.getName() + "=" + header.getValues().stream().sorted().toList())
            .sorted().toList();
   }

   private ArazzoSessionCall recorded(String sessionId) {
      ArgumentCaptor<ArazzoSessionCall> call = ArgumentCaptor.forClass(ArazzoSessionCall.class);
      verify(arazzoSessionService).recordCall(eq(sessionId), call.capture());
      return call.getValue();
   }

   private static MockInvocationContext placeOrder() {
      Service service = new Service();
      service.setName("Order API");
      service.setVersion("1.0.0");
      Operation operation = new Operation();
      operation.setName("POST /orders");
      return new MockInvocationContext(service, operation, "/orders");
   }
}
