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
package io.github.microcks.web.dto;

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

import java.util.List;

/**
 * Data Transfer Object for SpanData that excludes the resource field from serialization. This is used to avoid sending
 * redundant system information (host details, process info, etc.) in the tracing API responses.
 */
public class SpanDataDTO implements SpanData {
   private final SpanData delegate;

   public SpanDataDTO(SpanData delegate) {
      this.delegate = delegate;
   }

   @Override
   public String getName() {
      return delegate.getName();
   }

   @Override
   public SpanKind getKind() {
      return delegate.getKind();
   }

   @Override
   public SpanContext getSpanContext() {
      return delegate.getSpanContext();
   }

   @Override
   public SpanContext getParentSpanContext() {
      return delegate.getParentSpanContext();
   }

   @Override
   public StatusData getStatus() {
      return delegate.getStatus();
   }

   @Override
   public long getStartEpochNanos() {
      return delegate.getStartEpochNanos();
   }

   @Override
   public Attributes getAttributes() {
      return delegate.getAttributes();
   }

   @Override
   public List<EventData> getEvents() {
      return delegate.getEvents();
   }

   @Override
   public List<LinkData> getLinks() {
      return delegate.getLinks();
   }

   @Override
   @JsonIgnore // Exclude resource from JSON serialization
   public Resource getResource() {
      return Resource.empty();
   }

   @Override
   public InstrumentationScopeInfo getInstrumentationScopeInfo() {
      return delegate.getInstrumentationScopeInfo();
   }

   @Override
   @Deprecated
   public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
      return delegate.getInstrumentationLibraryInfo();
   }

   @Override
   public boolean hasEnded() {
      return delegate.hasEnded();
   }

   @Override
   public long getEndEpochNanos() {
      return delegate.getEndEpochNanos();
   }

   @Override
   public int getTotalRecordedEvents() {
      return delegate.getTotalRecordedEvents();
   }

   @Override
   public int getTotalRecordedLinks() {
      return delegate.getTotalRecordedLinks();
   }

   @Override
   public int getTotalAttributeCount() {
      return delegate.getTotalAttributeCount();
   }
}
