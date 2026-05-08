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

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Weaves {@link WithSpan @WithSpan} annotations into proper tracer spans. Spring Boot 4's official OpenTelemetry
 * starter does not bundle a {@code WithSpanAspect}, so we provide a minimal one ourselves to keep the existing
 * REST/gRPC {@code processInvocation} instrumentation working.
 *
 * <p>
 * The span is opened with the configured name and kind, made current for the duration of the method, and ended on
 * return. Thrown exceptions are recorded and the span status is set to {@link StatusCode#ERROR}.
 *
 * <p>
 * Disabled by setting {@code otel.instrumentation.annotations.enabled=false}.
 */
@Aspect
@Component
@ConditionalOnProperty(prefix = "otel.instrumentation.annotations", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WithSpanAspect {

   private static final String INSTRUMENTATION_NAME = "io.github.microcks.with-span";

   private final Tracer tracer;

   public WithSpanAspect(OpenTelemetry openTelemetry) {
      this.tracer = openTelemetry.getTracer(INSTRUMENTATION_NAME);
   }

   @Around("@annotation(io.opentelemetry.instrumentation.annotations.WithSpan)")
   public Object traceMethod(ProceedingJoinPoint pjp) throws Throwable {
      Method method = ((MethodSignature) pjp.getSignature()).getMethod();
      WithSpan annotation = method.getAnnotation(WithSpan.class);

      String spanName = annotation.value().isEmpty()
            ? method.getDeclaringClass().getSimpleName() + "." + method.getName()
            : annotation.value();
      SpanKind kind = annotation.kind();

      Span span = tracer.spanBuilder(spanName).setSpanKind(kind).startSpan();
      try (Scope ignored = span.makeCurrent()) {
         return pjp.proceed();
      } catch (Throwable t) {
         span.recordException(t);
         span.setStatus(StatusCode.ERROR, t.getMessage() == null ? "" : t.getMessage());
         throw t;
      } finally {
         span.end();
      }
   }
}
