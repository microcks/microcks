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

import io.github.microcks.service.SpanStorageService;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for accessing trace and span information stored by the SpanStorageService. Provides endpoints to
 * retrieve traces, spans
 *
 */
@RestController
@RequestMapping("/api/traces")
public class TracingController {

   private final SpanStorageService spanStorageService;


   public TracingController(SpanStorageService spanStorageService) {
      this.spanStorageService = spanStorageService;
   }

   /**
    * Get all trace IDs currently stored.
    *
    * @return Set of trace IDs
    */
   @GetMapping
   public ResponseEntity<Set<String>> getAllTraceIds() {
      return ResponseEntity.ok(spanStorageService.getAllTraceIds());
   }

   /**
    * Get all spans for a specific trace ID.
    *
    * @param traceId The trace ID to look up
    * @return List of spans for the trace
    */
   @GetMapping("/{traceId}/spans")
   public ResponseEntity<List<SpanData>> getSpansForTrace(@PathVariable("traceId") String traceId) {
      List<ReadableSpan> spans = spanStorageService.getSpansForTrace(traceId);
      if (spans.isEmpty()) {
         return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(spans.stream().map(ReadableSpan::toSpanData).toList());
   }

   @GetMapping("/operations/spans")
   public ResponseEntity<List<List<ReadableSpan>>> getSpansForOperation(@RequestParam("serviceName") String serviceName,
         @RequestParam("operationName") String operationName) {
      List<String> traceIds = spanStorageService
            .queryTraceIdsBySpanAttributes(Map.of(io.opentelemetry.api.common.AttributeKey.stringKey("service.name"),
                  serviceName, io.opentelemetry.api.common.AttributeKey.stringKey("operation.name"), operationName));
      if (traceIds.isEmpty()) {
         return ResponseEntity.notFound().build();
      }

      List<List<ReadableSpan>> spansByTraceId = traceIds.stream().map(spanStorageService::getSpansForTrace).toList();


      return ResponseEntity.ok(spansByTraceId);
   }

   /**
    * Clear all stored traces and spans.
    *
    * @return Success message
    */
   @DeleteMapping
   public ResponseEntity<String> clearAllTraces() {
      spanStorageService.clearAll();
      return ResponseEntity.ok("All traces and spans have been cleared");
   }

}
