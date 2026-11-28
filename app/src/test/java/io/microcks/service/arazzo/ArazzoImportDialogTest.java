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
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoDocumentSummary;
import io.microcks.domain.arazzo.ArazzoImportAnalysis;
import io.microcks.domain.arazzo.ArazzoSourceAnalysis;
import io.microcks.domain.arazzo.ArazzoSourceMatch;
import io.microcks.domain.arazzo.ArazzoMetadata;
import io.microcks.domain.arazzo.ArazzoWorkflowCheck;
import io.microcks.domain.arazzo.ArazzoWorkflowSummary;
import io.microcks.domain.ServiceRef;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.artifact.MockRepositoryImportException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of the import dialog of Arazzo documents, one or more per acceptance criterion of the story. The
 * contracts the source descriptions declare are served by a local HTTP server, so that these tests do not depend on the
 * internet.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoImportDialogTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String DOCUMENTS = "import-dialog.arazzo.yaml";
   private static final String CONTRACTS = "import-dialog.openapi.yaml";

   private static HttpServer contractServer;

   @Inject
   private ArazzoImportService arazzoImportService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoMetadataRepository arazzoMetadataRepository;

   @TempDir
   private Path importedContracts;

   /** Serves the contract example named by the {@code example} query parameter, whatever the path. */
   @BeforeAll
   static void startContractServer() throws IOException {
      contractServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      contractServer.createContext("/", ArazzoImportDialogTest::serveContract);
      contractServer.start();
   }

   @AfterAll
   static void stopContractServer() {
      contractServer.stop(0);
   }

   // AC-01: The dropped document is validated before import

   @Test
   void theDroppedDocumentIsValidatedBeforeImport() throws Exception {
      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(document("place-order"), null);

      assertEquals("Valid Arazzo 1.0.1 · title \"Order fulfilment\" 1.0.0 · 2 workflows · 2 source descriptions",
            validation(analysis));
      assertEquals(0, arazzoDocumentRepository.count());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "no-info | Invalid Arazzo document: info.title is required",
         "unsupported-field | Unsupported workflow field: successActions",
         "not-arazzo | Not an Arazzo document: it declares no arazzo version",
         "unparseable | Arazzo document cannot be parsed as YAML or JSON" })
   void anInvalidOrUnsupportedDocumentShowsItsReasonAndCannotBeImported(String example, String reason)
         throws Exception {
      String content = document(example);

      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(content, null);
      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> arazzoImportService.importArazzoDocument(content, example + ".arazzo.yaml", Map.of()));

      assertEquals("Refused: " + reason, validation(analysis));
      assertEquals(reason, refusal.getMessage());
      assertEquals(0, arazzoDocumentRepository.count());
      assertEquals(0, arazzoMetadataRepository.count());
   }

   // AC-02: A source is matched on the title and version of the contract it declares

   @Test
   void aSourceIsMatchedOnTheTitleAndVersionOfTheContractItDeclares() throws Exception {
      apiIsImported("customer-api");

      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(document("place-order"), null);

      assertEquals("customerApi -> Customer API 1.0.0 [matched Customer API 1.0.0]: 2 of 2 found",
            sources(analysis).get(0));
   }

   @Test
   void theDialogListsTheApisOfMicrocksForEachSource() throws Exception {
      apiIsImported("order-api");
      apiIsImported("customer-api");

      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(document("place-order"), null);

      assertEquals(List.of("Customer API 1.0.0", "Order API 1.0.0"), apis(analysis));
   }

   @ParameterizedTest
   @CsvSource({ "/customer-api.openapi.yaml?example=customer-api", "/specs/order-api.openapi.yaml?example=customer-api",
         "/download?example=customer-api" })
   void theFileNameOfTheDeclaredUrlPlaysNoRoleInTheMatch(String declaredPath) throws Exception {
      apiIsImported("customer-api");
      apiIsImported("order-api");

      ArazzoImportAnalysis analysis = arazzoImportService
            .analyseArazzoDocument(oneSourceDeclaring(contractUrl(declaredPath)), null);

      assertEquals(List.of("customerApi -> Customer API 1.0.0 [matched Customer API 1.0.0]: 2 of 2 found"),
            sources(analysis));
   }

   /** Microcks names an API after the title and version it reads in the contract: a source is matched the same way. */
   @Test
   void theVersionOfTheContractIsReadAsMicrocksReadsItOnImport() throws Exception {
      apiIsImported("loyalty-api");
      String version = serviceRepository.findByNameLike("Loyalty API").get(0).getVersion();

      ArazzoImportAnalysis analysis = arazzoImportService
            .analyseArazzoDocument(oneSourceDeclaring(contractUrl("/loyalty.yaml?example=loyalty-api")), null);

      assertEquals("1.1", version);
      assertEquals("Loyalty API 1.1", linkOf(analysis.sources().get(0)));
   }

   // AC-03: A source whose API is missing says so and stays unlinked

   @Test
   void aSourceWhoseApiIsMissingSaysSoAndStaysUnlinked() throws Exception {
      apiIsImported("customer-api");

      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(document("place-order"), null);

      assertEquals(List.of("customerApi -> Customer API 1.0.0 [matched Customer API 1.0.0]: 2 of 2 found",
            "orderApi -> not linked [no API titled Order API 1.0.0]: 2 steps unresolved"), sources(analysis));
      assertEquals(List.of("searchThenRead: ready to run, all its operations are in [Customer API]",
            "placeOrder: cannot run until [orderApi] linked, []"), checks(analysis));
   }

   /**
    * A relative url cannot be read for an uploaded document, and an absolute one may fail: the source is not matched,
    * and the developer picks its API.
    */
   @ParameterizedTest
   @CsvSource({ "./customer-api.openapi.yaml", "customer-api.openapi.yaml", "CONTRACTS/gone.yaml?example=absent",
         "CONTRACTS/contract.yaml?example=no-info", "CONTRACTS/contract.yaml?example=no-title",
         "CONTRACTS/contract.yaml?example=no-version", "CONTRACTS/contract.yaml?example=unparseable",
         "file:///tmp/customer-api.openapi.yaml", "'http://[customer-api'" })
   void aDeclaredUrlThatCannotBeReadLeavesTheApiToBePicked(String declaredUrl) throws Exception {
      apiIsImported("customer-api");

      ArazzoImportAnalysis analysis = arazzoImportService
            .analyseArazzoDocument(oneSourceDeclaring(declaredUrl.replace("CONTRACTS", contractUrl(""))), null);

      assertEquals(List.of("customerApi -> not linked [unreadable]: 2 steps unresolved"), sources(analysis));
   }

   // AC-04: The developer can change a link before importing

   @Test
   void pickingAnotherApiRecomputesTheStepOperationsAndTheChecks() throws Exception {
      apiIsImported("customer-api");
      Map<String, ServiceRef> links = Map.of("customerApi", api("Customer API", "1.0.0"), "orderApi",
            api("Customer API", "1.0.0"));

      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(document("place-order"), links);

      assertEquals("orderApi -> Customer API 1.0.0 [no API titled Order API 1.0.0]: 0 of 2 found",
            sources(analysis).get(1));
      assertEquals("placeOrder: cannot run until [] linked, [Operation CreateOrder not found in Customer API 1.0.0, "
            + "Operation GetOrder not found in Customer API 1.0.0]", checks(analysis).get(1));
   }

   @Test
   void pickingNotLinkedRecomputesTheStepOperationsAndTheChecks() throws Exception {
      apiIsImported("customer-api");
      apiIsImported("order-api");
      Map<String, ServiceRef> links = new HashMap<>();
      links.put("customerApi", null);
      links.put("orderApi", api("Order API", "1.0.0"));

      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(document("place-order"), links);

      assertEquals(List.of("customerApi -> not linked [matched Customer API 1.0.0]: 2 steps unresolved",
            "orderApi -> Order API 1.0.0 [matched Order API 1.0.0]: 2 of 2 found"), sources(analysis));
      assertEquals(List.of("searchThenRead: cannot run until [customerApi] linked, []",
            "placeOrder: ready to run, all its operations are in [Order API]"), checks(analysis));
   }

   // AC-05: Import stores the document and its links, all or nothing

   @Test
   void importStoresTheDocumentAndItsLinksReplacingAnExistingOverlay() throws Exception {
      apiIsImported("customer-api");
      arazzoDocumentService.importArazzoMetadata(fixture("order-fulfilment.arazzo-metadata.yaml"));

      ArazzoDocumentSummary summary = arazzoImportService.importArazzoDocument(document("place-order"),
            "place-order.arazzo.yaml", Map.of("customerApi", api("Customer API", "1.0.0")));

      ArazzoDocument imported = arazzoDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0");
      assertEquals("place-order.arazzo.yaml", imported.getFileName());
      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0}"),
            arazzoMetadataRepository.listAll().stream().map(ArazzoImportDialogTest::describe).toList());
      assertEquals(
            List.of("searchThenRead: runnable", "placeOrder: not runnable [no API name and version for this source]"),
            workflowsOnTheWorkflowsPage());
      assertEquals(arazzoDocumentService.getDocumentSummaries(null), List.of(summary));
   }

   @Test
   void anImportRefusedForOneOfItsLinksStoresNothing() throws Exception {
      arazzoDocumentService.importArazzoMetadata(fixture("order-fulfilment.arazzo-metadata.yaml"));

      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> arazzoImportService.importArazzoDocument(document("place-order"), "place-order.arazzo.yaml",
                  Map.of("paymentApi", api("Payment API", "1.0.0"))));

      assertEquals("Unknown source description: paymentApi", refusal.getMessage());
      assertNull(arazzoDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0"));
      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0, orderApi=Order API 1.0.0}"),
            arazzoMetadataRepository.listAll().stream().map(ArazzoImportDialogTest::describe).toList());
   }

   private static String validation(ArazzoImportAnalysis analysis) {
      if (analysis.refusal() != null) {
         return "Refused: " + analysis.refusal();
      }
      return "Valid Arazzo " + analysis.arazzoVersion() + " · title \"" + analysis.title() + "\" " + analysis.version()
            + " · " + analysis.workflowIds().size() + " workflows · " + analysis.sources().size()
            + " source descriptions";
   }

   private static List<String> sources(ArazzoImportAnalysis analysis) {
      return analysis.sources().stream().map(ArazzoImportDialogTest::describe).toList();
   }

   private static String describe(ArazzoSourceAnalysis source) {
      ArazzoSourceMatch match = source.match();
      String matched = switch (match.status()) {
         case MATCHED -> "matched " + match.contractTitle() + " " + match.contractVersion();
         case NO_API -> "no API titled " + match.contractTitle() + " " + match.contractVersion();
         case UNREADABLE -> "unreadable";
      };
      String operations = source.link().linked()
            ? source.foundOperationCount() + " of " + source.operationCount() + " found"
            : source.unresolvedStepCount() + " steps unresolved";
      return source.sourceName() + " -> " + linkOf(source) + " [" + matched + "]: " + operations;
   }

   private static String linkOf(ArazzoSourceAnalysis source) {
      return source.link().linked() ? source.link().apiName() + " " + source.link().apiVersion() : "not linked";
   }

   private static List<String> checks(ArazzoImportAnalysis analysis) {
      return analysis.checks().stream().map(ArazzoImportDialogTest::describe).toList();
   }

   private static String describe(ArazzoWorkflowCheck check) {
      if (check.runnable()) {
         return check.workflowId() + ": ready to run, all its operations are in " + check.apiNames();
      }
      return check.workflowId() + ": cannot run until " + check.unlinkedSources() + " linked, " + check.reasons();
   }

   private static List<String> apis(ArazzoImportAnalysis analysis) {
      return analysis.apis().stream().map(api -> api.getName() + " " + api.getVersion()).toList();
   }

   private static String describe(ArazzoMetadata overlay) {
      Map<String, String> sources = new TreeMap<>();
      overlay.getSources().forEach((name, api) -> sources.put(name, api.getName() + " " + api.getVersion()));
      return overlay.getName() + " " + overlay.getVersion() + ": " + sources;
   }

   private List<String> workflowsOnTheWorkflowsPage() {
      return arazzoDocumentService.getDocumentSummaries(null).get(0).workflows().stream()
            .map(ArazzoWorkflowSummary::status).map(status -> status.workflowId() + ": "
                  + (status.runnable() ? "runnable" : "not runnable " + status.reasons()))
            .toList();
   }

   private static ServiceRef api(String name, String version) {
      return new ServiceRef(null, name, version);
   }

   private void apiIsImported(String contractExample) throws Exception {
      Path contract = importedContracts.resolve(contractExample + ".openapi.yaml");
      Files.writeString(contract, exampleOf(CONTRACTS, contractExample));
      serviceService.importServiceDefinition(contract.toFile(), null,
            new ArtifactInfo(contract.getFileName().toString(), true));
   }

   private static String document(String example) throws IOException {
      return exampleOf(DOCUMENTS, example).replace("CONTRACTS", contractUrl(""));
   }

   private static String oneSourceDeclaring(String declaredUrl) throws IOException {
      return exampleOf(DOCUMENTS, "one-source").replace("DECLARED_URL", declaredUrl);
   }

   private static String contractUrl(String pathAndQuery) {
      return "http://localhost:" + contractServer.getAddress().getPort() + pathAndQuery;
   }

   private static void serveContract(HttpExchange exchange) throws IOException {
      String query = exchange.getRequestURI().getQuery();
      String example = query == null ? "" : query.substring(query.indexOf("example=") + "example=".length());
      byte[] content;
      try {
         content = exampleOf(CONTRACTS, example).getBytes(StandardCharsets.UTF_8);
      } catch (IllegalArgumentException iae) {
         exchange.sendResponseHeaders(404, -1);
         exchange.close();
         return;
      }
      exchange.sendResponseHeaders(200, content.length);
      try (OutputStream out = exchange.getResponseBody()) {
         out.write(content);
      }
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoImportDialogTest.class.getResourceAsStream(FIXTURES + fileName)) {
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
