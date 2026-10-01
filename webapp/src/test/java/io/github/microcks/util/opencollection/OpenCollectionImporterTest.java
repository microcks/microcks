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

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.MockRepositoryImporterFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This is a test case for class OpenCollectionImporter.
 * @author SebastienDegodez
 */
class OpenCollectionImporterTest {

   @Test
   void testSimpleProjectImport() {
      File collection = new File("../samples/PetstoreAPI-opencollection.yml");
      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(collection, null));

      // Check that basic service properties are there.
      List<Service> services = assertDoesNotThrow(importer::getServiceDefinitions);
      assertEquals(1, services.size());
      Service service = services.get(0);
      assertEquals("Petstore API", service.getName());
      assertEquals("1.0", service.getVersion());
      assertEquals(ServiceType.REST, service.getType());

      // Check that operations have been found, in document order and with the path kept verbatim.
      assertEquals(2, service.getOperations().size());
      Operation findByStatus = service.getOperations().get(0);
      assertEquals("GET /pet/findByStatus", findByStatus.getName());
      assertEquals("GET", findByStatus.getMethod());
      Operation getById = service.getOperations().get(1);
      assertEquals("GET /pet/:petId", getById.getName());
      assertEquals("GET", getById.getMethod());

      // Check that the collection is exposed as the only resource.
      List<Resource> resources = assertDoesNotThrow(() -> importer.getResourceDefinitions(service));
      assertEquals(1, resources.size());
      assertEquals("Petstore API-1.0.yaml", resources.get(0).getName());
      assertEquals(ResourceType.OPEN_COLLECTION, resources.get(0).getType());

      // Check that no example is provided for any operation.
      for (Operation operation : service.getOperations()) {
         List<Exchange> exchanges = assertDoesNotThrow(() -> importer.getMessageDefinitions(service, operation));
         assertEquals(0, exchanges.size());
      }
   }

   @Test
   void testResourceContentIsUntouched() {
      File collection = new File("../samples/PetstoreAPI-opencollection.yml");
      MockRepositoryImporter importer = assertDoesNotThrow(
            () -> MockRepositoryImporterFactory.getMockRepositoryImporter(collection, null));
      Service service = assertDoesNotThrow(importer::getServiceDefinitions).get(0);

      Resource resource = assertDoesNotThrow(() -> importer.getResourceDefinitions(service)).get(0);

      byte[] uploaded = assertDoesNotThrow(() -> Files.readAllBytes(collection.toPath()));
      assertArrayEquals(uploaded, resource.getContent().getBytes(UTF_8));
   }

   @Test
   void testBundledFalseIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-bundled-false.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("not yet supported"));
   }

   @Test
   void testBundledNotBooleanIsRejected() {
      OpenCollectionImporter importer = importerFor("bundled-not-boolean.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("'bundled' property must be a boolean"));
   }

   @Test
   void testUnsupportedSpecVersionIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-spec-2.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("Only OpenCollection 1.x"));
      assertTrue(exception.getMessage().contains("2.0.0"));
   }

   @Test
   void testNumericOpenCollectionPropertyIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-numeric-opencollection.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("'opencollection' property must be a string"));
   }

   @Test
   void testNotAnOpenCollectionIsRejected() {
      OpenCollectionImporter importer = importerFor("not-an-opencollection.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage()
            .contains("Not an OpenCollection document: 'opencollection' root property is missing"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "petstore-no-version.yml", "petstore-blank-version.yml" })
   void testMissingOrBlankVersionIsRejected(String fixture) {
      OpenCollectionImporter importer = importerFor(fixture);

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("Version property"));
   }

   @Test
   void testNumericVersionIsRejected() {
      OpenCollectionImporter importer = importerFor("petstore-numeric-version.yml");

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("must be a string"));
   }

   @ParameterizedTest
   @ValueSource(strings = { "petstore-no-name.yml", "petstore-blank-name.yml" })
   void testMissingOrBlankNameIsRejected(String fixture) {
      OpenCollectionImporter importer = importerFor(fixture);

      MockRepositoryImportException exception = assertThrows(MockRepositoryImportException.class,
            importer::getServiceDefinitions);

      assertTrue(exception.getMessage().contains("Name property"));
   }

   private static OpenCollectionImporter importerFor(String fixture) {
      return assertDoesNotThrow(
            () -> new OpenCollectionImporter("target/test-classes/io/github/microcks/util/opencollection/" + fixture));
   }
}
