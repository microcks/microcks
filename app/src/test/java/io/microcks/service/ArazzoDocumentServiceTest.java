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
package io.microcks.service;

import io.microcks.domain.ArazzoDocument;
import io.microcks.repository.ArazzoDocumentRepository;
import io.microcks.artifact.MockRepositoryImportException;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of the import of an Arazzo document, one per acceptance criterion of the story.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoDocumentServiceTest {

   @Inject
   private ArazzoDocumentService service;

   @Inject
   private ArazzoDocumentRepository repository;

   @Test
   void aSupportedDocumentIsImported() throws Exception {
      service.importArazzoDocument(fixture("pastry.arazzo.yaml"));

      ArazzoDocument imported = repository.findByTitleAndVersion("API Pastry scenarios", "1.0.0");
      assertNotNull(imported);
      assertEquals(List.of("readThenReprice", "listThenReadFirst"), imported.getWorkflowIds());
   }

   @Test
   void anImportedDocumentIsKeptAsUploaded() throws Exception {
      String content = fixture("pastry.arazzo.yaml");

      service.importArazzoDocument(content);

      ArazzoDocument imported = repository.findByTitleAndVersion("API Pastry scenarios", "1.0.0");
      assertNotNull(imported);
      assertEquals(content, imported.getContent());
   }

   @Test
   void aReImportedDocumentIsKeptAsUploadedInItsNewRevision() throws Exception {
      String revision = fixture("pastry-revision.arazzo.yaml");
      service.importArazzoDocument(fixture("pastry.arazzo.yaml"));

      service.importArazzoDocument(revision);

      ArazzoDocument imported = repository.findByTitleAndVersion("API Pastry scenarios", "1.0.0");
      assertNotNull(imported);
      assertEquals(revision, imported.getContent());
   }

   @Test
   void anUnsupportedVersionIsRefusedWithItsReason() throws Exception {
      String content = fixture("unsupported-version.arazzo.yaml");

      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> service.importArazzoDocument(content));

      assertEquals("Unsupported Arazzo version: 2.0.0", refusal.getMessage());
      assertEquals(0, repository.count());
   }

   @Test
   void aFeatureOutsideTheSupportedSubsetIsRefusedWithItsReason() throws Exception {
      String content = fixture("step-on-failure.arazzo.yaml");

      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> service.importArazzoDocument(content));

      assertEquals("Unsupported step field: onFailure", refusal.getMessage());
   }

   @Test
   void aDocumentWithoutTheInfoArazzoRequiresIsRefusedWithItsReason() throws Exception {
      String content = fixture("no-info.arazzo.yaml");

      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> service.importArazzoDocument(content, "uuid_order_workflow.arazzo.yaml"));

      assertEquals("Invalid Arazzo document: info.title is required", refusal.getMessage());
      assertEquals(0, repository.count());
   }

   @Test
   void reImportingTheSameTitleAndVersionUpdatesTheDocument() throws Exception {
      service.importArazzoDocument(fixture("pastry.arazzo.yaml"));

      service.importArazzoDocument(fixture("pastry-revision.arazzo.yaml"));

      assertEquals(List.of("API Pastry scenarios 1.0.0: [readThenReprice, listThenReadFirst, repriceAll]"), repository
            .findAll().stream().map(d -> d.getTitle() + " " + d.getVersion() + ": " + d.getWorkflowIds()).toList());
   }

   @Test
   void aDocumentThatIsNeitherYamlNorJsonIsRefused() throws Exception {
      String content = fixture("unparseable.arazzo.yaml");

      assertThrows(MockRepositoryImportException.class, () -> service.importArazzoDocument(content));
   }

   @ParameterizedTest
   @CsvSource({ "pastry.arazzo.yaml, true", "pastry.openapi.yaml, false", "unparseable.arazzo.yaml, false" })
   void onlyADocumentDeclaringAnArazzoVersionIsAnArazzoDocument(String fileName, boolean arazzoDocument)
         throws Exception {
      assertEquals(arazzoDocument, service.isArazzoDocument(fixture(fileName)));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoDocumentServiceTest.class
            .getResourceAsStream("/io/microcks/service/arazzo/" + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
