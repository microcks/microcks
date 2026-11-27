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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Factory for building/retrieving mock repository importer implementations. Importers are contributed by artifact
 * format modules as {@link MockRepositoryImporterProvider} discovered through {@link ServiceLoader}. The first lines of
 * the artifact are submitted to providers by ascending priority; when none matches, the default provider is used.
 * @author laurent
 */
public class MockRepositoryImporterFactory {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(MockRepositoryImporterFactory.class);

   private MockRepositoryImporterFactory() {
      // Private constructor to hide the implicit one as it's a utility class.
   }

   /**
    * Create the right MockRepositoryImporter implementation depending on repository type.
    * @param mockRepository    The file representing the repository type
    * @param referenceResolver The Resolver to be used during import (may be null).
    * @return An instance of MockRepositoryImporter implementation
    * @throws IOException in case of file access
    */
   public static MockRepositoryImporter getMockRepositoryImporter(File mockRepository,
         ReferenceResolver referenceResolver) throws IOException {
      List<MockRepositoryImporterProvider> providers = loadProviders();
      String path = mockRepository.getPath();

      // Analyse first lines of file content to guess repository type.
      try (BufferedReader reader = Files.newBufferedReader(mockRepository.toPath(), StandardCharsets.UTF_8)) {
         String line;
         while ((line = reader.readLine()) != null) {
            String rawLine = line;
            line = line.trim();
            for (MockRepositoryImporterProvider provider : providers) {
               if (!provider.isDefault() && provider.accepts(line, rawLine)) {
                  log.info("Artifact recognized by {}", provider.getClass().getSimpleName());
                  return provider.create(path, referenceResolver);
               }
            }
         }
      }

      // Otherwise, use the default provider (HAR project importer) because it has no proper identity marker.
      for (MockRepositoryImporterProvider provider : providers) {
         if (provider.isDefault()) {
            log.info("Have not found any explicit marker so applying the default importer {}...",
                  provider.getClass().getSimpleName());
            return provider.create(path, referenceResolver);
         }
      }
      throw new IOException("No importer found for " + path + " and no default importer is registered");
   }

   private static List<MockRepositoryImporterProvider> loadProviders() {
      List<MockRepositoryImporterProvider> providers = new ArrayList<>();
      ServiceLoader.load(MockRepositoryImporterProvider.class).forEach(providers::add);
      providers.sort(Comparator.comparingInt(MockRepositoryImporterProvider::priority));
      return providers;
   }
}
