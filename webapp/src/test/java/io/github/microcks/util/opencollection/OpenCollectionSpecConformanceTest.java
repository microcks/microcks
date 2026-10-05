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
package io.github.microcks.util.opencollection;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.MockRepositoryImporterFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conformance of the OpenCollection importer to the OpenCollection v1.0.0 JSON schema
 * (https://schema.opencollection.com/opencollection/v1.0.0.json). Each case of the spec-derived catalogue is one small
 * fixture file under <code>spec/</code>, named after its case id; cases already covered by a fixture of the other
 * OpenCollection tests are not repeated. The files of the <code>spec/realistic</code> folder are our own synthetic
 * collections, shaped like the ones real tools export.
 * @author SebastienDegodez
 */
class OpenCollectionSpecConformanceTest {

   private static final String FIXTURES = "target/test-classes/io/github/microcks/util/opencollection/";

   private static final String SPEC_FIXTURES = FIXTURES + "spec/";

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
         "root/a02-opencollection-other-1x.yml | Svc | 1.0 | GET /pet",
         "root/a07-opencollection-major-only.yml | Svc | 1.0 | GET /pet",
         "root/a13-unknown-root-key.yml | Svc | 1.0 | GET /pet", "root/a14-items-absent.yml | Svc | 1.0 |",
         "root/a15-items-empty-list.yml | Svc | 1.0 |", "info/b01-version-non-numeric-string.yml | Svc | v3 | GET /pet",
         "info/b08-name-with-accents.yml | Gestes activés | 1.0 | GET /pet",
         "info/b09-info-authors-and-summary.yml | Svc | 1.0 | GET /pet",
         "info/b10-info-unknown-key.yml | Svc | 1.0 | GET /pet",
         "items/c02-folder-without-info.yml | Svc | 1.0 | GET /pet",
         "items/c13-http-item-with-app-key.yml | Svc | 1.0 | GET /pet",
         "http/d02-custom-method-purge.yml | Svc | 1.0 | PURGE /pet",
         "http/d03-method-surrounded-by-spaces.yml | Svc | 1.0 | GET /pet",
         "http/d10-path-and-query-params.yml | Svc | 1.0 | GET /pet/:id",
         "http/d11-param-without-value.yml | Svc | 1.0 | GET /pet" })
   void testSpecValidDocumentIsImported(String fixture, String serviceName, String serviceVersion, String operations) {
      Service service = serviceOf(SPEC_FIXTURES + fixture);

      assertEquals(serviceName, service.getName());
      assertEquals(serviceVersion, service.getVersion());
      assertEquals(operations == null ? List.of() : Arrays.asList(operations.split(";")), operationNamesOf(service));
   }

   /** Documents the schema rejects, refused because the importer reads the faulty field. */
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
         "root/a12-bundled-null.yml | 'bundled' property must be a boolean" })
   void testSpecInvalidDocumentIsRejected(String fixture, String messageFragment) {
      assertImportIsRejected(SPEC_FIXTURES + fixture, messageFragment);
   }

   /** Documents the schema accepts, refused because of a Microcks rule (supported version, identity, path). */
   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
         "root/a06-opencollection-empty.yml | Only OpenCollection 1.x",
         "info/b06-name-whitespace-only.yml | Name property is missing",
         "info/b07-info-absent.yml | Name property is missing", "http/d05-method-empty.yml | http.method",
         "http/d08-url-empty.yml | http.url", "url/e10-bare-segment.yml | no path found after host prefix",
         "url/e18-space-in-path.yml | path contains whitespace",
         "realistic/h02-bruno-export-without-version.yaml | Version property is missing" })
   void testDocumentMicrocksCannotImportIsRejected(String fixture, String messageFragment) {
      assertImportIsRejected(SPEC_FIXTURES + fixture, messageFragment);
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', value = { "url/e02-literal-https-with-base-path.yml | GET /v2/pet",
         "url/e03-localhost-port-and-path.yml | GET /api/v1/tasks/8", "url/e08-path-only.yml | GET /pet",
         "url/e15-fragment.yml | GET /pet", "url/e17-percent-encoded-space.yml | GET /pet%20shop",
         "url/e19-ipv6-host.yml | GET /pet", "url/e20-userinfo-in-authority.yml | GET /pet",
         "url/e21-websocket-scheme.yml | GET /pet" })
   void testOperationNameFromSpecUrl(String fixture, String expectedOperation) {
      assertEquals(List.of(expectedOperation), operationNamesOf(serviceOf(SPEC_FIXTURES + fixture)));
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { "detection/f04-yaml-document-start.yml", "detection/f04-yaml-leading-comment.yml",
         "detection/f06-g03-crlf-line-endings.yml" })
   void testSpecFileIsDetectedAsOpenCollection(String fixture) {
      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(new File(SPEC_FIXTURES + fixture), null));

      assertEquals(OpenCollectionImporter.class, importer.getClass());
   }

   @ParameterizedTest(name = "{0}")
   @ValueSource(strings = { SPEC_FIXTURES + "detection/f03-minified-json.json",
         SPEC_FIXTURES + "detection/f06-g03-crlf-line-endings.yml", SPEC_FIXTURES + "info/b08-name-with-accents.yml",
         FIXTURES + "petstore-opencollection.json", SPEC_FIXTURES + "realistic/h02-bruno-export-without-version.yaml" })
   void testSourceIsStoredByteForByte(String fixture) throws IOException {
      OpenCollectionImporter importer = importerFor(fixture);
      Service service = new Service();
      service.setName("Any");
      service.setVersion("1.0");

      Resource resource = assertDoesNotThrow(() -> importer.getResourceDefinitions(service)).get(0);

      assertArrayEquals(Files.readAllBytes(Path.of(fixture)),
            resource.getContent().getBytes(java.nio.charset.StandardCharsets.UTF_8));
   }

   @Test
   void testResourceNameForJsonSourceKeepsYamlExtension() {
      OpenCollectionImporter importer = importerFor(FIXTURES + "petstore-opencollection.json");
      Service service = assertDoesNotThrow(importer::getServiceDefinitions).get(0);

      Resource resource = assertDoesNotThrow(() -> importer.getResourceDefinitions(service)).get(0);

      assertEquals("Petstore API-1.0.yaml", resource.getName());
   }

   private static void assertImportIsRejected(String path, String messageFragment) {
      OpenCollectionImporter importer = importerFor(path);

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains(messageFragment), exception.getMessage());
   }

   private static OpenCollectionImporter importerFor(String path) {
      return assertDoesNotThrow(() -> new OpenCollectionImporter(path));
   }

   private static Service serviceOf(String path) {
      return assertDoesNotThrow(importerFor(path)::getServiceDefinitions).get(0);
   }

   private static List<String> operationNamesOf(Service service) {
      return service.getOperations().stream().map(Operation::getName).toList();
   }
}
