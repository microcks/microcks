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
package io.github.microcks.minion.async.producer;

import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Header;
import io.github.microcks.minion.async.AsyncMockDefinition;
import io.github.microcks.minion.async.AsyncMockRepository;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import java.util.List;
import java.util.Set;

/**
 * WebSocket implementation of producer for async event messages.
 * @author laurent
 */
public class WebSocketProducerManager {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   AsyncMockRepository asyncMockRepository;

   @Inject
   WebSocketSessionRegistry sessionRegistry;

   public WebSocketProducerManager() {
   }

   /**
    * Publish a message on specified channel.
    * @param channel The destination channel for message
    * @param message The message payload
    * @param headers A set of headers if any (maybe null or empty)
    */
   public void publishMessage(String channel, String message, Set<Header> headers) {
      logger.infof("Publishing on channel {%s}, message: %s ", channel, message);

      List<Session> sessions = sessionRegistry.getSessions(channel);
      if (sessions != null && !sessions.isEmpty()) {
         logger.debugf("Sending message to %d WebSocket sessions", sessions.size());
         sessions.forEach(s -> {
            s.getAsyncRemote().sendObject(message, result -> {
               if (result.getException() != null) {
                  logger.error("Unable to send message: " + result.getException());
               }
            });
         });
      }
   }

   @OnOpen
   public void onOpen(Session session, @PathParam("service") String service, @PathParam("version") String version) {
      logger.infof("New WebSocket session opening on service {%s} - {%s}. Checking if mocked...", service, version);

      // If service or version were encoded with '+' instead of '%20', remove them.
      if (service.contains("+")) {
         service = service.replace('+', ' ');
      }
      if (version.contains("+")) {
         version = version.replace('+', ' ');
      }

      Set<AsyncMockDefinition> definitions = asyncMockRepository.getMockDefinitionsByServiceAndVersion(service,
            version);
      if (definitions != null && !definitions.isEmpty()) {
         sessionRegistry.putSession(session);
      } else {
         try {
            logger.infof("No mock available on '%s', closing the session", session.getRequestURI().toString());
            session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT,
                  "No mock available on " + session.getRequestURI()));
         } catch (Exception e) {
            logger.infof("Caught an exception while rejecting a WebSocket opening on unmanaged '%'",
                  session.getRequestURI().toString());
         }
      }
   }

   @OnClose
   public void onClose(Session session, @PathParam("service") String service, @PathParam("version") String version) {
      sessionRegistry.removeSession(session);
   }

   @OnError
   public void onError(Session session, @PathParam("service") String service, @PathParam("version") String version,
         Throwable throwable) {
      sessionRegistry.removeSession(session);
   }

   @OnMessage
   public void onMessage(String message, @PathParam("service") String service, @PathParam("version") String version) {
      // Nothing to do here.
      logger.debug("Received a message on WebSocketProducerManager, nothing to do...");
   }

   /**
    * Get the Websocket endpoint URI corresponding to a AsyncMockDefinition, sanitizing all parameters.
    * @param definition   The AsyncMockDefinition
    * @param eventMessage The message to get topic
    * @return The request URI corresponding to def and message
    */
   public String getRequestURI(AsyncMockDefinition definition, EventMessage eventMessage) {
      // Produce service name part of topic name.
      String serviceName = definition.getOwnerService().getName().replace(" ", "+");

      // Produce version name part of topic name.
      String versionName = definition.getOwnerService().getVersion().replace(" ", "+");

      // Produce operation name part of topic name.
      String operationName = ProducerManager.getDestinationOperationPart(definition.getOperation(), eventMessage);

      return "/api/ws/" + serviceName + "/" + versionName + "/" + operationName;
   }
}
