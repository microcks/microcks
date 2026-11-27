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
package io.microcks.service;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

/**
 * A resolver class to obtain an OpenTelemetry instance from the CDI container. If no OpenTelemetry bean is found, it
 * falls back to the global OpenTelemetry instance.
 * @author laurent
 */
@ApplicationScoped
public class OpenTelemetryResolverService {

   private final Instance<OpenTelemetry> openTelemetry;

   /**
    * Build a resolver service.
    * @param openTelemetry The lazily resolved OpenTelemetry bean
    */
   @Inject
   public OpenTelemetryResolverService(Instance<OpenTelemetry> openTelemetry) {
      this.openTelemetry = openTelemetry;
   }

   /**
    * Get an OpenTelemetry instance from the container or fall back to the global instance.
    * @return An OpenTelemetry instance
    */
   public OpenTelemetry getOpenTelemetry() {
      if (openTelemetry != null && openTelemetry.isResolvable()) {
         return openTelemetry.get();
      }
      return GlobalOpenTelemetry.get();
   }
}
