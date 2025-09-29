package io.github.microcks.service;

import io.github.microcks.event.TraceEvent;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.PatternSyntaxException;

/**
 * Service for managing subscriptions to trace updates via Server-Sent Events (SSE). Clients can subscribe with filters
 * on service name and operation name, and will receive updates when matching traces are updated.
 */
@Service
public class TraceSubscriptionService {

   private final SpanStorageService spanStorageService;
   private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();

   public TraceSubscriptionService(SpanStorageService spanStorageService) {
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
      List<ReadableSpan> spans = spanStorageService.getSpansForTrace(event.traceId());
      if (spans.isEmpty())
         return;

      for (Subscription sub : subscriptions) {
         boolean matches = spans.stream()
               .anyMatch(span -> matchesWildcard(sub.serviceName(), event.service())
                     && matchesWildcard(sub.operationName(), event.operation())
                     && matchesWildcard(sub.clientAddress(), event.clientAddress()));

         if (matches) {
            List<SpanData> spanDataList = spans.stream().map(ReadableSpan::toSpanData).toList();

            try {
               sub.emitter().send(SseEmitter.event().name("trace").data(spanDataList));
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

   /**
    * Check if a value matches a pattern with optional regex support.
    *
    * @param pattern The pattern to match against (can be null, "*", or regex pattern).
    * @param value   The value to check (can be null).
    * @return True if the value matches the pattern, false otherwise.
    */
   private boolean matchesWildcard(String pattern, String value) {
      if (pattern == null || value == null) {
         return pattern == null && value == null;
      }

      // If pattern is "*", match everything
      if ("*".equals(pattern)) {
         return true;
      }

      // Try regex matching first
      try {
         return value.matches(pattern);
      } catch (PatternSyntaxException e) {
         // If regex fails, fall back to exact match
         return pattern.equals(value);
      }
   }

   private record Subscription(SseEmitter emitter, String serviceName, String operationName, String clientAddress) {
   }
}
