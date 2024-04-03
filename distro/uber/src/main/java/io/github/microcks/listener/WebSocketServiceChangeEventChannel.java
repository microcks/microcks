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
public class WebSocketServiceChangeEventChannel extends TextWebSocketHandler implements ServiceChangeEventChannel {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(WebSocketServiceChangeEventChannel.class);

   private ObjectMapper mapper = new ObjectMapper();

   private List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

   @Override
   public void sendServiceViewChangeEvent(ServiceViewChangeEvent event) throws Exception {
      log.debug("Sending ServiceViewChangeEvent to {} connected WS sessions", sessions.size());
      for (WebSocketSession wsSession : sessions) {
         wsSession.sendMessage(new TextMessage(mapper.writeValueAsString(event)));
      }
   }

   @Override
   public void afterConnectionEstablished(WebSocketSession session) throws Exception {
      log.debug("afterConnectionEstablished, session: {}", session.getId());
      sessions.add(session);
   }

   @Override
   public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
      log.debug("afterConnectionClosed, session: {}", session.getId());
      sessions.remove(session);
   }
}
