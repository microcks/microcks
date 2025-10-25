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
package io.github.microcks.util.otel;

import io.github.microcks.service.SpanStorageService;
import io.github.microcks.util.tracing.TraceUtil;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SpanProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * A custom SpanProcessor that delegates span storage to SpanStorageService. This processor filters and forwards
 * relevant spans to the storage service for collection and retrieval.
 * @author Apoorva Srinivas Appadoo
 */
@Component
public class CustomExplainTraceProcessor implements SpanProcessor {

   /** A commons logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(CustomExplainTraceProcessor.class);

   private final SpanStorageService spanStorageService;

   /**
    * Construct a CustomExplainTraceProcessor with the given SpanStorageService.
    * @param spanStorageService the span storage service
    */
   public CustomExplainTraceProcessor(SpanStorageService spanStorageService) {
      this.spanStorageService = spanStorageService;
   }

   @Override
   public void onStart(@Nonnull Context parentContext, @Nonnull ReadWriteSpan span) {
      // Called when a span is started - we don't need to do anything here
   }

   @Override
   public boolean isStartRequired() {
      return false; // We don't need to be notified when spans start
   }

   @Override
   public void onEnd(@Nonnull ReadableSpan span) {
      // Called when a span ends - delegate to storage service if span matches filter criteria
      // if span has attribute explain-trace save it
      Map<AttributeKey<?>, Object> attributes = span.toSpanData().getAttributes().asMap();

      if (attributes.keySet().stream()
            .anyMatch(key -> SpanStorageService.valuesEqualAttr(key, TraceUtil.EXPLAIN_TRACE_ATTRIBUTE))) {
         spanStorageService.storeSpan(span);
      }
   }

   @Override
   public boolean isEndRequired() {
      return true; // We need to be notified when spans end
   }

   @Override
   public void close() {
      // Clean up resources when the processor is closed
      log.info("TraceSpanProcessor is being closed. Clearing all stored spans.");
      spanStorageService.clearAll();
   }
}
