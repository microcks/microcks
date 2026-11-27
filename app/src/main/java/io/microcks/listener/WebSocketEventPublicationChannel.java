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

import io.microcks.cluster.ClusterEventBus;
import io.microcks.event.AsyncAPITriggerCommand;
import io.microcks.event.ServiceViewChangeEvent;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.WebSocketConnection;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This is the {@code EventPublicationChannel} that sends {@code ServiceViewChangeEvent} and
 * {@code AsyncAPITriggerCommand} as JSON text frames to the async minions connected on the WebSocket endpoints
 * {@code /api/services-updates} and {@code /api/asyncapi-triggers}.
 * <p>
 * When a {@code ClusterEventBus} is available (full mode with several replicas), events are published on the bus and
 * every replica, this one included, delivers them to its own connected minions: a minion gets the events whatever the
 * replica it is connected to, in the same order. Without bus (uber distribution) events are delivered locally. Each
 * kind of message is sent under a lock, so that every connection receives the messages one at a time, in order. Nothing
 * is replayed: a minion resynchronizes through the REST API when it (re)connects.
 * @author laurent
 */
@ApplicationScoped
public class WebSocketEventPublicationChannel implements EventPublicationChannel {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(WebSocketEventPublicationChannel.class);

   /** Name of the cluster topic carrying the service view changes. */
   public static final String SERVICES_UPDATES_TOPIC = "microcks-services-updates";
   /** Name of the cluster topic carrying the AsyncAPI trigger commands. */
   public static final String ASYNCAPI_TRIGGERS_TOPIC = "microcks-asyncapi-triggers";

   private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

   private final ObjectMapper mapper = new ObjectMapper();

   private final Map<String, WebSocketConnection> serviceChangesConnections = new ConcurrentHashMap<>();
   private final Map<String, WebSocketConnection> asyncAPITriggersConnections = new ConcurrentHashMap<>();

   private final Instance<ClusterEventBus> clusterBus;

   private final Object serviceChangesLock = new Object();
   private final Object asyncAPITriggersLock = new Object();

   /**
    * Build a new channel with mandatory dependencies.
    * @param clusterBus The optional cluster bus, unsatisfied when the application runs on a single instance
    */
   public WebSocketEventPublicationChannel(Instance<ClusterEventBus> clusterBus) {
      this.clusterBus = clusterBus;
   }

   /** Subscribe to the cluster topics so that events published by any replica reach the local minions. */
   void onStart(@Observes StartupEvent event) {
      if (clusterBus.isResolvable()) {
         log.info("Cluster event bus found, relaying events to the WebSocket connections of this replica");
         clusterBus.get().subscribe(SERVICES_UPDATES_TOPIC, this::deliverServiceChanges);
         clusterBus.get().subscribe(ASYNCAPI_TRIGGERS_TOPIC, this::deliverAsyncAPITriggers);
      }
   }

   @Override
   public void sendServiceViewChangeEvent(ServiceViewChangeEvent event) throws Exception {
      String message = mapper.writeValueAsString(event);
      if (clusterBus.isResolvable()) {
         clusterBus.get().publish(SERVICES_UPDATES_TOPIC, message);
      } else {
         deliverServiceChanges(message);
      }
   }

   @Override
   public void sendAsyncAPITriggerCommand(AsyncAPITriggerCommand command) throws Exception {
      String message = mapper.writeValueAsString(command);
      if (clusterBus.isResolvable()) {
         clusterBus.get().publish(ASYNCAPI_TRIGGERS_TOPIC, message);
      } else {
         deliverAsyncAPITriggers(message);
      }
   }

   private void deliverServiceChanges(String message) {
      log.debug("Sending ServiceViewChangeEvent to {} connected WS sessions", serviceChangesConnections.size());
      synchronized (serviceChangesLock) {
         broadcast(serviceChangesConnections, message);
      }
   }

   private void deliverAsyncAPITriggers(String message) {
      log.debug("Sending AsyncAPITriggerCommand to {} connected WS sessions", asyncAPITriggersConnections.size());
      synchronized (asyncAPITriggersLock) {
         broadcast(asyncAPITriggersConnections, message);
      }
   }

   /**
    * Register a connection on the services changes endpoint.
    * @param connection The newly opened connection
    */
   public void registerServiceChangesConnection(WebSocketConnection connection) {
      log.debug("services-updates connection opened: {}", connection.id());
      serviceChangesConnections.put(connection.id(), connection);
   }

   /**
    * Unregister a connection of the services changes endpoint.
    * @param connection The closed connection
    */
   public void unregisterServiceChangesConnection(WebSocketConnection connection) {
      log.debug("services-updates connection closed: {}", connection.id());
      serviceChangesConnections.remove(connection.id());
   }

   /**
    * Register a connection on the async API triggers endpoint.
    * @param connection The newly opened connection
    */
   public void registerAsyncAPITriggersConnection(WebSocketConnection connection) {
      log.debug("asyncapi-triggers connection opened: {}", connection.id());
      asyncAPITriggersConnections.put(connection.id(), connection);
   }

   /**
    * Unregister a connection of the async API triggers endpoint.
    * @param connection The closed connection
    */
   public void unregisterAsyncAPITriggersConnection(WebSocketConnection connection) {
      log.debug("asyncapi-triggers connection closed: {}", connection.id());
      asyncAPITriggersConnections.remove(connection.id());
   }

   private void broadcast(Map<String, WebSocketConnection> connections, String message) {
      for (WebSocketConnection connection : connections.values()) {
         try {
            connection.sendText(message).await().atMost(SEND_TIMEOUT);
         } catch (Exception e) {
            // Best effort: the connection is dropped, the minion resynchronizes when it reconnects.
            log.warn("Failed sending WS message to connection {}, dropping it", connection.id(), e);
            connections.remove(connection.id());
         }
      }
   }
}
