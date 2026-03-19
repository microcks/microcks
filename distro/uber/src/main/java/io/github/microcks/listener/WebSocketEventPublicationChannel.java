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

import io.github.microcks.event.AsyncAPITriggerCommand;
import io.github.microcks.event.ServiceViewChangeEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * This is an implementation of {@code ServiceChangeEventChannel} that uses a WebSocket endpoint as a destination
 * recipient for {@code ServiceViewChangeEvent}.
 * @author laurent
 */
@Component
@Primary
@Profile("uber")
public class WebSocketEventPublicationChannel extends TextWebSocketHandler implements EventPublicationChannel {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(WebSocketEventPublicationChannel.class);

   private final ObjectMapper mapper = new ObjectMapper();

   private final List<WebSocketSession> serviceChangesSessions = new CopyOnWriteArrayList<>();
   private final List<WebSocketSession> asyncAPITriggersSessions = new CopyOnWriteArrayList<>();

   @Override
   public void sendServiceViewChangeEvent(ServiceViewChangeEvent event) throws Exception {
      log.debug("Sending ServiceViewChangeEvent to {} connected WS sessions", serviceChangesSessions.size());
      for (WebSocketSession wsSession : serviceChangesSessions) {
         wsSession.sendMessage(new TextMessage(mapper.writeValueAsString(event)));
      }
   }

   @Override
   public void sendAsyncAPITriggerCommand(AsyncAPITriggerCommand command) throws Exception {
      log.debug("Sending AsyncAPITriggerCommand to {} connected WS sessions", asyncAPITriggersSessions.size());
      for (WebSocketSession wsSession : asyncAPITriggersSessions) {
         wsSession.sendMessage(new TextMessage(mapper.writeValueAsString(command)));
      }
   }

   @Override
   public void afterConnectionEstablished(WebSocketSession session) throws Exception {
      log.debug("afterConnectionEstablished, session: {}", session.getId());
      if (session.getUri() == null || session.getUri().getPath() == null) {
         log.warn("URI path is null, cannot determine which session list to add to");
         return;
      }
      String uriPath = session.getUri().getPath();
      if (uriPath.endsWith("/api/services-updates")) {
         serviceChangesSessions.add(session);
      } else if (uriPath.endsWith("/api/asyncapi-triggers")) {
         asyncAPITriggersSessions.add(session);
      }
   }

   @Override
   public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
      log.debug("afterConnectionClosed, session: {}", session.getId());
      if (session.getUri() == null || session.getUri().getPath() == null) {
         log.warn("URI path is null, cannot determine which session list to remove from");
         return;
      }
      String uriPath = session.getUri().getPath();
      if (uriPath.endsWith("/api/services-updates")) {
         serviceChangesSessions.remove(session);
      } else if (uriPath.endsWith("/api/asyncapi-triggers")) {
         asyncAPITriggersSessions.remove(session);
      }
   }
}
