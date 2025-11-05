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
package io.github.microcks.web;

import io.github.microcks.event.TraceEvent;
import io.github.microcks.service.SpanStorageService;
import io.github.microcks.util.tracing.SpanFilterUtil;

import io.opentelemetry.sdk.trace.data.SpanData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service for managing subscriptions to trace updates via Server-Sent Events (SSE). Clients can subscribe with filters
 * on service name and operation name, and will receive updates when matching traces are updated.
 * @author Apoorva Srinivas Appadoo
 */
@Component
public class TraceSubscriptionManager {

   /** A commons logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(TraceSubscriptionManager.class);

   private final SpanStorageService spanStorageService;
   private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

   public TraceSubscriptionManager(SpanStorageService spanStorageService) {
      this.spanStorageService = spanStorageService;
   }

   /**
    * Register a new subscription with filters.
    */
   public SseEmitter subscribe(String serviceName, String operationName, String clientAddress) {
      SseEmitter emitter = new SseEmitter(0L); // No timeout
      subscribe(emitter, serviceName, operationName, clientAddress);
      return emitter;
   }

   /**
    * Subscribe to Custom Emitter
    */
   public void subscribe(SseEmitter emitter, String serviceName, String operationName, String clientAddress) {
      Subscription sub = new Subscription(emitter, serviceName, operationName, clientAddress);

      subscriptions.add(sub);

      emitter.onCompletion(() -> subscriptions.remove(sub));
      emitter.onTimeout(() -> subscriptions.remove(sub));
      emitter.onError(e -> subscriptions.remove(sub));

      // send initial heartbeat
      try {
         emitter.send(SseEmitter.event().name("heartbeat").data(Collections.emptyList()));
      } catch (IOException ignored) {
      }
   }

   /**
    * Handle trace updates and push data to matching subscribers.
    */
   @EventListener
   public void onTraceUpdated(TraceEvent event) {
      log.trace("Received trace update event for traceId='{}'", event.traceId());
      List<SpanData> spans = spanStorageService.getSpansForTrace(event.traceId());
      if (spans.isEmpty())
         return;

      for (Subscription sub : subscriptions) {
         if (SpanFilterUtil.matchesTraceEvent(event, sub.serviceName(), sub.operationName(), sub.clientAddress())) {
            try {
               sub.emitter().send(SseEmitter.event().name("trace").data(spans));
            } catch (IOException e) {
               sub.emitter().complete();
               subscriptions.remove(sub);
            }
         }
      }
   }

   /**
    * Periodic heartbeat to keep connections alive.
    */
   @Scheduled(fixedRate = 15000)
   public void sendHeartbeats() {
      for (Subscription sub : subscriptions) {
         try {
            sub.emitter().send(SseEmitter.event().name("heartbeat").data(Collections.emptyList())
                  .id(String.valueOf(Instant.now().toEpochMilli())));
         } catch (IOException e) {
            sub.emitter().complete();
            subscriptions.remove(sub);
         }
      }
   }


   private record Subscription(SseEmitter emitter, String serviceName, String operationName, String clientAddress) {
   }
}
