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

import io.microcks.minion.async.AsyncMinionApp;
import io.microcks.minion.async.AsyncMockDefinitionUpdater;
import io.microcks.minion.async.AsyncMockProducerTrigger;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Wires the {@link MicrocksWebSocketConnector} to the minion beans and manages its lifecycle. The WebSocket base URL is
 * derived from the Microcks REST client URL, so there is a single source of truth to reach Microcks app.
 * @author laurent
 */
@Unremovable
@ApplicationScoped
public class MicrocksWebSocketLifecycle {

   private final Vertx vertx;
   private final ObjectMapper mapper;
   private final AsyncMinionApp minionApp;
   private final AsyncMockDefinitionUpdater definitionUpdater;
   private final AsyncMockProducerTrigger producerTrigger;
   private final MicrocksTokenProvider tokenProvider;

   @ConfigProperty(name = "io.microcks.minion.async.client.MicrocksAPIConnector/mp-rest/url")
   String microcksUrl;

   @ConfigProperty(name = "minion.microcks-ws.min-backoff", defaultValue = "1000")
   long minBackoffMs;

   @ConfigProperty(name = "minion.microcks-ws.max-backoff", defaultValue = "30000")
   long maxBackoffMs;

   @ConfigProperty(name = "minion.microcks-ws.ping-interval", defaultValue = "30000")
   long pingIntervalMs;

   private MicrocksWebSocketConnector connector;

   /**
    * Create a lifecycle bean with required dependencies.
    * @param vertx             The Vert.x instance
    * @param mapper            To deserialize events
    * @param minionApp         To synchronize with Microcks API
    * @param definitionUpdater To apply service change events
    * @param producerTrigger   To apply AsyncAPI trigger commands
    * @param tokenProvider     To authenticate WebSocket connections
    */
   public MicrocksWebSocketLifecycle(Vertx vertx, ObjectMapper mapper, AsyncMinionApp minionApp,
         AsyncMockDefinitionUpdater definitionUpdater, AsyncMockProducerTrigger producerTrigger,
         MicrocksTokenProvider tokenProvider) {
      this.vertx = vertx;
      this.mapper = mapper;
      this.minionApp = minionApp;
      this.definitionUpdater = definitionUpdater;
      this.producerTrigger = producerTrigger;
      this.tokenProvider = tokenProvider;
   }

   void onStart(@Observes StartupEvent event) {
      connector = new MicrocksWebSocketConnector(vertx, microcksUrl, mapper, minionApp::synchronize,
            definitionUpdater::applyServiceChangeEvent, producerTrigger::applyAsyncAPITriggerCommand, minBackoffMs,
            maxBackoffMs, pingIntervalMs);
      connector.setAuthorizationSupplier(() -> {
         try {
            return tokenProvider.authorization();
         } catch (ConnectorException e) {
            throw new IllegalStateException(e.getMessage(), e);
         }
      });
      connector.start();
   }

   void onStop(@Observes ShutdownEvent event) {
      if (connector != null) {
         connector.stop();
      }
   }
}
