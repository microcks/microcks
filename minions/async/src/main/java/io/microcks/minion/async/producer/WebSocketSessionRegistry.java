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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.Session;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple registry for holding WebSocket sessions based on the requested channel path. This registry is intended to be
 * shared by the different WebSocket endpoints that allows client registration. Root producer will then use the registry
 * to select target sessions when a mock get published on a channel path.
 * @author laurent
 */
@ApplicationScoped
public class WebSocketSessionRegistry {

   private final Map<String, List<Session>> sessions = new ConcurrentHashMap<>();

   /**
    * Store a session within registry according the requested URI.
    * @param session A WebSocket session
    */
   public void putSession(Session session) {
      // As session.getRequestURI() doubles query parameters but session.getQueryString() not,
      // we need to build the channelURI manually.
      String channelURI = session.getRequestURI().getPath();
      if (session.getQueryString() != null) {
         channelURI += "?" + session.getQueryString();
      }

      List<Session> channelSessions = sessions.computeIfAbsent(channelURI, k -> new ArrayList<>());
      channelSessions.add(session);
   }

   /**
    * Get the sessions connected to a request URI.
    * @param requestURI The URI to get sessions for.
    * @return A List of WebSocket sessions.
    */
   public List<Session> getSessions(String requestURI) {
      return sessions.get(requestURI);
   }

   /**
    * Remove a session from registry (typically when closed or errored).
    * @param session The WebSocket session to remote
    */
   public void removeSession(Session session) {
      List<Session> channelSessions = sessions.get(session.getRequestURI().toString());
      if (channelSessions != null) {
         channelSessions.remove(session);
      }
   }
}
