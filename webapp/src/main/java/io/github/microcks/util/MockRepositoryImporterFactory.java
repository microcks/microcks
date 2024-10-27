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

import io.github.microcks.util.asyncapi.AsyncAPI3Importer;
import io.github.microcks.util.asyncapi.AsyncAPIImporter;
import io.github.microcks.util.graphql.GraphQLImporter;
import io.github.microcks.util.grpc.ProtobufImporter;
import io.github.microcks.util.har.HARImporter;
import io.github.microcks.util.metadata.ExamplesImporter;
import io.github.microcks.util.metadata.MetadataImporter;
import io.github.microcks.util.openapi.OpenAPIImporter;
import io.github.microcks.util.postman.PostmanCollectionImporter;
import io.github.microcks.util.postman.PostmanWorkspaceCollectionImporter;
import io.github.microcks.util.soapui.SoapUIProjectImporter;
import io.github.microcks.util.openapi.SwaggerImporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Factory for building/retrieving mock repository importer implementations. For now, it implements a very simple
 * algorithm : if repository is a JSON file (guess on first lines content), it assume repository it implemented as a
 * Postman collection and then uses PostmanCollectionImporter; otherwise it uses SoapUIProjectImporter.
 * @author laurent
 */
public class MockRepositoryImporterFactory {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(MockRepositoryImporterFactory.class);

   /** A RegExp for detecting a line containing the openapi: 3 pragma. */
   public static final String OPENAPI_3_REGEXP = ".*['\\\"]?openapi['\\\"]?\\s*:\\s*['\\\"]?[3\\.].*";

   /** A RegExp for detecting a line containing the asyncapi: 2 pragma. */
   public static final String ASYNCAPI_2_REGEXP = ".*['\\\"]?asyncapi['\\\"]?\\s*:\\s*['\\\"]?[2\\.].*";

   /** A RegExp for detecting a line containing the asyncapi: 3 pragma. */
   public static final String ASYNCAPI_3_REGEXP = ".*['\\\"]?asyncapi['\\\"]?\\s*:\\s*['\\\"]?[3\\.].*";

   /** A RegExp for detecting a line containing the swagger pragma. */
   public static final String SWAGGER_REGEXP = ".*['\\\"]?swagger['\\\"]?\\s*:\\s*.*";

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
      MockRepositoryImporter importer = null;

      // Analyse first lines of file content to guess repository type.
      String line = null;
      try (BufferedReader reader = Files.newBufferedReader(mockRepository.toPath(), StandardCharsets.UTF_8)) {
         while ((line = reader.readLine()) != null && importer == null) {
            line = line.trim();
            // Check with basic Postman formats..
            importer = checkPostmanImporters(line, mockRepository);
            // Then try OpenAPI related ones...
            if (importer == null) {
               importer = checkOpenAPIImporters(line, mockRepository, referenceResolver);
            }
            // Then try any other else.
            if (importer == null) {
               importer = checkOtherImporters(line, mockRepository, referenceResolver);
            }
         }
      }

      // Otherwise, default to HAR project importer implementation because it has no proper identity marker.
      if (importer == null) {
         log.info("Have not found any explicit marker so applying the default HTTP Archive (HAR) importer...");
         importer = new HARImporter(mockRepository.getPath());
      }

      return importer;
   }

   private static MockRepositoryImporter checkPostmanImporters(String line, File mockRepository) throws IOException {
      if (line.startsWith("\"_postman_id\":")) {
         log.info("Found a _postman_id in file so assuming it's a Postman Collection to import");
         return new PostmanCollectionImporter(mockRepository.getPath());
      } else if (line.startsWith("\"collection\":") || line.startsWith("{\"collection\":")) {
         log.info("Found a collection in file so assuming it's a Postman Workspace Collection to import");
         return new PostmanWorkspaceCollectionImporter(mockRepository.getPath());
      }
      return null;
   }

   private static MockRepositoryImporter checkOpenAPIImporters(String line, File mockRepository,
         ReferenceResolver referenceResolver) throws IOException {
      if (line.matches(OPENAPI_3_REGEXP)) {
         log.info("Found an openapi: 3 pragma in file so assuming it's an OpenAPI spec to import");
         return new OpenAPIImporter(mockRepository.getPath(), referenceResolver);
      } else if (line.matches(SWAGGER_REGEXP)) {
         log.info("Found an swagger: pragma in file so assuming it's a Swagger spec to import");
         return new SwaggerImporter(mockRepository.getPath(), referenceResolver);
      }
      return null;
   }

   private static MockRepositoryImporter checkOtherImporters(String line, File mockRepository,
         ReferenceResolver referenceResolver) throws IOException {
      if (line.startsWith("<?xml")) {
         log.info("Found a XML pragma in file so assuming it's a SoapUI Project to import");
         return new SoapUIProjectImporter(mockRepository.getPath());
      } else if (line.matches(ASYNCAPI_2_REGEXP)) {
         log.info("Found an asyncapi: 2 pragma in file so assuming it's an AsyncAPI spec to import");
         return new AsyncAPIImporter(mockRepository.getPath(), referenceResolver);
      } else if (line.matches(ASYNCAPI_3_REGEXP)) {
         log.info("Found an asyncapi: 3 pragma in file so assuming it's an AsyncAPI spec to import");
         return new AsyncAPI3Importer(mockRepository.getPath(), referenceResolver);
      } else if (line.startsWith("syntax = \"proto3\";") || line.startsWith("syntax=\"proto3\";")) {
         log.info("Found a syntax = proto3 pragma in file so assuming it's a GRPC Protobuf spec to import");
         return new ProtobufImporter(mockRepository.getPath(), referenceResolver);
      } else if (line.contains("kind: APIMetadata")) {
         log.info("Found a kind: APIMetadata pragma in file so assuming it's a Microcks APIMetadata to import");
         return new MetadataImporter(mockRepository.getPath());
      } else if (line.contains("kind: APIExamples")) {
         log.info("Found a kind: APIExamples pragma in file so assuming it's a Microcks APIExamples to import");
         return new ExamplesImporter(mockRepository.getPath());
      } else if (line.contains("type Query {") || line.contains("type Mutation {")
            || line.startsWith("# microcksId:")) {
         log.info("Found query, mutation or microcksId: pragma in file so assuming it's a GraphQL schema to import");
         return new GraphQLImporter(mockRepository.getPath());
      }
      return null;
   }
}
