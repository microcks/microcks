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
package io.microcks.web;

import io.microcks.event.TraceEvent;
import io.microcks.service.SpanStorageService;
import io.microcks.util.tracing.SpanFilterUtil;

import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.MultiEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing subscriptions to trace updates via Server-Sent Events (SSE). Clients can subscribe with filters
 * on service name and operation name, and will receive updates when matching traces are updated.
 * @author Apoorva Srinivas Appadoo
 */
@ApplicationScoped
public class TraceSubscriptionManager {

   /** A commons logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(TraceSubscriptionManager.class);

   private final SpanStorageService spanStorageService;
   private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

   @Inject
   public TraceSubscriptionManager(SpanStorageService spanStorageService) {
      this.spanStorageService = spanStorageService;
   }

   /**
    * Register a new subscription with filters.
    * @param sse           The JAX-RS SSE factory used to build events
    * @param serviceName   The service name filter
    * @param operationName The operation name filter
    * @param clientAddress The client address filter
    * @return A stream of SSE events, without timeout
    */
   public Multi<OutboundSseEvent> subscribe(Sse sse, String serviceName, String operationName, String clientAddress) {
      return Multi.createFrom().emitter(emitter -> {
         Subscription sub = new Subscription(sse, emitter, serviceName, operationName, clientAddress);
         subscriptions.add(sub);
         emitter.onTermination(() -> subscriptions.remove(sub));

         // Send initial heartbeat.
         emitter.emit(sse.newEventBuilder().name("heartbeat")
               .mediaType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).data(Collections.emptyList()).build());
      });
   }

   /**
    * Handle trace updates and push data to matching subscribers.
    * @param event The trace event
    */
   public void onTraceUpdated(@ObservesAsync TraceEvent event) {
      log.trace("Received trace update event for traceId='{}'", event.traceId());
      List<SpanData> spans = spanStorageService.getSpansForTrace(event.traceId());
      if (spans.isEmpty())
         return;

      for (Subscription sub : subscriptions) {
         if (SpanFilterUtil.matchesTraceEvent(event, sub.serviceName(), sub.operationName(), sub.clientAddress())) {
            sub.emitter().emit(sub.sse().newEventBuilder().name("trace")
                  .mediaType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).data(spans).build());
         }
      }
   }

   /**
    * Periodic heartbeat to keep connections alive.
    */
   @Scheduled(every = "15s")
   public void sendHeartbeats() {
      for (Subscription sub : subscriptions) {
         sub.emitter()
               .emit(sub.sse().newEventBuilder().name("heartbeat")
                     .mediaType(jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE).data(Collections.emptyList())
                     .id(String.valueOf(Instant.now().toEpochMilli())).build());
      }
   }

   private record Subscription(Sse sse, MultiEmitter<? super OutboundSseEvent> emitter, String serviceName,
         String operationName, String clientAddress) {
   }
}
