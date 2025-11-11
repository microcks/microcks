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
package io.github.microcks.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;

import java.util.Collections;
import java.util.List;

/**
 * Memory-optimized Data Transfer Object for SpanData that stores only essential fields needed for trace analysis. Only
 * stores: span context, name, kind, status, timestamps, attributes, and events.
 */
public class SpanDataDTO implements SpanData {
   // Essential fields for trace analysis
   private final String name;
   private final SpanKind kind;
   private final SpanContext spanContext;
   private final SpanContext parentSpanContext;
   private final StatusData status;
   private final long startEpochNanos;
   private final long endEpochNanos;
   private final Attributes attributes;
   private final List<EventData> events;
   private final boolean hasEnded;
   private final int totalRecordedEvents;
   private final int totalAttributeCount;

   public SpanDataDTO(SpanData delegate) {
      // Copy only essential data to minimize memory footprint
      this.name = delegate.getName();
      this.kind = delegate.getKind();
      this.spanContext = delegate.getSpanContext();
      this.parentSpanContext = delegate.getParentSpanContext();
      this.status = delegate.getStatus();
      this.startEpochNanos = delegate.getStartEpochNanos();
      this.endEpochNanos = delegate.getEndEpochNanos();
      this.attributes = delegate.getAttributes();
      this.events = delegate.getEvents();
      this.hasEnded = delegate.hasEnded();
      this.totalRecordedEvents = delegate.getTotalRecordedEvents();
      this.totalAttributeCount = delegate.getTotalAttributeCount();
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public SpanKind getKind() {
      return kind;
   }

   @Override
   public SpanContext getSpanContext() {
      return spanContext;
   }

   @Override
   public SpanContext getParentSpanContext() {
      return parentSpanContext;
   }

   @Override
   public StatusData getStatus() {
      return status;
   }

   @Override
   public long getStartEpochNanos() {
      return startEpochNanos;
   }

   @Override
   public Attributes getAttributes() {
      return attributes;
   }

   @Override
   public List<EventData> getEvents() {
      return events;
   }

   @Override
   public List<LinkData> getLinks() {
      // Links are not stored to save memory
      return Collections.emptyList();
   }

   @Override
   @JsonIgnore
   public Resource getResource() {
      // Resource is not stored to save memory
      return Resource.empty();
   }

   @Override
   public InstrumentationScopeInfo getInstrumentationScopeInfo() {
      // Not stored to save memory - return minimal info
      return InstrumentationScopeInfo.empty();
   }

   @Override
   @Deprecated
   public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
      // Not stored to save memory - return empty
      return io.opentelemetry.sdk.common.InstrumentationLibraryInfo.empty();
   }

   @Override
   public boolean hasEnded() {
      return hasEnded;
   }

   @Override
   public long getEndEpochNanos() {
      return endEpochNanos;
   }

   @Override
   public int getTotalRecordedEvents() {
      return totalRecordedEvents;
   }

   @Override
   public int getTotalRecordedLinks() {
      // Not stored to save memory
      return 0;
   }

   @Override
   public int getTotalAttributeCount() {
      return totalAttributeCount;
   }
}
