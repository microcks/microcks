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

import io.opentelemetry.context.propagation.TextMapPropagator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry tracing wiring for Spring Boot 4. The local span storage processor
 * ({@link io.github.microcks.util.otel.CustomExplainTraceProcessor}) is registered automatically as a
 * {@code @Component} and picked up by Spring's {@code OpenTelemetryTracingAutoConfiguration} via its
 * {@code ObjectProvider<SpanProcessor>} aggregation. The {@link XTraceIdTextMapPropagator} is exposed here as a Spring
 * bean so it composes alongside the W3C / Baggage propagators that Spring's tracing autoconfig provides.
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
 * </pre>
 */
@Configuration
public class OtelAutoConfigurationCustomizerProvider {

   @Bean
   public TextMapPropagator xTraceIdTextMapPropagator() {
      return new XTraceIdTextMapPropagator();
   }
}
