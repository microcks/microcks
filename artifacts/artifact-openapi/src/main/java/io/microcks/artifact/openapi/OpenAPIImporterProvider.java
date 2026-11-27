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
package io.microcks.artifact.openapi;

import io.microcks.artifact.MockRepositoryImporter;
import io.microcks.artifact.MockRepositoryImporterProvider;
import io.microcks.util.ReferenceResolver;

import java.io.IOException;

/**
 * Provider detecting OpenAPI 3.x artifacts.
 * @author laurent
 */
public class OpenAPIImporterProvider implements MockRepositoryImporterProvider {

   /** A RegExp for detecting a line containing the openapi: 3 pragma. */
   public static final String OPENAPI_3_REGEXP = ".*['\\\"]?openapi['\\\"]?\\s*:\\s*['\\\"]?[3\\.].*";

   @Override
   public int priority() {
      return 20;
   }

   @Override
   public boolean accepts(String line) {
      return line.matches(OPENAPI_3_REGEXP);
   }

   @Override
   public MockRepositoryImporter create(String path, ReferenceResolver referenceResolver) throws IOException {
      return new OpenAPIImporter(path, referenceResolver);
   }
}
