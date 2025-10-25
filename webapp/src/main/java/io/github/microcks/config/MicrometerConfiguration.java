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

import java.util.Map;

import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

/**
 * Allows the configuration of the Micrometer observation, adding extra tags for tracking service and version in
 * metrics.
 */
@Component
public class MicrometerConfiguration extends DefaultServerRequestObservationConvention {

   @Override
   public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
      // Here, we just want to have an additional KeyValue to the observation, keeping the default values.
      return super.getLowCardinalityKeyValues(context).and(additionalTags(context));
   }

   /**
    * Extract the service and version request attributes to add them as tags.
    * @param context the server request observation context
    * @return a KeyValues instance with additional tags
    */
   protected KeyValues additionalTags(ServerRequestObservationContext context) {
      KeyValues keyValues = KeyValues.empty();

      Map<String, String> pathVariables = (Map<String, String>) context.getCarrier()
            .getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
      if (pathVariables != null) {
         // It's a call to a mock controller.
         String service = pathVariables.get("service");
         String version = pathVariables.get("version");
         if (service != null) {
            keyValues = keyValues.and(KeyValue.of("service", service));
         }
         if (version != null) {
            keyValues = keyValues.and(KeyValue.of("version", version));
         }
      } else {
         String requestPath = context.getCarrier().getRequestURI();
         if (requestPath.startsWith("/api/")) {
            // It's a call to an API controller.
            String[] segments = requestPath.split("/");
            if (segments.length >= 3) {
               keyValues = keyValues.and(KeyValue.of("service", "/api/" + segments[2]));
            }
         }
      }
      return keyValues;
   }
}
