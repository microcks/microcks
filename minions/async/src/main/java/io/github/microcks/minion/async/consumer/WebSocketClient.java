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
package io.github.microcks.minion.async.consumer;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@ClientEndpoint
/**
 * A simple WebSocket client that stores messages as <code>ConsumedMessage</code>s. It supports both the
 * {@class jakarta.websocket.ClientEndpoint} annotation and the generic {@class jakarta.websocket.Endpoint} interface,
 * registering itself as a new message handler.
 * @author laurent
 */
public class WebSocketClient extends Endpoint {

   private List<ConsumedMessage> messages = new ArrayList<>();

   /**
    * Create a new WebSocket Client endpoint.
    */
   public WebSocketClient() {
   }

   @Override
   public void onOpen(Session session, EndpointConfig endpointConfig) {
      session.addMessageHandler(new MessageHandler.Whole<String>() {
         public void onMessage(String messagePayload) {
            storeMessage(messagePayload);
         }
      });
   }

   @OnOpen
   public void onOpen(Session session) {
      // Nothing to do on session opening.
   }

   @OnMessage
   public void onMessage(String messagePayload) {
      storeMessage(messagePayload);
   }

   /**
    * Get the list of consumed messages.
    * @return The consumed message during client connection time.
    */
   public List<ConsumedMessage> getMessages() {
      return messages;
   }

   /**
    * Store after having transformed into a ConsumedMessage.
    * @param messagePayload The payload to store
    */
   protected void storeMessage(String messagePayload) {
      // Build a ConsumedMessage from Kafka record.
      ConsumedMessage message = new ConsumedMessage();
      message.setReceivedAt(System.currentTimeMillis());
      message.setPayload(messagePayload.getBytes(StandardCharsets.UTF_8));
      messages.add(message);
   }
}
