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
package io.github.microcks.minions.async.client;

import io.github.microcks.event.ServiceViewChangeEvent;
import io.github.microcks.minion.async.AsyncMockDefinitionUpdater;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.Unremovable;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * This is a connector to Microcks app WebSocket endpoints. It listens to /api/services-updates endpoint for incoming
 * {@code ServiceViewChangeEvent} and propagate them to the {@code AsyncMockDefinitionUpdater}.
 * @author laurent
 */
@Unremovable
@ApplicationScoped
public class MicrocksWebSocketConnector {

   /** Get a JBoss logging logger. */
   private static final Logger logger = Logger.getLogger(MicrocksWebSocketConnector.class);

   @ConfigProperty(name = "minion.microcks-host-port")
   String microcksHostAndPort;

   /** Application startup method. */
   void onStart(@Observes StartupEvent event) throws URISyntaxException, DeploymentException, IOException {
      URI uri = null;

      try {
         uri = new URI("ws://" + microcksHostAndPort + "/api/services-updates");
         ContainerProvider.getWebSocketContainer().connectToServer(WebSocketClient.class, uri);
         logger.debug("Now connected to Microcks /api/services-updates WS endpoint");
      } catch (URISyntaxException e) {
         logger.error("Exception while building the Microcks WebSocket URI, check your settings", e);
         throw e;
      } catch (DeploymentException e) {
         logger.error("Caught a WebSocket DeploymentException", e);
         throw e;
      } catch (IOException e) {
         logger.info("Caught an IOException while connecting Microcks WebSocket endpoint", e);
         throw e;
      }
   }

   @ClientEndpoint
   public static class WebSocketClient {

      private final AsyncMockDefinitionUpdater definitionUpdater;
      private final ObjectMapper mapper;

      /**
       * Create a WebSocketClient with mandatory dependencies.
       * @param definitionUpdater to update Async mocks definition when needed
       * @param mapper            to deserialize ServiceViewChangeEvents
       */
      public WebSocketClient(AsyncMockDefinitionUpdater definitionUpdater, ObjectMapper mapper) {
         this.definitionUpdater = definitionUpdater;
         this.mapper = mapper;
      }

      @OnOpen
      public void open(Session session) {
         logger.debug("Opening a WebSocket Session on Microcks server");
         // Send a message to indicate that we are ready,
         // as the message handler may not be registered immediately after this callback.
         session.getAsyncRemote().sendText("_ready_");
      }

      @OnMessage
      public void message(String message) {
         logger.debugf("Received this WebSocket message: " + message);
         try {
            ServiceViewChangeEvent serviceViewChangeEvent = mapper.readValue(message, ServiceViewChangeEvent.class);
            logger.infof("Received a new change event [%s] for '%s', at %d", serviceViewChangeEvent.getChangeType(),
                  serviceViewChangeEvent.getServiceId(), serviceViewChangeEvent.getTimestamp());

            definitionUpdater.applyServiceChangeEvent(serviceViewChangeEvent);
         } catch (Exception e) {
            logger.error("WebSocket message cannot be converted into a ServiceViewChangeEvent", e);
            logger.error("Ignoring this WebSocket message");
         }
      }
   }
}
