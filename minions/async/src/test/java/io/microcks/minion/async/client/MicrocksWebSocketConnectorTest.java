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
package io.microcks.minion.async.client;

import io.microcks.event.ChangeType;
import io.microcks.event.ServiceViewChangeEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test case for {@link MicrocksWebSocketConnector} against a fake Microcks app WebSocket server.
 */
class MicrocksWebSocketConnectorTest {

   private final ObjectMapper mapper = new ObjectMapper();
   private Vertx vertx;
   private HttpServer server;
   private MicrocksWebSocketConnector connector;

   private final List<ServerWebSocket> serviceSockets = new CopyOnWriteArrayList<>();
   private final List<ServerWebSocket> triggerSockets = new CopyOnWriteArrayList<>();
   private final List<String> log = new CopyOnWriteArrayList<>();
   private final AtomicInteger syncCount = new AtomicInteger();

   @BeforeEach
   void setUp() {
      vertx = Vertx.vertx();
   }

   @AfterEach
   void tearDown() {
      if (connector != null) {
         connector.stop();
      }
      vertx.close().toCompletionStage().toCompletableFuture().join();
   }

   @Test
   void testUrlDerivation() {
      assertEquals("ws://localhost:8080", MicrocksWebSocketConnector.toWebSocketBaseUrl("http://localhost:8080/"));
      assertEquals("wss://microcks.example.com",
            MicrocksWebSocketConnector.toWebSocketBaseUrl("https://microcks.example.com"));
   }

   @Test
   void testEventsAreAppliedInOrderAfterSynchronization() throws Exception {
      int port = startServer(0);
      startConnector(port, () -> {
         log.add("sync");
         syncCount.incrementAndGet();
         // Give a chance to events to arrive while synchronizing.
         sleep(300);
      });
      waitFor(() -> serviceSockets.size() == 1 && triggerSockets.size() == 1);

      for (int i = 0; i < 5; i++) {
         serviceSockets.get(0).writeTextMessage(event("svc-" + i));
      }
      waitFor(() -> log.size() == 6);
      assertEquals(List.of("sync", "svc-0", "svc-1", "svc-2", "svc-3", "svc-4"), log);
   }

   @Test
   void testReconnectsAndSynchronizesAfterServerDrop() throws Exception {
      int port = startServer(0);
      startConnector(port, () -> syncCount.incrementAndGet());
      waitFor(() -> syncCount.get() == 1 && serviceSockets.size() == 1);

      serviceSockets.get(0).close();
      waitFor(() -> syncCount.get() == 2 && serviceSockets.size() == 2 && triggerSockets.size() == 2);

      serviceSockets.get(1).writeTextMessage(event("after-reconnect"));
      waitFor(() -> log.contains("after-reconnect"));
   }

   @Test
   void testStartsEvenIfAppIsNotReachableYet() throws Exception {
      int port;
      try (ServerSocket socket = new ServerSocket(0)) {
         port = socket.getLocalPort();
      }
      startConnector(port, () -> syncCount.incrementAndGet());
      sleep(500);
      assertEquals(0, syncCount.get());

      startServer(port);
      waitFor(() -> syncCount.get() == 1 && serviceSockets.size() == 1);
   }

   @Test
   void testRetriesWhenSynchronizationFails() throws Exception {
      int port = startServer(0);
      startConnector(port, () -> {
         if (syncCount.incrementAndGet() == 1) {
            throw new IllegalStateException("Microcks API not ready");
         }
      });
      waitFor(() -> syncCount.get() == 2);
   }

   private String event(String serviceId) throws Exception {
      ServiceViewChangeEvent event = new ServiceViewChangeEvent(serviceId, null, ChangeType.UPDATED,
            System.currentTimeMillis());
      return mapper.writeValueAsString(event);
   }

   private int startServer(int port) throws Exception {
      server = vertx.createHttpServer().webSocketHandler(ws -> {
         if (MicrocksWebSocketConnector.SERVICES_UPDATES_PATH.equals(ws.path())) {
            serviceSockets.add(ws);
         } else if (MicrocksWebSocketConnector.ASYNCAPI_TRIGGERS_PATH.equals(ws.path())) {
            triggerSockets.add(ws);
         } else {
            ws.reject();
         }
      });
      return server.listen(port).toCompletionStage().toCompletableFuture().get(5, TimeUnit.SECONDS).actualPort();
   }

   private void startConnector(int port, Runnable synchronizer) {
      connector = new MicrocksWebSocketConnector(vertx, "http://localhost:" + port, mapper, synchronizer,
            e -> log.add(e.getServiceId()), c -> log.add("trigger"), 100, 400, 5000);
      connector.start();
   }

   private static void sleep(long ms) {
      try {
         Thread.sleep(ms);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static void waitFor(BooleanSupplier condition) throws Exception {
      long deadline = System.currentTimeMillis() + 10_000;
      while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
         Thread.sleep(25);
      }
      assertTrue(condition.getAsBoolean(), "Condition not met within timeout");
   }
}
