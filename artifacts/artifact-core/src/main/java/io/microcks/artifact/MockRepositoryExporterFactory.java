/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * Factory for building/retrieving mock repository exporter implementations. Exporters are contributed by artifact
 * format modules as {@link MockRepositoryExporterProvider} discovered through {@link ServiceLoader}.
 * @author laurent
 */
public class MockRepositoryExporterFactory {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(MockRepositoryExporterFactory.class);

   /** A constant representing the APIExamples export format option. */
   public static final String API_EXAMPLES_FORMAT = "APIExamples";
   /** A constant representing the OpenAPI Overlay export format option. */
   public static final String OPENAPI_OVERLAY_FORMAT = "OAS Overlay";

   private MockRepositoryExporterFactory() {
      // Private constructor to hide the implicit one as it's a utility class.
   }

   /**
    * Create the right MockRepositoryExporter implementation depending on requested format.
    * @param format The requested export format.
    * @return An instance of MockRepositoryExporter implementation
    */
   public static MockRepositoryExporter getMockRepositoryExporter(String format) {
      MockRepositoryExporter fallback = null;
      for (MockRepositoryExporterProvider provider : ServiceLoader.load(MockRepositoryExporterProvider.class)) {
         if (provider.format().equalsIgnoreCase(format)) {
            return provider.create();
         }
         if (API_EXAMPLES_FORMAT.equalsIgnoreCase(provider.format())) {
            fallback = provider.create();
         }
      }
      log.info("The request format {} is unknown. Returning an APIExamples exporter as the default.", format);
      if (fallback == null) {
         throw new IllegalStateException("No exporter registered for format " + format + " and no default exporter");
      }
      return fallback;
   }
}
