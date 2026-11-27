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
package io.microcks.minion.arazzo.runner;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests of sending the requests of steps over HTTP, to a local server standing for an API.
 * @author SebastienDegodez
 */
class JdkHttpTransportTest {

   private final List<String> received = new ArrayList<>();
   private HttpServer server;
   private String baseUrl;

   @BeforeEach
   void startTheApi() throws IOException {
      server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext("/", exchange -> {
         String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
         received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI() + " "
               + exchange.getRequestHeaders().getFirst("Accept") + " " + body);
         if (exchange.getRequestURI().getPath().equals("/slow")) {
            sleep();
         }
         byte[] answer = "{\"price\":2.6}".getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(202, answer.length);
         exchange.getResponseBody().write(answer);
         exchange.close();
      });
      server.start();
      baseUrl = "http://localhost:" + server.getAddress().getPort();
   }

   @AfterEach
   void stopTheApi() {
      server.stop(0);
   }

   @Test
   void aRequestIsSentWithItsMethodHeadersAndBody() throws IOException {
      HttpOutcome outcome = new JdkHttpTransport()
            .send(new HttpCall("PATCH", URI.create(baseUrl + "/pastry/Eclair%20Cafe?size=S"),
                  Map.of("Accept", "application/json"), "{\"price\":2.6}"), Duration.ofSeconds(5));

      assertEquals(List.of("PATCH /pastry/Eclair%20Cafe?size=S application/json {\"price\":2.6}"), received);
      assertEquals(202, outcome.status());
      assertEquals(List.of("application/json"), outcome.headers().get("content-type"));
      assertEquals("{\"price\":2.6}", outcome.body());
   }

   @Test
   void aRequestWithoutBodySendsNone() throws IOException {
      new JdkHttpTransport().send(new HttpCall("GET", URI.create(baseUrl + "/pastry"), Map.of(), null),
            Duration.ofSeconds(5));

      assertEquals(List.of("GET /pastry null "), received);
   }

   @Test
   void aResponseLaterThanTheTimeoutIsNotAwaited() {
      assertThrows(HttpTimeoutException.class, () -> new JdkHttpTransport()
            .send(new HttpCall("GET", URI.create(baseUrl + "/slow"), Map.of(), null), Duration.ofMillis(100)));
   }

   @Test
   void anInterruptedWaitEndsWithoutResponseAndKeepsTheInterruption() {
      Thread.currentThread().interrupt();

      IOException noResponse = assertThrows(IOException.class, () -> new JdkHttpTransport()
            .send(new HttpCall("GET", URI.create(baseUrl + "/slow"), Map.of(), null), Duration.ofSeconds(5)));

      assertEquals("Interrupted while waiting for the response", noResponse.getMessage());
      assertTrue(Thread.interrupted());
   }

   private static void sleep() {
      try {
         Thread.sleep(1000);
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
      }
   }
}
