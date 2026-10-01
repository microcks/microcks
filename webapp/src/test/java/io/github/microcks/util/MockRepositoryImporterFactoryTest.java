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
import io.github.microcks.util.metadata.MetadataImporter;
import io.github.microcks.util.openapi.OpenAPIImporter;
import io.github.microcks.util.openapi.SwaggerImporter;
import io.github.microcks.util.opencollection.OpenCollectionImporter;
import io.github.microcks.util.postman.PostmanCollectionImporter;
import io.github.microcks.util.postman.PostmanWorkspaceCollectionImporter;
import io.github.microcks.util.soapui.SoapUIProjectImporter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is a test case for MockRepositoryImporterFactory.
 * @author laurent
 */
class MockRepositoryImporterFactoryTest {

   @Test
   void testGetMockRepositoryImporter() {

      // Load a SoapUI file.
      File soapUIProject = new File("../samples/HelloService-soapui-project.xml");
      MockRepositoryImporter importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(soapUIProject, null);
      } catch (Throwable t) {
         fail("Getting importer for SoapUI should not fail!");
      }
      assertTrue(importer instanceof SoapUIProjectImporter);

      // Load a Postman file.
      File postmanCollection = new File("../samples/PetstoreAPI-collection.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(postmanCollection, null);
      } catch (Throwable t) {
         fail("Getting importer for Postman should not fail!");
      }
      assertTrue(importer instanceof PostmanCollectionImporter);

      // Load a Postman Workspace file.
      File postmanWorkspaceCollection = new File(
            "target/test-classes/io/github/microcks/util/postman/Swagger Petstore.postman_workspace_collection-2.1.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(postmanWorkspaceCollection, null);
      } catch (Throwable t) {
         fail("Getting importer for Postman Workspace should not fail!");
      }
      assertTrue(importer instanceof PostmanWorkspaceCollectionImporter);

      // Load an OpenAPI YAML file.
      importer = null;
      File openAPISpec = new File("target/test-classes/io/github/microcks/util/openapi/cars-openapi.yaml");
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(openAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer for OpenAPI YAML should not fail!");
      }
      assertTrue(importer instanceof OpenAPIImporter);

      // Load an OpenAPI JSON file.
      openAPISpec = new File("target/test-classes/io/github/microcks/util/openapi/cars-openapi.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(openAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer for OpenAPI JSON should not fail!");
      }
      assertTrue(importer instanceof OpenAPIImporter);

      // Load an OpenAPI JSON oneliner file.
      openAPISpec = new File("target/test-classes/io/github/microcks/util/openapi/openapi-oneliner.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(openAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer for OpenAPI JSON oneliner should not fail!");
      }
      assertTrue(importer instanceof OpenAPIImporter);

      // Load an AsyncAPI JSON file.
      File asyncAPISpec = new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(asyncAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer for AsyncAPI JSON should not fail!");
      }
      assertTrue(importer instanceof AsyncAPIImporter);

      // Load an AsyncAPI JSON oneliner file.
      asyncAPISpec = new File(
            "target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-oneliner.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(asyncAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer for AsyncAPI JSON oneliner should not fail!");
      }
      assertTrue(importer instanceof AsyncAPIImporter);

      // Load an AsyncAPI YAML file.
      asyncAPISpec = new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(asyncAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer for AsyncAPI YAML should not fail!");
      }
      assertTrue(importer instanceof AsyncAPIImporter);

      // Load an AsyncAPI v3 YAML file.
      asyncAPISpec = new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi-3.0.yaml");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(asyncAPISpec, null);
      } catch (Throwable t) {
         fail("Getting importer for AsyncAPI v3 YAML should not fail!");
      }
      assertTrue(importer instanceof AsyncAPI3Importer);

      // Load a Protobuf schema file.
      File protobufSchema = new File("target/test-classes/io/github/microcks/util/grpc/hello-v1.proto");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(protobufSchema, null);
      } catch (Throwable t) {
         fail("Getting importer for Protobuf should not fail!");
      }
      assertTrue(importer instanceof ProtobufImporter);

      // Load an APIMetadata file.
      File apiMetadata = new File("target/test-classes/io/github/microcks/util/metadata/hello-grpc-v1-metadata.yml");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(apiMetadata, null);
      } catch (Throwable t) {
         fail("Getting importer for APIMetadata should not fail!");
      }
      assertTrue(importer instanceof MetadataImporter);

      // Load a GraphQL schema file.
      File graphQLSchema = new File("target/test-classes/io/github/microcks/util/graphql/films.graphql");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(graphQLSchema, null);
      } catch (Throwable t) {
         fail("Getting importer for GraphQL should not fail!");
      }
      assertTrue(importer instanceof GraphQLImporter);

      // Load a Swagger v2 YAML file.
      File swaggerSpec = new File("target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-swagger.yaml");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(swaggerSpec, null);
      } catch (Throwable t) {
         fail("Getting importer for Swagger v2 YAML should not fail!");
      }
      assertTrue(importer instanceof SwaggerImporter);

      // Load a Swagger v2 JSON file.
      swaggerSpec = new File("target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-swagger.json");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(swaggerSpec, null);
      } catch (Throwable t) {
         fail("Getting importer for Swagger v2 JSON should not fail!");
      }
      assertTrue(importer instanceof SwaggerImporter);

      // Load a HAR JSON file.
      File harFile = new File("target/test-classes/io/github/microcks/util/har/api-pastries-0.0.1.har");
      importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(harFile, null);
      } catch (IOException ioe) {
         fail("Getting importer for HAR JSON file should not fail!");
      }
      assertTrue(importer instanceof HARImporter);
   }

   @Test
   void testGetMockRepositoryImporterForOpenCollection() {
      File openCollection = new File("../samples/PetstoreAPI-opencollection.yml");

      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(openCollection, null),
            "Getting importer for OpenCollection should not fail!");

      assertInstanceOf(OpenCollectionImporter.class, importer);
   }

   @Test
   void testNestedOpenCollectionKeyIsNotMistakenForAnOpenCollection() {
      File openAPIWithNestedKey = new File(
            "target/test-classes/io/github/microcks/util/opencollection/nested-opencollection-key.yml");

      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(openAPIWithNestedKey, null),
            "Getting importer for a file with a nested opencollection key should not fail!");

      assertInstanceOf(OpenAPIImporter.class, importer);
   }

   @Test
   void testGetMockRepositoryImporterForPrettyPrintedJsonOpenCollection() {
      File openCollection = new File(
            "target/test-classes/io/github/microcks/util/opencollection/pretty-printed-opencollection.json");

      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(openCollection, null),
            "Getting importer for a JSON OpenCollection should not fail!");

      assertInstanceOf(OpenCollectionImporter.class, importer);
   }

   @Test
   void testGetMockRepositoryImporterForNotBundledOpenCollection() {
      File notBundled = new File(
            "target/test-classes/io/github/microcks/util/opencollection/petstore-bundled-false.yml");

      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(notBundled, null),
            "Getting importer for a multi-file OpenCollection should not fail!");

      assertInstanceOf(OpenCollectionImporter.class, importer);
   }

   @Test
   void testOpenCollectionParsingError() {
      File unreadable = new File(
            "target/test-classes/io/github/microcks/util/opencollection/invalid-opencollection.yml");

      IOException exception = assertThrows(IOException.class,
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(unreadable, null));

      assertEquals("OpenCollection file parsing error", exception.getMessage());
   }

   static Stream<Arguments> firstLinesAndTheirImporter() {
      String petstoreInfo = "info:\n  name: Petstore API\n  version: \"1.0\"\n";
      String minimalOpenAPI = "openapi: 3.0.2\ninfo:\n  title: Petstore API\n  version: \"1.0\"\npaths: {}\n";
      return Stream.of(Arguments.of("opencollection: 1.0.0\n" + petstoreInfo, OpenCollectionImporter.class),
            Arguments.of("opencollection: \"1.0.0\"\n" + petstoreInfo, OpenCollectionImporter.class),
            Arguments.of("\"opencollection\": \"1.0.0\"\n" + petstoreInfo, OpenCollectionImporter.class),
            Arguments.of("opencollection: 2.0.0\n" + petstoreInfo, OpenCollectionImporter.class),
            Arguments.of("opencollection-extra: x\n" + minimalOpenAPI, OpenAPIImporter.class),
            Arguments.of(minimalOpenAPI, OpenAPIImporter.class));
   }

   @ParameterizedTest
   @MethodSource("firstLinesAndTheirImporter")
   void testOpenCollectionDetectionLine(String content, Class<?> expectedImporter, @TempDir Path directory)
         throws IOException {
      Path file = Files.writeString(directory.resolve("collection.yml"), content, UTF_8);

      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(file.toFile(), null));

      assertInstanceOf(expectedImporter, importer);
   }
}
