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
package io.microcks.listener;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;

/**
 * WebSocket endpoint the async minion connects to for receiving {@code ServiceViewChangeEvent}. Incoming messages (the
 * minion sends a {@code _ready_} text on open) are ignored.
 * @author laurent
 */
@WebSocket(path = "/api/services-updates")
public class ServiceUpdatesWebSocket {

   private final WebSocketEventPublicationChannel channel;

   /**
    * Build a new ServiceUpdatesWebSocket with mandatory dependencies.
    * @param channel The channel to register connections on
    */
   public ServiceUpdatesWebSocket(WebSocketEventPublicationChannel channel) {
      this.channel = channel;
   }

   @OnOpen
   public void onOpen(WebSocketConnection connection) {
      channel.registerServiceChangesConnection(connection);
   }

   @OnTextMessage
   public void onMessage(String message) {
      // Nothing to do, the endpoint is publish only.
   }

   @OnClose
   public void onClose(WebSocketConnection connection) {
      channel.unregisterServiceChangesConnection(connection);
   }
}
