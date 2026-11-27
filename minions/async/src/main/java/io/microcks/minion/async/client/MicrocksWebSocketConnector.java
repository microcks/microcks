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

import io.microcks.event.AsyncAPITriggerCommand;
import io.microcks.event.ServiceViewChangeEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketClient;
import io.vertx.core.http.WebSocketClientOptions;
import io.vertx.core.http.WebSocketConnectOptions;
import org.jboss.logging.Logger;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Connector to the Microcks app WebSocket endpoints <code>/api/services-updates</code> and
 * <code>/api/asyncapi-triggers</code>. This is the only link between the app and the minion. As the app does not replay
 * messages, the connector:
 * <ul>
 * <li>connects both endpoints first, then runs a full synchronization (so that no event window is lost),</li>
 * <li>applies synchronization and received events sequentially, in arrival order, on a single worker thread,</li>
 * <li>reconnects with a bounded exponential backoff and synchronizes again after each connection loss, including when
 * the app is not reachable yet at startup.</li>
 * </ul>
 * @author laurent
 */
public class MicrocksWebSocketConnector {

   /** Get a JBoss logging logger. */
   private static final Logger logger = Logger.getLogger(MicrocksWebSocketConnector.class);

   /** The path of the services updates endpoint. */
   public static final String SERVICES_UPDATES_PATH = "/api/services-updates";
   /** The path of the AsyncAPI triggers endpoint. */
   public static final String ASYNCAPI_TRIGGERS_PATH = "/api/asyncapi-triggers";

   private static final long CONNECT_TIMEOUT_MS = 10_000L;
   private static final int MAX_MESSAGE_SIZE = 64 * 1024 * 1024;

   private final Vertx vertx;
   private final String wsBaseUrl;
   private final ObjectMapper mapper;
   private final Runnable synchronizer;
   private final Consumer<ServiceViewChangeEvent> serviceEventConsumer;
   private final Consumer<AsyncAPITriggerCommand> triggerConsumer;
   private final long minBackoffMs;
   private final long maxBackoffMs;
   private final long pingIntervalMs;

   private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> daemon(r, "microcks-ws-worker"));
   private final ExecutorService supervisor = Executors
         .newSingleThreadExecutor(r -> daemon(r, "microcks-ws-connector"));
   private volatile boolean running;
   private volatile Connection current;
   private WebSocketClient client;
   private volatile Supplier<Optional<String>> authorizationSupplier = Optional::empty;

   /**
    * Build a connector.
    * @param vertx                the Vert.x instance used to open WebSockets
    * @param restBaseUrl          the HTTP(S) base URL of Microcks app, converted into a WS(S) one
    * @param mapper               to deserialize received events
    * @param synchronizer         full synchronization with Microcks API, run after each (re)connection
    * @param serviceEventConsumer to apply a {@code ServiceViewChangeEvent}
    * @param triggerConsumer      to apply an {@code AsyncAPITriggerCommand}
    * @param minBackoffMs         first delay before reconnecting
    * @param maxBackoffMs         maximum delay before reconnecting
    * @param pingIntervalMs       interval of keep-alive pings, used to detect dead connections
    */
   public MicrocksWebSocketConnector(Vertx vertx, String restBaseUrl, ObjectMapper mapper, Runnable synchronizer,
         Consumer<ServiceViewChangeEvent> serviceEventConsumer, Consumer<AsyncAPITriggerCommand> triggerConsumer,
         long minBackoffMs, long maxBackoffMs, long pingIntervalMs) {
      this.vertx = vertx;
      this.wsBaseUrl = toWebSocketBaseUrl(restBaseUrl);
      this.mapper = mapper;
      this.synchronizer = synchronizer;
      this.serviceEventConsumer = serviceEventConsumer;
      this.triggerConsumer = triggerConsumer;
      this.minBackoffMs = minBackoffMs;
      this.maxBackoffMs = maxBackoffMs;
      this.pingIntervalMs = pingIntervalMs;
   }

   /**
    * Set the supplier of the Authorization header value sent on each WebSocket handshake. It may throw if a token
    * cannot be obtained, which is handled like a failed connection.
    * @param supplier The supplier, returning empty when Microcks requires no authentication
    */
   public void setAuthorizationSupplier(Supplier<Optional<String>> supplier) {
      this.authorizationSupplier = supplier;
   }

   /**
    * Convert an HTTP(S) base URL into a WS(S) one, without trailing slash.
    * @param restBaseUrl The URL of Microcks app, such as http://localhost:8080
    * @return The WebSocket base URL, such as ws://localhost:8080
    */
   public static String toWebSocketBaseUrl(String restBaseUrl) {
      String url = restBaseUrl.trim();
      if (url.regionMatches(true, 0, "https://", 0, 8)) {
         url = "wss://" + url.substring(8);
      } else if (url.regionMatches(true, 0, "http://", 0, 7)) {
         url = "ws://" + url.substring(7);
      } else if (!url.startsWith("ws://") && !url.startsWith("wss://")) {
         url = "ws://" + url;
      }
      while (url.endsWith("/")) {
         url = url.substring(0, url.length() - 1);
      }
      return url;
   }

   /** Start connecting in background. Never blocks nor fails if Microcks is not reachable. */
   public synchronized void start() {
      if (running) {
         return;
      }
      running = true;
      client = vertx.createWebSocketClient(new WebSocketClientOptions().setMaxMessageSize(MAX_MESSAGE_SIZE)
            .setConnectTimeout((int) CONNECT_TIMEOUT_MS));
      supervisor.submit(this::superviseLoop);
   }

   /** Stop the connector and close the current connection. */
   public synchronized void stop() {
      running = false;
      supervisor.shutdownNow();
      Connection connection = current;
      if (connection != null) {
         connection.close();
      }
      worker.shutdownNow();
      if (client != null) {
         client.close();
      }
   }

   private void superviseLoop() {
      long delay = minBackoffMs;
      while (running) {
         Connection connection = new Connection();
         current = connection;
         try {
            logger.infof("Connecting to Microcks WebSocket endpoints at %s", wsBaseUrl);
            connection.open();
            logger.info("Connected to Microcks, synchronizing...");
            worker.submit(() -> {
               if (connection.active) {
                  synchronizer.run();
               }
            }).get();
            logger.info("Synchronization done, now listening to Microcks events");
            delay = minBackoffMs;
            connection.awaitClosed();
            logger.warn("Connection to Microcks lost");
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
         } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            logger.warnf("Cannot connect or synchronize with Microcks (%s), retrying in %d ms", cause.toString(),
                  delay);
            logger.debug("Connection failure details", e);
         } finally {
            connection.close();
         }
         if (!running) {
            break;
         }
         try {
            Thread.sleep(delay);
         } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
         }
         delay = Math.min(delay * 2, maxBackoffMs);
      }
   }

   private static Thread daemon(Runnable r, String name) {
      Thread t = new Thread(r, name);
      t.setDaemon(true);
      return t;
   }

   /** A pair of WebSocket connections that live and die together. */
   private class Connection {
      private final CountDownLatch closed = new CountDownLatch(1);
      private volatile boolean active = true;
      private volatile long lastPong = System.currentTimeMillis();
      private volatile WebSocket servicesSocket;
      private volatile WebSocket triggersSocket;

      void open() throws Exception {
         servicesSocket = connect(SERVICES_UPDATES_PATH,
               message -> handle(SERVICES_UPDATES_PATH, message, ServiceViewChangeEvent.class, serviceEventConsumer));
         triggersSocket = connect(ASYNCAPI_TRIGGERS_PATH,
               message -> handle(ASYNCAPI_TRIGGERS_PATH, message, AsyncAPITriggerCommand.class, triggerConsumer));
      }

      private WebSocket connect(String path, Consumer<String> messageHandler) throws Exception {
         WebSocketConnectOptions options = new WebSocketConnectOptions().setAbsoluteURI(wsBaseUrl + path);
         authorizationSupplier.get().ifPresent(value -> options.addHeader("Authorization", value));
         WebSocket ws = client.connect(options).toCompletionStage().toCompletableFuture().get(CONNECT_TIMEOUT_MS + 1000,
               TimeUnit.MILLISECONDS);
         ws.textMessageHandler(messageHandler::accept);
         ws.pongHandler(b -> lastPong = System.currentTimeMillis());
         ws.closeHandler(v -> closed.countDown());
         ws.exceptionHandler(t -> {
            logger.warnf("WebSocket error on %s: %s", path, t.toString());
            closed.countDown();
         });
         // Tell the server we are ready, as it ignores it anyway.
         ws.writeTextMessage("_ready_");
         logger.infof("Connected to %s", wsBaseUrl + path);
         return ws;
      }

      private <T> void handle(String path, String message, Class<T> type, Consumer<T> consumer) {
         logger.debugf("Received this WebSocket message for %s: %s", path, message);
         try {
            worker.submit(() -> {
               if (!active) {
                  return;
               }
               try {
                  consumer.accept(mapper.readValue(message, type));
               } catch (Exception e) {
                  logger.errorf(e, "Ignoring WebSocket message on %s that cannot be read or applied", path);
               }
            });
         } catch (RuntimeException e) {
            logger.debugf("Dropping message on %s as connector is stopped", path);
         }
      }

      /** Block until a socket is closed or stops answering to pings. */
      void awaitClosed() throws InterruptedException {
         while (!closed.await(pingIntervalMs, TimeUnit.MILLISECONDS)) {
            if (System.currentTimeMillis() - lastPong > 2 * pingIntervalMs + 1000) {
               logger.warn("No pong received from Microcks, considering connection as dead");
               return;
            }
            servicesSocket.writePing(Buffer.buffer("ping"));
            triggersSocket.writePing(Buffer.buffer("ping"));
         }
      }

      void close() {
         active = false;
         closed.countDown();
         for (WebSocket ws : new WebSocket[] { servicesSocket, triggersSocket }) {
            if (ws != null && !ws.isClosed()) {
               ws.close();
            }
         }
      }
   }
}
