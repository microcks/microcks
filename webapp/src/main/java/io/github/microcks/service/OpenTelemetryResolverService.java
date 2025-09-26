package io.github.microcks.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

/**
 * A resolver class to obtain an OpenTelemetry instance from the Spring application context. If no OpenTelemetry bean is
 * found, it falls back to the global OpenTelemetry instance.
 *
 */
@Service
public class OpenTelemetryResolverService {
   private final ApplicationContext applicationContext;

   public OpenTelemetryResolverService(ApplicationContext applicationContext) {
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
