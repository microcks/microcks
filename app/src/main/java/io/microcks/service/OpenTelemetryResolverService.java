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

   /**
    * Build a resolver service with the given application context.
    * @param applicationContext The Spring application context
    */
   public OpenTelemetryResolverService(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   /**
    * Get an OpenTelemetry instance from the application context or fall back to the global instance.
    * @return An OpenTelemetry instance
    */
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
