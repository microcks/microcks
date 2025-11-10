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
package io.github.microcks.config;

import io.github.microcks.service.SpanStorageService;
import io.github.microcks.util.otel.CustomExplainTraceProcessor;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.trace.SdkTracerProviderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry auto-configuration customizer that adds a custom span processor for local trace storage. This processor
 * runs alongside the default OpenTelemetry export flow to external systems.
 *
 * <pre>
 * Trace Flow:
 * ===========
 *
 *  ┌─────────────────┐            ┌──────────────────────┐
 *  │   Application   │───────────▶│   OpenTelemetry      │
 *  │     traces      │            │   SDK                │
 *  └─────────────────┘            └──────────────────────┘
 *                                           │
 *                                           ▼
 *                                 ┌─────────────────────┐
 *                                 │    Span Created     │
 *                                 └─────────────────────┘
 *                                           │
 *                                           ▼
 *                                  ┌────────┴────────┐
 *                                  │                 │
 *                                  ▼                 ▼
 *           ┌─────────────────────────────┐   ┌─────────────────────────────┐
 *           │ CustomExplainTraceProcessor │   │  Default Batch Processor    │
 *           │     (stores locally)        │   │ (exports to external sys.)  │
 *           └─────────────────────────────┘   └─────────────────────────────┘
 *                          │                                 │
 *                          ▼                                 ▼
 *           ┌─────────────────────────────┐   ┌─────────────────────────────┐
 *           │     SpanStorageService      │   │         Exporters           │
 *           │      (In-Memory)            │   │  (Jaeger, Tempo, OTLP)      │
 *           └─────────────────────────────┘   └─────────────────────────────┘
 *                                                            │
 *                                                            ▼
 *                                           ┌─────────────────────────────────┐
 *                                           │         External Systems        │
 *                                           │  (Jaeger, Tempo, OTLP, etc.)    │
 *                                           └─────────────────────────────────┘
 *
 * The CustomExplainTraceProcessor adds local storage capability without interfering
 * with the standard OpenTelemetry export pipeline to external tracing systems.
 * </pre>
 *
 * @see CustomExplainTraceProcessor
 * @see SpanStorageService
 * @author Apoorva Srinivas Appadoo
 */
@Configuration
public class OtelAutoConfigurationCustomizerProvider {

   private final SpanStorageService spanStorageService;

   @Autowired
   public OtelAutoConfigurationCustomizerProvider(SpanStorageService spanStorageService) {
      this.spanStorageService = spanStorageService;
   }

   @Bean
   public AutoConfigurationCustomizerProvider otelCustomizer() {
      return p -> p.addTracerProviderCustomizer(this::configureSdkTracerProvider);
   }

   private SdkTracerProviderBuilder configureSdkTracerProvider(SdkTracerProviderBuilder tracerProvider,
         ConfigProperties config) {
      return tracerProvider.addSpanProcessor(new CustomExplainTraceProcessor(spanStorageService));
   }
}
