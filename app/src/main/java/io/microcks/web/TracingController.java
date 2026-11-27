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

import io.microcks.service.SpanStorageService;
import io.microcks.util.SafeLogger;

import io.opentelemetry.sdk.trace.data.SpanData;

import io.smallrye.common.annotation.RunOnVirtualThread;
import io.smallrye.mutiny.Multi;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;

import java.util.List;
import java.util.Set;

/**
 * REST controller for accessing trace and span information stored by the SpanStorageService. Provides endpoints to
 * retrieve traces, spans
 * @author Apoorva Srinivas Appadoo
 */
@Path("/api/traces")
@Produces(MediaType.APPLICATION_JSON)
public class TracingController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(TracingController.class);

   private final SpanStorageService spanStorageService;
   private final TraceSubscriptionManager traceSubscriptionManager;

   /**
    * Build a TracingController with the given SpanStorageService.
    * @param spanStorageService       the span storage service
    * @param traceSubscriptionManager the trace subscription manager
    */
   @Inject
   public TracingController(SpanStorageService spanStorageService, TraceSubscriptionManager traceSubscriptionManager) {
      this.spanStorageService = spanStorageService;
      this.traceSubscriptionManager = traceSubscriptionManager;
   }

   /**
    * Get all trace IDs currently stored.
    * @return Set of trace IDs
    */
   @GET
   @RunOnVirtualThread
   public Set<String> getAllTraceIds() {
      return spanStorageService.getAllTraceIds();
   }

   /**
    * Get all spans for a specific trace ID.
    * @param traceId The trace ID to look up
    * @return List of spans for the trace
    */
   @GET
   @Path("/{traceId}/spans")
   @RunOnVirtualThread
   public Response getSpansForTrace(@PathParam("traceId") String traceId) {
      List<SpanData> spans = spanStorageService.getSpansForTrace(traceId);
      if (spans.isEmpty()) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      return Response.ok(spans).build();
   }

   @GET
   @Path("/operations")
   @RunOnVirtualThread
   public Response getTracesForOperation(@QueryParam("serviceName") String serviceName,
         @QueryParam("operationName") String operationName,
         @QueryParam("clientAddress") @DefaultValue(".*") String clientAddress) {
      List<String> traceIds = spanStorageService.queryTraceIdsByPatterns(serviceName, operationName, clientAddress);
      if (traceIds.isEmpty()) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }

      List<List<SpanData>> spansByTraceId = traceIds.stream().map(spanStorageService::getSpansForTrace).toList();

      return Response.ok(spansByTraceId).build();
   }

   /**
    * Clear all stored traces and spans.
    * @return Success message
    */
   @DELETE
   @RunOnVirtualThread
   public Response clearAllTraces() {
      spanStorageService.clearAll();
      return Response.ok("All traces and spans have been cleared").type(MediaType.TEXT_PLAIN).build();
   }

   @GET
   @Path("/operations/stream")
   @Produces(MediaType.SERVER_SENT_EVENTS)
   public Multi<OutboundSseEvent> streamTraces(@Context Sse sse, @QueryParam("serviceName") String serviceName,
         @QueryParam("operationName") String operationName, @QueryParam("clientAddress") String clientAddress) {
      return traceSubscriptionManager.subscribe(sse, serviceName, operationName, clientAddress);
   }
}
