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
package io.microcks.test.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.microcks.util.http.ClientHttpRequest;
import io.microcks.util.http.ClientHttpRequestFactory;
import io.microcks.util.http.ClientHttpResponse;
import io.microcks.util.http.HttpHeaders;
import io.microcks.util.http.HttpMethod;

import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization tests of the HTTP behaviours that test runners rely on. They only use the
 * {@code ClientHttpRequestFactory} abstraction: {@link #newFactory()} is the single place that knows about the
 * underlying HTTP client.
 */
class ClientHttpRequestFactoryTest {

   private static HttpServer server;

   private static ClientHttpRequestFactory newFactory() {
      return new ApacheClientHttpRequestFactory(HttpClients.createDefault());
   }

   @BeforeAll
   static void startServer() throws IOException {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/echo", ClientHttpRequestFactoryTest::echo);
      server.createContext("/status/", ClientHttpRequestFactoryTest::status);
      server.createContext("/gzip", ClientHttpRequestFactoryTest::gzip);
      server.createContext("/redirect", ClientHttpRequestFactoryTest::redirect);
      server.createContext("/binary", ClientHttpRequestFactoryTest::binary);
      server.start();
   }

   @AfterAll
   static void stopServer() {
      server.stop(0);
   }

   private static URI uri(String path) {
      return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
   }

   private static void send(HttpExchange exchange, int status, String contentType, byte[] body) throws IOException {
      if (contentType != null) {
         exchange.getResponseHeaders().set("Content-Type", contentType);
      }
      if (body.length == 0) {
         exchange.sendResponseHeaders(status, -1);
      } else {
         exchange.sendResponseHeaders(status, body.length);
         exchange.getResponseBody().write(body);
      }
      exchange.close();
   }

   /** Replies with a "METHOD|content-type|x-custom values|body" summary of what was received. */
   private static void echo(HttpExchange exchange) throws IOException {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      String custom = String.join(",", exchange.getRequestHeaders().getOrDefault("X-Custom", java.util.List.of()));
      String summary = exchange.getRequestMethod() + "|" + exchange.getRequestHeaders().getFirst("Content-Type") + "|"
            + custom + "|" + body;
      exchange.getResponseHeaders().add("X-Reply", "one");
      exchange.getResponseHeaders().add("X-Reply", "two");
      send(exchange, 200, "text/plain; charset=UTF-8", summary.getBytes(StandardCharsets.UTF_8));
   }

   private static void status(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      int code = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
      byte[] body = code == 204 ? new byte[0] : ("status " + code).getBytes(StandardCharsets.UTF_8);
      send(exchange, code, "text/plain", body);
   }

   private static void gzip(HttpExchange exchange) throws IOException {
      byte[] plain = "compressed payload".getBytes(StandardCharsets.UTF_8);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
         gz.write(plain);
      }
      exchange.getResponseHeaders().set("Content-Encoding", "gzip");
      send(exchange, 200, "text/plain", out.toByteArray());
   }

   private static void redirect(HttpExchange exchange) throws IOException {
      exchange.getRequestBody().readAllBytes();
      exchange.getResponseHeaders().set("Location", "/echo");
      send(exchange, 302, null, new byte[0]);
   }

   private static void binary(HttpExchange exchange) throws IOException {
      byte[] data = new byte[200_000];
      for (int i = 0; i < data.length; i++) {
         data[i] = (byte) i;
      }
      send(exchange, 200, "application/octet-stream", data);
   }

   private static ClientHttpResponse call(HttpMethod method, String path, String contentType, String body,
         String... customValues) throws IOException {
      ClientHttpRequest request = newFactory().createRequest(uri(path), method);
      if (contentType != null) {
         request.getHeaders().set(HttpHeaders.CONTENT_TYPE, contentType);
      }
      for (String value : customValues) {
         request.getHeaders().add("X-Custom", value);
      }
      if (body != null) {
         request.getBody().write(body.getBytes(StandardCharsets.UTF_8));
      }
      return request.execute();
   }

   private static String read(ClientHttpResponse response) throws IOException {
      try (response) {
         return new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   @Test
   void sendsMethodHeadersAndBody() throws IOException {
      for (HttpMethod method : new HttpMethod[] { HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH }) {
         ClientHttpResponse response = call(method, "/echo", "application/json", "{\"a\":1}", "v1", "v2");
         assertEquals(200, response.getStatusCode().value());
         String text = read(response);
         assertTrue(text.startsWith(method.methodName() + "|application/json"), text);
         assertTrue(text.endsWith("|{\"a\":1}"), text);
         assertTrue(text.contains("v1") && text.contains("v2"), text);
      }
   }

   @Test
   void sendsGetAndDeleteWithoutBody() throws IOException {
      for (HttpMethod method : new HttpMethod[] { HttpMethod.GET, HttpMethod.DELETE }) {
         String text = read(call(method, "/echo", null, null));
         assertEquals(method.methodName() + "|null||", text);
      }
   }

   @Test
   void preservesUtf8BodyBytes() throws IOException {
      String text = read(call(HttpMethod.POST, "/echo", "text/plain; charset=UTF-8", "café ☕ 日本"));
      assertTrue(text.endsWith("|café ☕ 日本"), text);
   }

   @Test
   void exposesStatusAndMultiValuedHeaders() throws IOException {
      ClientHttpResponse response = call(HttpMethod.GET, "/echo", null, null);
      try (response) {
         assertEquals(200, response.getStatusCode().value());
         assertTrue(response.getHeaders().getContentType().startsWith("text/plain"));
         assertEquals(java.util.List.of("one", "two"), response.getHeaders().get("X-Reply"));
         assertEquals("one", response.getHeaders().getFirst("x-reply"));
      }
   }

   @Test
   void returnsErrorStatusesWithBody() throws IOException {
      for (int code : new int[] { 400, 404, 500, 503 }) {
         ClientHttpResponse response = call(HttpMethod.GET, "/status/" + code, null, null);
         assertEquals(code, response.getStatusCode().value());
         assertTrue(response.getStatusCode().isError());
         assertEquals("status " + code, read(response));
      }
   }

   @Test
   void handlesEmptyResponseBody() throws IOException {
      ClientHttpResponse response = call(HttpMethod.GET, "/status/204", null, null);
      assertEquals(204, response.getStatusCode().value());
      assertEquals("", read(response));
   }

   @Test
   void decompressesGzipResponses() throws IOException {
      assertEquals("compressed payload", read(call(HttpMethod.GET, "/gzip", null, null)));
   }

   @Test
   void followsRedirectsAndSwitchesPostToGet() throws IOException {
      ClientHttpResponse response = call(HttpMethod.POST, "/redirect", "text/plain", "ignored");
      assertEquals(200, response.getStatusCode().value());
      assertEquals("GET|null||", read(response));
   }

   @Test
   void readsLargeBinaryBodiesIntact() throws IOException {
      ClientHttpResponse response = call(HttpMethod.GET, "/binary", null, null);
      try (response) {
         byte[] data = response.getBody().readAllBytes();
         assertEquals(200_000, data.length);
         for (int i = 0; i < data.length; i += 997) {
            assertEquals((byte) i, data[i]);
         }
      }
   }

   @Test
   void failsWithIOExceptionWhenConnectionRefused() throws IOException {
      URI closed;
      try (java.net.ServerSocket socket = new java.net.ServerSocket(0, 0, java.net.InetAddress.getLoopbackAddress())) {
         closed = URI.create("http://127.0.0.1:" + socket.getLocalPort() + "/");
      }
      ClientHttpRequest request = newFactory().createRequest(closed, HttpMethod.GET);
      assertThrows(IOException.class, request::execute);
   }

   @Test
   void failsWithIOExceptionOnUnknownHost() throws IOException {
      ClientHttpRequest request = newFactory().createRequest(URI.create("http://inexistent.invalid:6543/"),
            HttpMethod.GET);
      assertThrows(IOException.class, request::execute);
   }
}
