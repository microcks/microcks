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

import org.junit.jupiter.api.Test;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for WebSocketMessageConsumptionTask.
 * @author laurent
 */
public class WebSocketMessageConsumptionTaskTest {

   @Test
   public void testAcceptEndpoint() {
      assertTrue(WebSocketMessageConsumptionTask.acceptEndpoint("ws://localhost:4000/"));

      assertTrue(WebSocketMessageConsumptionTask.acceptEndpoint("ws://localhost:4000/websocket"));

      assertTrue(WebSocketMessageConsumptionTask
            .acceptEndpoint("ws://microcks-ws.example.com/api/ws/Service/1.0.0/channel/sub/path"));
   }

   @Test
   public void testAcceptEndpointFailures() {
      assertFalse(WebSocketMessageConsumptionTask.acceptEndpoint("localhost:4000/websocket"));

      assertFalse(WebSocketMessageConsumptionTask
            .acceptEndpoint("microcks-ws.example.com/api/ws/Service/1.0.0/channel/sub/path"));
   }

   /*
    * @Test public void testBasicReceive() {
    * 
    * try { WebSocketClient client = new WebSocketClient(); //Session session =
    * ContainerProvider.getWebSocketContainer().connectToServer(client,
    * URI.create("ws://localhost:8081/api/ws/User+signed-up+WebSocket+API/0.1.9/user/signedup")); Session session =
    * ContainerProvider.getWebSocketContainer().connectToServer(client, URI.create("ws://localhost:4000/websocket"));
    * 
    * Thread.sleep(10000L);
    * 
    * List<ConsumedMessage> messages = client.getMessages(); for (int i=0; i<messages.size(); i++) {
    * System.err.println("Received: " + new String(messages.get(i).getPayload(), "UTF-8")); } } catch (Exception e) {
    * e.printStackTrace(); } }
    */
}
