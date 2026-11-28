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
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.service.arazzo.view.ArazzoDocumentSummary;
import io.microcks.service.arazzo.view.ArazzoSourceLink;
import io.microcks.service.arazzo.view.ArazzoWorkflowStatus;
import io.microcks.repository.workflow.WorkflowDocumentRepository;
import io.microcks.repository.ServiceRepository;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Acceptance tests of the list of Arazzo documents the Workflows page shows, one per acceptance criterion of the story.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoDocumentSummaryTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private WorkflowDocumentRepository workflowDocumentRepository;

   @Test
   void documentsAreListedWithTheirWorkflowsApisAndLastResult() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      Date beforeImport = new Date();
      arazzoDocumentService.importArazzoDocument(fixture("pastry.arazzo.yaml"), "pastry.arazzo.yaml");
      Date afterImport = new Date();
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));

      List<ArazzoDocumentSummary> documents = arazzoDocumentService.getDocumentSummaries(null);

      assertEquals(List.of("API Pastry scenarios 1.0.0 (pastry.arazzo.yaml, ARAZZO 1.0.1)"),
            documents.stream().map(ArazzoDocumentSummaryTest::describe).toList());
      assertEquals(List.of("pastryApi -> REST API Pastry - 2.0 2.0.0: linked"), sources(documents.get(0)));
      assertEquals(serviceRepository.findByNameAndVersion("API Pastry - 2.0", "2.0.0").getId(),
            documents.get(0).sources().get(0).apiId());
      assertEquals(List.of(
            "readThenReprice: 'Reads a pastry, then changes its price using the name returned by the first call.' "
                  + "2 steps, runnable, NEVER_RUN",
            "listThenReadFirst: 'Reads the first pastry of the list.' 2 steps, runnable, NEVER_RUN"),
            workflows(documents.get(0)));
      Date importDate = documents.get(0).importDate();
      assertFalse(importDate.before(beforeImport) || importDate.after(afterImport),
            "imported on " + importDate + ", not between " + beforeImport + " and " + afterImport);
   }

   @Test
   void aDocumentWithAnUnlinkedSourceSaysSo() throws Exception {
      apiIsImported("customer-api.openapi.yaml");
      arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"), "place-order.arazzo.yaml");
      arazzoDocumentService.importArazzoMetadata(fixture("order-fulfilment.arazzo-metadata.yaml"));

      List<ArazzoDocumentSummary> documents = arazzoDocumentService.getDocumentSummaries(null);

      assertEquals(List.of("Order fulfilment 1.0.0 (place-order.arazzo.yaml, ARAZZO 1.0.1)"),
            documents.stream().map(ArazzoDocumentSummaryTest::describe).toList());
      assertEquals(List.of("customerApi -> REST Customer API 1.0.0: linked", "orderApi -> Order API 1.0.0: not linked"),
            sources(documents.get(0)));
      assertEquals(List.of(
            "searchThenRead: 'Searches customers by name, then reads the first one found.' 2 steps, runnable, "
                  + "NEVER_RUN",
            "placeOrder: 'Reads a customer, then places an order for this customer.' 2 steps, "
                  + "not runnable [API Order API 1.0.0 not found], NEVER_RUN"),
            workflows(documents.get(0)));
   }

   @Test
   void aDocumentImportedWithoutItsFileHasNoFileName() throws Exception {
      WorkflowDocument imported = arazzoDocumentService.importArazzoDocument(fixture("pastry.arazzo.yaml"));

      assertEquals("API Pastry scenarios 1.0.0 (null)",
            imported.getTitle() + " " + imported.getVersion() + " (" + imported.getFileName() + ")");
   }

   @Test
   void aDocumentStoredBeforeTheWorkflowsPageIsListedWithoutWhatWasNotStored() throws Exception {
      arazzoDocumentService.importArazzoDocument(fixture("pastry.arazzo.yaml"), "pastry.arazzo.yaml");
      WorkflowDocument storedBefore = workflowDocumentRepository.findByTitleAndVersion("API Pastry scenarios", "1.0.0");
      storedBefore.setFormatVersion(null);
      storedBefore.setFileName(null);
      storedBefore.setImportDate(null);
      storedBefore.setWorkflowDescriptions(null);
      workflowDocumentRepository.persistOrUpdate(storedBefore);

      List<ArazzoDocumentSummary> documents = arazzoDocumentService.getDocumentSummaries(null);

      assertEquals(List.of("API Pastry scenarios 1.0.0 (null, ARAZZO null)"),
            documents.stream().map(ArazzoDocumentSummaryTest::describe).toList());
      assertNull(documents.get(0).importDate());
      assertEquals(List.of(
            "readThenReprice: 'null' 2 steps, not runnable [no API name and version for this source], NEVER_RUN",
            "listThenReadFirst: 'null' 2 steps, not runnable [no API name and version for this source], NEVER_RUN"),
            workflows(documents.get(0)));
   }

   @Test
   void noDocumentIsListedWhenNoneIsImported() {
      assertEquals(List.of(), arazzoDocumentService.getDocumentSummaries(null));
   }

   @ParameterizedTest
   @CsvSource(value = { "API Pastry - 2.0 | API Pastry scenarios 1.0.0", "Customer API | Order fulfilment 1.0.0",
         "Order API | Order fulfilment 1.0.0", "Unknown API | ''",
         "| API Pastry scenarios 1.0.0, Order fulfilment 1.0.0" }, delimiter = '|')
   void theListCanBeFilteredByApi(String apiName, String listed) throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      apiIsImported("customer-api.openapi.yaml");
      arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));
      arazzoDocumentService.importArazzoMetadata(fixture("order-fulfilment.arazzo-metadata.yaml"));
      arazzoDocumentService.importArazzoDocument(fixture("pastry.arazzo.yaml"));
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));

      List<ArazzoDocumentSummary> documents = arazzoDocumentService.getDocumentSummaries(apiName);

      assertEquals(listed, String.join(", ",
            documents.stream().map(document -> document.title() + " " + document.version()).toList()));
   }

   private static String describe(ArazzoDocumentSummary document) {
      return document.title() + " " + document.version() + " (" + document.fileName() + ", " + document.format() + " "
            + document.formatVersion() + ")";
   }

   private static List<String> sources(ArazzoDocumentSummary document) {
      return document.sources().stream().map(ArazzoDocumentSummaryTest::describe).toList();
   }

   private static String describe(ArazzoDocumentSummary.Source source) {
      ArazzoSourceLink link = source.link();
      String type = source.apiType() == null ? "" : source.apiType() + " ";
      String api = link.apiName() == null ? "" : " -> " + type + link.apiName() + " " + link.apiVersion();
      return link.sourceName() + api + ": " + (link.linked() ? "linked" : "not linked");
   }

   private static List<String> workflows(ArazzoDocumentSummary document) {
      return document.workflows().stream().map(ArazzoDocumentSummaryTest::describe).toList();
   }

   private static String describe(ArazzoDocumentSummary.Workflow workflow) {
      ArazzoWorkflowStatus status = workflow.status();
      return status.workflowId() + ": '" + workflow.description() + "' " + workflow.stepCount() + " steps, "
            + (status.runnable() ? "runnable" : "not runnable " + status.reasons()) + ", " + workflow.lastResult();
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoDocumentSummaryTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoDocumentSummaryTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
