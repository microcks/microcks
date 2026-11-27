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
package io.microcks.config;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

/**
 * A configuration source factory providing the {@code microcks.uber.mongodb-url} property. The embedded MongoDB Java
 * server is started lazily when this property is first resolved, that is when the Quarkus MongoDB client reads its
 * {@code quarkus.mongodb.connection-string} (defined as {@code ${microcks.uber.mongodb-url}} in the uber
 * configuration). This guarantees that the server is listening before any connection attempt, with no startup ordering
 * to manage. The storage file is read from {@code mongodb.storage.file} (or {@code MONGODB_STORAGE_PATH}).
 * @author laurent
 */
public class EmbeddedMongoConfigSourceFactory implements ConfigSourceFactory {

   /** The name of the property that resolves to the embedded server connection string. */
   public static final String MONGODB_URL_PROPERTY = "microcks.uber.mongodb-url";

   private static final String STORAGE_FILE_PROPERTY = "mongodb.storage.file";
   private static final String STORAGE_PATH_ENV = "MONGODB_STORAGE_PATH";

   @Override
   public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
      ConfigValue storageFile = context.getValue(STORAGE_FILE_PROPERTY);
      String storage = storageFile != null && storageFile.getValue() != null ? storageFile.getValue()
            : System.getenv(STORAGE_PATH_ENV);
      return List.of(new EmbeddedMongoConfigSource(storage));
   }

   @Override
   public OptionalInt getPriority() {
      // Above environment variables and system properties so that these are visible in the context.
      return OptionalInt.of(450);
   }

   private static class EmbeddedMongoConfigSource implements ConfigSource {

      private final String storageFile;

      EmbeddedMongoConfigSource(String storageFile) {
         this.storageFile = storageFile;
      }

      @Override
      public Set<String> getPropertyNames() {
         // Not enumerated on purpose: resolving the value starts the server.
         return Collections.emptySet();
      }

      @Override
      public String getValue(String propertyName) {
         if (MONGODB_URL_PROPERTY.equals(propertyName)) {
            return EmbeddedMongoServer.start(storageFile);
         }
         return null;
      }

      @Override
      public String getName() {
         return "EmbeddedMongoConfigSource";
      }

      @Override
      public int getOrdinal() {
         return 450;
      }
   }
}
