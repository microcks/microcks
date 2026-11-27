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
package io.microcks.service.arazzo;

import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;
import io.microcks.domain.arazzo.ArazzoSourceLink;
import io.microcks.domain.arazzo.ArazzoSources;
import io.microcks.domain.arazzo.ArazzoWorkflowStatus;
import io.microcks.domain.Resource;
import io.microcks.repository.arazzo.ArazzoSourcesRepository;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.artifact.MockRepositoryImportException;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of the link between the source descriptions of an Arazzo document and the APIs of Microcks, one per
 * acceptance criterion of the story.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSourceLinkTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private ResourceRepository resourceRepository;

   @Inject
   private ArazzoSourcesRepository arazzoSourcesRepository;

   @Test
   void theOverlayLinksASourceToTheApiWithThatNameAndVersion() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");

      arazzoDocumentService.importArazzoDocument(fixture("pastry.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));

      assertEquals(List.of("pastryApi -> API Pastry - 2.0 2.0.0: linked"),
            sourceLinks("API Pastry scenarios", "1.0.0"));
      assertEquals(List.of("readThenReprice: runnable", "listThenReadFirst: runnable"),
            workflowStatuses("API Pastry scenarios", "1.0.0"));
   }

   @Test
   void aSourceMappedToAnApiAbsentFromMicrocksStaysUnlinked() throws Exception {
      apiIsImported("customer-api.openapi.yaml");

      arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(fixture("order-fulfilment.arazzo-sources.yaml"));

      assertEquals(List.of("customerApi -> Customer API 1.0.0: linked", "orderApi -> Order API 1.0.0: not linked"),
            sourceLinks("Order fulfilment", "1.0.0"));
      assertEquals(List.of("searchThenRead: runnable", "placeOrder: not runnable [API Order API 1.0.0 not found]"),
            workflowStatuses("Order fulfilment", "1.0.0"));
   }

   @Test
   void withoutOverlaySourcesAreNotLinked() throws Exception {
      arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));

      assertEquals(List.of("customerApi: not linked", "orderApi: not linked"),
            sourceLinks("Order fulfilment", "1.0.0"));
      assertEquals(
            List.of("searchThenRead: not runnable [no API name and version for this source]",
                  "placeOrder: not runnable [no API name and version for this source]"),
            workflowStatuses("Order fulfilment", "1.0.0"));
   }

   @Test
   void aStepCallingAnOperationTheLinkedApiDoesNotDefineIsReported() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));

      arazzoDocumentService.importArazzoDocument(fixture("pastry-delete.arazzo.yaml"));

      assertEquals(List.of("pastryApi -> API Pastry - 2.0 2.0.0: linked"),
            sourceLinks("API Pastry scenarios", "1.0.0"));
      assertEquals(
            List.of("readThenReprice: not runnable [Operation DeletePastry not found in API Pastry - 2.0 2.0.0]"),
            workflowStatuses("API Pastry scenarios", "1.0.0"));
   }

   @Test
   void importingTheMissingApiLaterLinksTheSource() throws Exception {
      apiIsImported("customer-api.openapi.yaml");
      arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(fixture("order-fulfilment.arazzo-sources.yaml"));

      apiIsImported("order-api.openapi.yaml");

      assertEquals(List.of("customerApi -> Customer API 1.0.0: linked", "orderApi -> Order API 1.0.0: linked"),
            sourceLinks("Order fulfilment", "1.0.0"));
      assertEquals(List.of("searchThenRead: runnable", "placeOrder: runnable"),
            workflowStatuses("Order fulfilment", "1.0.0"));
   }

   @Test
   void everyReasonIsGivenOnceInTheOrderOfTheSteps() throws Exception {
      arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(fixture("order-fulfilment.arazzo-sources.yaml"));

      assertEquals(
            List.of("searchThenRead: not runnable [API Customer API 1.0.0 not found]",
                  "placeOrder: not runnable [API Customer API 1.0.0 not found, API Order API 1.0.0 not found]"),
            workflowStatuses("Order fulfilment", "1.0.0"));
   }

   @Test
   void anApiImportedFromAnotherFormatDefinesNoOperationAStepCanCall() throws Exception {
      serviceService.createGenericResourceService("API Pastry - 2.0", "2.0.0", "pastry", null);
      arazzoDocumentService.importArazzoDocument(fixture("pastry.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));

      assertEquals(
            List.of(
                  "readThenReprice: not runnable [Operation GetPastryByName not found in API Pastry - 2.0 2.0.0, "
                        + "Operation PatchPastry not found in API Pastry - 2.0 2.0.0]",
                  "listThenReadFirst: not runnable [Operation GetPastries not found in API Pastry - 2.0 2.0.0, "
                        + "Operation GetPastryByName not found in API Pastry - 2.0 2.0.0]"),
            workflowStatuses("API Pastry scenarios", "1.0.0"));
   }

   @Test
   void aStoredContractThatCannotBeReadDefinesNoOperation() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      for (Resource contract : resourceRepository
            .findMainByServiceId(serviceRepository.findByNameAndVersion("API Pastry - 2.0", "2.0.0").getId())) {
         contract.setContent("paths: [ GetPastries");
         resourceRepository.persistOrUpdate(contract);
      }
      arazzoDocumentService.importArazzoDocument(fixture("pastry.arazzo.yaml"));
      arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));

      assertEquals(
            List.of(
                  "readThenReprice: not runnable [Operation GetPastryByName not found in API Pastry - 2.0 2.0.0, "
                        + "Operation PatchPastry not found in API Pastry - 2.0 2.0.0]",
                  "listThenReadFirst: not runnable [Operation GetPastries not found in API Pastry - 2.0 2.0.0, "
                        + "Operation GetPastryByName not found in API Pastry - 2.0 2.0.0]"),
            workflowStatuses("API Pastry scenarios", "1.0.0"));
   }

   @Test
   void reImportingAnOverlayWithTheSameNameAndVersionReplacesIt() throws Exception {
      arazzoDocumentService.importArazzoSources(fixture("pastry-previous.arazzo-sources.yaml"));

      ArazzoSources imported = arazzoDocumentService.importArazzoSources(fixture("pastry.arazzo-sources.yaml"));

      assertEquals("API Pastry scenarios 1.0.0: {pastryApi=API Pastry - 2.0 2.0.0}", describe(imported));
      assertEquals(List.of("API Pastry scenarios 1.0.0: {pastryApi=API Pastry - 2.0 2.0.0}"),
            arazzoSourcesRepository.listAll().stream().map(ArazzoSourceLinkTest::describe).toList());
   }

   /**
    * An overlay is matched to its Arazzo document by its metadata name and version: without them, it links nothing and
    * is refused rather than kept.
    */
   @ParameterizedTest
   @CsvSource({ "no-metadata, Invalid ArazzoSources overlay: metadata.name is required",
         "no-metadata-name, Invalid ArazzoSources overlay: metadata.name is required",
         "no-metadata-version, Invalid ArazzoSources overlay: metadata.version is required" })
   void anOverlayThatNamesNoDocumentIsRefusedWithItsReason(String example, String reason) throws Exception {
      String content = exampleOf("invalid.arazzo-sources.yaml", example);

      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> arazzoDocumentService.importArazzoSources(content));

      assertEquals(reason, refusal.getMessage());
      assertEquals(0, arazzoSourcesRepository.count());
   }

   @ParameterizedTest
   @CsvSource({ "pastry.arazzo-sources.yaml, true", "pastry.arazzo.yaml, false", "unparseable.arazzo.yaml, false" })
   void onlyAnArtifactOfKindArazzoSourcesIsAnOverlay(String fileName, boolean overlay) throws Exception {
      assertEquals(overlay, arazzoDocumentService.isArazzoSources(fixture(fileName)));
   }

   private List<String> sourceLinks(String title, String version) {
      return arazzoDocumentService.getSourceLinks(title, version).stream().map(ArazzoSourceLinkTest::describe).toList();
   }

   private List<String> workflowStatuses(String title, String version) {
      return arazzoDocumentService.getWorkflowStatuses(title, version).stream().map(ArazzoSourceLinkTest::describe)
            .toList();
   }

   private static String describe(ArazzoSourceLink link) {
      String api = link.apiName() == null ? "" : " -> " + link.apiName() + " " + link.apiVersion();
      return link.sourceName() + api + ": " + (link.linked() ? "linked" : "not linked");
   }

   private static String describe(ArazzoSources overlay) {
      Map<String, String> sources = new TreeMap<>();
      overlay.getSources().forEach((name, api) -> sources.put(name, api.getName() + " " + api.getVersion()));
      return overlay.getName() + " " + overlay.getVersion() + ": " + sources;
   }

   private static String describe(ArazzoWorkflowStatus status) {
      return status.workflowId() + ": " + (status.runnable() ? "runnable" : "not runnable " + status.reasons());
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoSourceLinkTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoSourceLinkTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }

   /**
    * @param fileName The fixture of a category, holding one YAML document per example, each announced by a
    *                 {@code # example: <name> - <what>} comment
    * @param example  The name of the example
    * @return The YAML document of that example
    */
   private static String exampleOf(String fileName, String example) throws IOException {
      String marker = "# example: " + example + " ";
      return Arrays.stream(fixture(fileName).split("\n---\n")).filter(document -> document.contains(marker)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fileName));
   }
}
