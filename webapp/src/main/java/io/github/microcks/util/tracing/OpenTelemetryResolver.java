package io.github.microcks.util.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

public class OpenTelemetryResolver {
   private final org.springframework.context.ApplicationContext applicationContext;

   public OpenTelemetryResolver(org.springframework.context.ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   public OpenTelemetry getOpenTelemetry() {
      OpenTelemetry openTelemetry;
      try {
         openTelemetry = applicationContext.getBean(OpenTelemetry.class);
      } catch (Exception ignored) {
         // Fall back to global if no bean is available (tests/non-boot contexts)
         openTelemetry = GlobalOpenTelemetry.get();
      }
      return openTelemetry;
   }
}
