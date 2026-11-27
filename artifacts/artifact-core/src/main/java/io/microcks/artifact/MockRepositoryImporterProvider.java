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

import io.microcks.util.ReferenceResolver;

import java.io.IOException;

/**
 * Service provider interface contributed by each artifact format module to be discovered by the
 * {@link MockRepositoryImporterFactory} through {@link java.util.ServiceLoader}. Implementations must be declared in
 * <code>META-INF/services/io.microcks.artifact.MockRepositoryImporterProvider</code>.
 * @author laurent
 */
public interface MockRepositoryImporterProvider {

   /**
    * Get the detection priority. Providers are evaluated by ascending priority for each line of the artifact.
    * @return The priority (lower is evaluated first)
    */
   int priority();

   /**
    * Tell if this provider recognizes the artifact format from a trimmed line of its content.
    * @param line A trimmed line from the beginning of the artifact
    * @return True if the line is a marker of the format handled by this provider
    */
   boolean accepts(String line);

   /**
    * Tell if this provider recognizes the artifact format, with access to the line as read (not trimmed, possibly
    * starting with a byte order mark). Defaults to {@link #accepts(String)}; override it when indentation matters.
    * @param line    A trimmed line from the beginning of the artifact
    * @param rawLine The same line, as read from the file
    * @return True if the line is a marker of the format handled by this provider
    */
   default boolean accepts(String line, String rawLine) {
      return accepts(line);
   }

   /**
    * Create the importer for the artifact.
    * @param path              The path of the artifact file
    * @param referenceResolver The resolver to use during import (may be null)
    * @return A new importer instance
    * @throws IOException in case of file access
    */
   MockRepositoryImporter create(String path, ReferenceResolver referenceResolver) throws IOException;

   /**
    * Tell if this provider is the one to use when no other provider recognized the artifact.
    * @return True if this is the fallback provider
    */
   default boolean isDefault() {
      return false;
   }
}
