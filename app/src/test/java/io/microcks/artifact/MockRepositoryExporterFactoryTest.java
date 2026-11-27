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
package io.github.microcks.util;

import io.github.microcks.util.metadata.ExamplesExporter;
import io.github.microcks.util.openapi.OpenAPIOverlayExporter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for MockRepositoryExporterFactory.
 * @author laurent
 */
class MockRepositoryExporterFactoryTest {

   @Test
   void testGetMockRepositoryExporter() {
      // Check nominal cases.
      MockRepositoryExporter exporter = MockRepositoryExporterFactory
            .getMockRepositoryExporter(MockRepositoryExporterFactory.API_EXAMPLES_FORMAT);
      assertTrue(exporter instanceof ExamplesExporter);

      exporter = MockRepositoryExporterFactory
            .getMockRepositoryExporter(MockRepositoryExporterFactory.OPENAPI_OVERLAY_FORMAT);
      assertTrue(exporter instanceof OpenAPIOverlayExporter);

      // Check with case-insensitive comparison.
      exporter = MockRepositoryExporterFactory.getMockRepositoryExporter("ApIExamPles");
      assertTrue(exporter instanceof ExamplesExporter);

      exporter = MockRepositoryExporterFactory.getMockRepositoryExporter("oas Overlay");
      assertTrue(exporter instanceof OpenAPIOverlayExporter);

      // Check with default.
      exporter = MockRepositoryExporterFactory.getMockRepositoryExporter("My exotic unsupported format");
      assertTrue(exporter instanceof ExamplesExporter);
   }
}
