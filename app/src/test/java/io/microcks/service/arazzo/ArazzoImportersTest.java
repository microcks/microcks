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
import io.microcks.service.JobService;
import io.microcks.service.ServiceService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.domain.workflow.WorkflowDocumentRef;
import io.microcks.service.arazzo.view.ArazzoDocumentSummary;
import io.microcks.service.arazzo.view.ArazzoImportAnalysis;
import io.microcks.domain.workflow.WorkflowMetadata;
import io.microcks.domain.workflow.WorkflowExample;
import io.microcks.domain.ImportJob;
import io.microcks.domain.Secret;
import io.microcks.domain.SecretRef;
import io.microcks.domain.ServiceRef;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.repository.ImportJobRepository;
import io.microcks.repository.SecretRepository;
import io.microcks.artifact.MockRepositoryImportException;
import io.microcks.util.ReferenceResolver;
import io.microcks.util.SimpleReferenceURLBuilder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of the import of Arazzo documents from a URL in Importers, and of the links of their sources made
 * again later from the Workflows pages, one or more per acceptance criterion of the story. The repository the jobs
 * import from is served by a local HTTP server, so that these tests do not depend on the internet.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoImportersTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String ARAZZO = "importers.arazzo.yaml";
   private static final String CONTRACTS = "importers.openapi.yaml";
   private static final String DOCUMENT = "journeys/place-order.arazzo.yaml";
   private static final String OVERLAY = "journeys/order-fulfilment.arazzo-metadata.yaml";
   private static final String EXAMPLES = "journeys/order-fulfilment.arazzo-examples.yaml";
   private static final String PRIVATE = "private/";
   private static final String TOKEN = "repository-token";

   /** The example served at each path of the repository: by default, the example named after that path. */
   private static final Map<String, String> repository = new ConcurrentHashMap<>();
   /** The content served at a path of the repository, in place of an example. */
   private static final Map<String, String> contents = new ConcurrentHashMap<>();

   private static HttpServer repositoryServer;

   @Inject
   private JobService jobService;

   @Inject
   private ArazzoImportService arazzoImportService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ImportJobRepository jobRepository;

   @Inject
   private SecretRepository secretRepository;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoMetadataRepository arazzoMetadataRepository;

   @TempDir
   private Path importedContracts;

   /**
    * Serves the repository at /repo/: the example of each path, under /repo/private/ only to the requests that give the
    * token of the repository.
    */
   @BeforeAll
   static void startRepositoryServer() throws IOException {
      repositoryServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      repositoryServer.createContext("/repo/", ArazzoImportersTest::serveRepository);
      repositoryServer.start();
   }

   @AfterAll
   static void stopRepositoryServer() {
      repositoryServer.stop(0);
   }

   @BeforeEach
   void resetRepository() {
      repository.clear();
      contents.clear();
   }

   // AC-01: An Importers job whose URL points to an Arazzo document imports it, and re-imports it on refresh

   @Test
   void anImportersJobWhoseUrlPointsToAnArazzoDocumentImportsIt() throws Exception {
      ImportJob job = job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true);

      jobService.doImportJob(job);

      WorkflowDocument document = arazzoDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0");
      assertEquals("Order fulfilment 1.0.0 from place-order.arazzo.yaml: [searchThenRead, placeOrder]",
            describe(document));
      ImportJob imported = jobRepository.findByIdOptional(job.getId()).orElseThrow();
      assertNull(imported.getLastImportError());
      assertNotNull(imported.getLastImportDate());
      assertNull(imported.getServiceRefs());
      assertEquals(
            new WorkflowDocumentRef(document.getId(), "Order fulfilment", "1.0.0", List.of("customerApi", "orderApi")),
            imported.getArazzoDocumentRef());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "unsupported-field | Unsupported workflow field: successActions",
         "no-info | Invalid Arazzo document: info.title is required" })
   void aRefusedArazzoDocumentStoresNothingAndTheJobTellsWhy(String example, String reason) throws Exception {
      repository.put(DOCUMENT, example);
      ImportJob job = job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true);

      jobService.doImportJob(job);

      ImportJob imported = jobRepository.findByIdOptional(job.getId()).orElseThrow();
      assertEquals(reason, imported.getLastImportError());
      assertNull(imported.getArazzoDocumentRef());
      assertEquals(0, arazzoDocumentRepository.count());
      assertEquals(0, arazzoMetadataRepository.count());
   }

   @Test
   void refreshingTheJobReimportsTheDocument() throws Exception {
      ImportJob job = job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true);
      jobService.doImportJob(job);
      String documentId = arazzoDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0").getId();

      repository.put(DOCUMENT, "place-order-revised");
      jobService.doImportJob(job);

      WorkflowDocument document = arazzoDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0");
      assertEquals(documentId, document.getId());
      assertEquals("Order fulfilment 1.0.0 from place-order.arazzo.yaml: [searchThenRead, placeOrder, trackOrder]",
            describe(document));
      assertEquals(1, arazzoDocumentRepository.count());
   }

   @Test
   void aRefusedRefreshKeepsTheDocumentImportedBefore() throws Exception {
      ImportJob job = job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true);
      jobService.doImportJob(job);

      repository.put(DOCUMENT, "unsupported-field");
      jobService.doImportJob(job);

      ImportJob refreshed = jobRepository.findByIdOptional(job.getId()).orElseThrow();
      assertEquals("Unsupported workflow field: successActions", refreshed.getLastImportError());
      assertNull(refreshed.getArazzoDocumentRef());
      assertEquals("Order fulfilment 1.0.0 from place-order.arazzo.yaml: [searchThenRead, placeOrder]",
            describe(arazzoDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0")));
   }

   @Test
   void anOverlayUrlAsASecondaryArtifactJobLinksTheSourcesOfItsDocument() throws Exception {
      jobService.doImportJob(job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true));
      apiIsImported("contracts/customers.yaml");
      apiIsImported("journeys/order-api.openapi.yaml");
      ImportJob overlayJob = job("Order fulfilment links", repositoryUrl(OVERLAY), false);

      jobService.doImportJob(overlayJob);

      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0, orderApi=Order API 1.0.0}"),
            overlays());
      ImportJob imported = jobRepository.findByIdOptional(overlayJob.getId()).orElseThrow();
      assertNull(imported.getLastImportError());
      assertEquals(new WorkflowDocumentRef(documentId(), "Order fulfilment", "1.0.0", List.of()),
            imported.getArazzoDocumentRef());
      assertEquals(List.of("searchThenRead: runnable", "placeOrder: runnable"), workflowsOnTheWorkflowsPage());
   }

   @Test
   void anExamplesUrlAsASecondaryArtifactJobImportsTheExamplesOfItsDocument() throws Exception {
      jobService.doImportJob(job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true));
      ImportJob examplesJob = job("Order fulfilment examples", repositoryUrl(EXAMPLES), false);

      jobService.doImportJob(examplesJob);

      ImportJob imported = jobRepository.findByIdOptional(examplesJob.getId()).orElseThrow();
      assertNull(imported.getLastImportError());
      assertEquals(new WorkflowDocumentRef(documentId(), "Order fulfilment", "1.0.0", List.of("customerApi",
            "orderApi")),
            imported.getArazzoDocumentRef());
      assertEquals(List.of("ada-found"), arazzoDocumentService
            .getExamples(arazzoDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0"), "searchThenRead")
            .stream().map(WorkflowExample::name).toList());
   }

   @Test
   void anExamplesJobImportedBeforeItsDocumentFailsAndTellsToImportTheDocumentFirst() throws Exception {
      ImportJob examplesJob = job("Order fulfilment examples", repositoryUrl(EXAMPLES), false);

      jobService.doImportJob(examplesJob);

      ImportJob refused = jobRepository.findByIdOptional(examplesJob.getId()).orElseThrow();
      assertEquals("Import the Arazzo document Order fulfilment 1.0.0 before its examples",
            refused.getLastImportError());
      assertNull(refused.getArazzoDocumentRef());
   }

   @Test
   void anOverlayJobImportedBeforeItsDocumentNamesTheDocumentToCome() throws Exception {
      ImportJob overlayJob = job("Order fulfilment links", repositoryUrl(OVERLAY), false);

      jobService.doImportJob(overlayJob);

      assertEquals(new WorkflowDocumentRef(null, "Order fulfilment", "1.0.0", List.of()),
            jobRepository.findByIdOptional(overlayJob.getId()).orElseThrow().getArazzoDocumentRef());
      assertEquals(1, arazzoMetadataRepository.count());
   }

   @Test
   void aJobWhoseUrlPointsToAnApiContractStillImportsTheApi() throws Exception {
      ImportJob job = job("Customer API", repositoryUrl("contracts/customers.yaml"), true);

      jobService.doImportJob(job);

      ImportJob imported = jobRepository.findByIdOptional(job.getId()).orElseThrow();
      assertNull(imported.getLastImportError());
      assertEquals(List.of("Customer API 1.0.0"),
            imported.getServiceRefs().stream().map(api -> api.getName() + " " + api.getVersion()).toList());
      assertNull(imported.getArazzoDocumentRef());
      assertEquals(0, arazzoDocumentRepository.count());
   }

   @Test
   void aJobWhoseUrlCannotBeDownloadedTellsWhy() throws Exception {
      ImportJob job = job("Gone", repositoryUrl("journeys/gone.arazzo.yaml"), true);

      jobService.doImportJob(job);

      assertEquals(repositoryUrl("journeys/gone.arazzo.yaml") + " cannot be downloaded",
            jobRepository.findByIdOptional(job.getId()).orElseThrow().getLastImportError());
   }

   @Test
   void aJobWhosePathIsNoFileTellsWhy() throws Exception {
      ImportJob job = job("Gone", importedContracts.resolve("gone.arazzo.yaml").toString(), true);

      jobService.doImportJob(job);

      assertNotNull(jobRepository.findByIdOptional(job.getId()).orElseThrow().getLastImportError());
      assertEquals(0, arazzoDocumentRepository.count());
   }

   // AC-02: Relative source urls are resolved against the document's URL, then matched on the title and version

   @Test
   void relativeSourceUrlsAreResolvedAgainstTheDocumentUrlAndMatchedOnTitleAndVersion() throws Exception {
      apiIsImported("contracts/customers.yaml");
      ImportJob job = job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true);

      jobService.doImportJob(job);

      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0}"), overlays());
      assertEquals(List.of("orderApi"),
            jobRepository.findByIdOptional(job.getId()).orElseThrow().getArazzoDocumentRef().unlinkedSources());
      assertEquals(
            List.of("searchThenRead: runnable", "placeOrder: not runnable [no API name and version for this source]"),
            workflowsOnTheWorkflowsPage());
   }

   @Test
   void noOverlayIsWrittenWhenNoSourceIsMatchedSoThatARefreshLinksThemLater() throws Exception {
      ImportJob job = job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true);
      jobService.doImportJob(job);
      long overlaysBefore = arazzoMetadataRepository.count();

      apiIsImported("contracts/customers.yaml");
      jobService.doImportJob(job);

      assertEquals(0, overlaysBefore);
      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0}"), overlays());
   }

   @Test
   void aJobImportNeverOverwritesAnExistingOverlay() throws Exception {
      apiIsImported("contracts/customers.yaml");
      apiIsImported("journeys/order-api.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(exampleOf(ARAZZO, "crossed-links"));
      ImportJob job = job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true);

      jobService.doImportJob(job);

      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0, orderApi=Customer API 1.0.0}"),
            overlays());
      assertEquals(List.of(),
            jobRepository.findByIdOptional(job.getId()).orElseThrow().getArazzoDocumentRef().unlinkedSources());
   }

   @Test
   void theSourcesNextToADocumentAreReadWithTheSecretOfItsJob() throws Exception {
      apiIsImported("contracts/customers.yaml");
      ImportJob job = job("Private journeys", repositoryUrl(PRIVATE + DOCUMENT), true);
      job.setSecretRef(secretWithToken());

      jobService.doImportJob(job);

      assertNull(jobRepository.findByIdOptional(job.getId()).orElseThrow().getLastImportError());
      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0}"), overlays());
   }

   /**
    * A source whose contract cannot be read is reported as linked to no API, never skipped. An absolute url is read
    * without the secret of the job, that is only given to the repository of the document.
    */
   @ParameterizedTest
   @CsvSource({ "./gone.yaml", "../journeys/no-info.yaml", "REPOSITORY/contracts/customers.yaml",
         "'http://[customer-api'" })
   void aSourceWhoseContractCannotBeReadIsReportedLinkedToNoApi(String declaredUrl) throws Exception {
      apiIsImported("contracts/customers.yaml");
      contents.put(DOCUMENT, exampleOf(ARAZZO, "one-source").replace("DECLARED_URL",
            declaredUrl.replace("REPOSITORY/", repositoryUrl(PRIVATE))));
      ImportJob job = job("Private journeys", repositoryUrl(PRIVATE + DOCUMENT), true);
      job.setSecretRef(secretWithToken());

      jobService.doImportJob(job);

      ImportJob imported = jobRepository.findByIdOptional(job.getId()).orElseThrow();
      assertNull(imported.getLastImportError());
      assertEquals(List.of("customerApi"), imported.getArazzoDocumentRef().unlinkedSources());
      assertEquals(0, arazzoMetadataRepository.count());
   }

   @Test
   void theContractsReadInTheRepositoryAreDeletedOnceMatched() throws Exception {
      ReferenceResolver repositoryOfTheDocument = new ReferenceResolver(repositoryUrl(DOCUMENT), null, true,
            new SimpleReferenceURLBuilder());

      arazzoImportService.importArazzoDocument(exampleOf(ARAZZO, DOCUMENT), "place-order.arazzo.yaml",
            repositoryOfTheDocument);

      assertEquals(Map.of(), repositoryOfTheDocument.getResolvedReferences());
   }

   @Test
   void aSuccessfulRefreshClearsTheErrorOfTheImportBefore() throws Exception {
      repository.put(DOCUMENT, "unsupported-field");
      ImportJob job = job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true);
      jobService.doImportJob(job);

      repository.remove(DOCUMENT);
      jobService.doImportJob(job);

      ImportJob refreshed = jobRepository.findByIdOptional(job.getId()).orElseThrow();
      assertNull(refreshed.getLastImportError());
      assertNotNull(refreshed.getArazzoDocumentRef());
   }

   @Test
   void aJobWhoseUrlNowServesAnArazzoDocumentNoLongerListsServices() throws Exception {
      ImportJob job = job("Order fulfilment", repositoryUrl("contracts/customers.yaml"), true);
      jobService.doImportJob(job);

      repository.put("contracts/customers.yaml", DOCUMENT);
      jobService.doImportJob(job);

      ImportJob refreshed = jobRepository.findByIdOptional(job.getId()).orElseThrow();
      assertNull(refreshed.getServiceRefs());
      assertEquals("Order fulfilment", refreshed.getArazzoDocumentRef().title());
   }

   // AC-03: Link sources and Change link open the linking step for an imported document and replace its overlay

   @Test
   void linkSourcesProposesTheCurrentLinksAndTheApiMatchedForASourceLinkedToNone() throws Exception {
      apiIsImported("contracts/customers.yaml");
      jobService.doImportJob(job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true));
      apiIsImported("journeys/order-api.openapi.yaml");

      ArazzoImportAnalysis analysis = arazzoImportService.analyseImportedDocument(documentId(), null);

      assertEquals(List.of("customerApi ../contracts/customers.yaml -> Customer API 1.0.0 [matched Customer API 1.0.0]",
            "orderApi ./order-api.openapi.yaml -> Order API 1.0.0 [matched Order API 1.0.0]"), sources(analysis));
      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0}"), overlays());
   }

   @Test
   void changeLinkIsPrefilledWithTheLinksOfTheOverlay() throws Exception {
      apiIsImported("contracts/customers.yaml");
      apiIsImported("journeys/order-api.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(exampleOf(ARAZZO, "crossed-links"));
      jobService.doImportJob(job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true));

      ArazzoImportAnalysis analysis = arazzoImportService.analyseImportedDocument(documentId(), null);

      assertEquals("orderApi ./order-api.openapi.yaml -> Customer API 1.0.0 [matched Order API 1.0.0]",
            sources(analysis).get(1));
      assertEquals(
            "placeOrder: [Operation CreateOrder not found in Customer API 1.0.0, Operation GetOrder not found in Customer API 1.0.0]",
            analysis.checks().get(1).workflowId() + ": " + analysis.checks().get(1).reasons());
   }

   @Test
   void pickingAnotherApiInTheLinkingStepRecomputesItsAnalysis() throws Exception {
      apiIsImported("contracts/customers.yaml");
      apiIsImported("journeys/order-api.openapi.yaml");
      jobService.doImportJob(job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true));
      Map<String, ServiceRef> links = new HashMap<>();
      links.put("customerApi", null);
      links.put("orderApi", api("Order API", "1.0.0"));

      ArazzoImportAnalysis analysis = arazzoImportService.analyseImportedDocument(documentId(), links);

      assertEquals(List.of("customerApi ../contracts/customers.yaml -> not linked [matched Customer API 1.0.0]",
            "orderApi ./order-api.openapi.yaml -> Order API 1.0.0 [matched Order API 1.0.0]"), sources(analysis));
   }

   @Test
   void theSourcesOfAnUploadedDocumentDeclaredRelativeAreLeftToPick() throws Exception {
      apiIsImported("contracts/customers.yaml");
      arazzoImportService.importArazzoDocument(exampleOf(ARAZZO, DOCUMENT), "place-order.arazzo.yaml", Map.of());

      ArazzoImportAnalysis analysis = arazzoImportService.analyseImportedDocument(documentId(), null);

      assertEquals(List.of("customerApi ../contracts/customers.yaml -> not linked [unreadable]",
            "orderApi ./order-api.openapi.yaml -> not linked [unreadable]"), sources(analysis));
   }

   @Test
   void savingTheLinksReplacesTheSourcesOfTheOverlayAndKeepsTheOverlay() throws Exception {
      apiIsImported("contracts/customers.yaml");
      apiIsImported("journeys/order-api.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(exampleOf(ARAZZO, "crossed-links"));
      String overlayId = arazzoMetadataRepository.listAll().get(0).getId();
      jobService.doImportJob(job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true));
      Map<String, ServiceRef> links = new HashMap<>();
      links.put("customerApi", null);
      links.put("orderApi", api("Order API", "1.0.0"));

      ArazzoDocumentSummary summary = arazzoImportService.relinkSources(documentId(), links);

      assertEquals(List.of("Order fulfilment 1.0.0: {orderApi=Order API 1.0.0}"), overlays());
      assertEquals(overlayId, arazzoMetadataRepository.listAll().get(0).getId());
      assertEquals(
            List.of("searchThenRead: not runnable [no API name and version for this source]", "placeOrder: runnable"),
            workflowsOnTheWorkflowsPage());
      assertEquals(arazzoDocumentService.getDocumentSummaries(null), List.of(summary));
   }

   @Test
   void savingTheLinksOfADocumentWithoutOverlayCreatesIt() throws Exception {
      apiIsImported("journeys/order-api.openapi.yaml");
      arazzoImportService.importArazzoDocument(exampleOf(ARAZZO, DOCUMENT), "place-order.arazzo.yaml", Map.of());
      arazzoMetadataRepository.deleteAll();

      arazzoImportService.relinkSources(documentId(), Map.of("orderApi", api("Order API", "1.0.0")));

      assertEquals(List.of("Order fulfilment 1.0.0: {orderApi=Order API 1.0.0}"), overlays());
   }

   @Test
   void aLinkToAnUnknownSourceIsRefusedAndKeepsTheOverlay() throws Exception {
      arazzoDocumentService.importArazzoMetadata(exampleOf(ARAZZO, "crossed-links"));
      jobService.doImportJob(job("Order fulfilment journeys", repositoryUrl(DOCUMENT), true));
      String documentId = documentId();
      Map<String, ServiceRef> links = Map.of("orderApi", api("Order API", "1.0.0"), "paymentApi",
            api("Payment API", "1.0.0"));

      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> arazzoImportService.relinkSources(documentId, links));

      assertEquals("Unknown source description: paymentApi", refusal.getMessage());
      assertEquals(List.of("Order fulfilment 1.0.0: {customerApi=Customer API 1.0.0, orderApi=Customer API 1.0.0}"),
            overlays());
   }

   @Test
   void anUnknownDocumentHasNothingToLink() throws Exception {
      assertNull(arazzoImportService.analyseImportedDocument("unknown", null));
      assertNull(arazzoImportService.relinkSources("unknown", Map.of()));
      assertEquals(0, arazzoMetadataRepository.count());
   }

   private ImportJob job(String name, String url, boolean mainArtifact) {
      ImportJob job = new ImportJob();
      job.setName(name);
      job.setRepositoryUrl(url);
      job.setMainArtifact(mainArtifact);
      return jobRepository.save(job);
   }

   private SecretRef secretWithToken() {
      Secret secret = new Secret();
      secret.setName("repository");
      secret.setToken(TOKEN);
      secret = secretRepository.save(secret);
      return new SecretRef(secret.getId(), secret.getName());
   }

   private String documentId() {
      return arazzoDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0").getId();
   }

   private static String describe(WorkflowDocument document) {
      return document.getTitle() + " " + document.getVersion() + " from " + document.getFileName() + ": "
            + document.getWorkflowIds();
   }

   private static List<String> sources(ArazzoImportAnalysis analysis) {
      return analysis.sources().stream().map(ArazzoImportersTest::describe).toList();
   }

   private static String describe(ArazzoImportAnalysis.Source source) {
      ArazzoImportAnalysis.Match match = source.match();
      String matched = switch (match.status()) {
         case MATCHED -> "matched " + match.contractTitle() + " " + match.contractVersion();
         case NO_API -> "no API titled " + match.contractTitle() + " " + match.contractVersion();
         case UNREADABLE -> "unreadable";
      };
      String link = source.link().linked() ? source.link().apiName() + " " + source.link().apiVersion() : "not linked";
      return source.sourceName() + " " + source.declaredUrl() + " -> " + link + " [" + matched + "]";
   }

   private List<String> overlays() {
      return arazzoMetadataRepository.listAll().stream().map(ArazzoImportersTest::describe).toList();
   }

   private static String describe(WorkflowMetadata overlay) {
      Map<String, String> sources = new TreeMap<>();
      overlay.getSources().forEach((name, api) -> sources.put(name, api.getName() + " " + api.getVersion()));
      return overlay.getName() + " " + overlay.getVersion() + ": " + sources;
   }

   private List<String> workflowsOnTheWorkflowsPage() {
      return arazzoDocumentService.getDocumentSummaries(null).get(0).workflows().stream()
            .map(ArazzoDocumentSummary.Workflow::status).map(status -> status.workflowId() + ": "
                  + (status.runnable() ? "runnable" : "not runnable " + status.reasons()))
            .toList();
   }

   private static ServiceRef api(String name, String version) {
      return new ServiceRef(null, name, version);
   }

   private void apiIsImported(String contractExample) throws Exception {
      Path contract = importedContracts.resolve(Path.of(contractExample).getFileName());
      Files.writeString(contract, exampleOf(CONTRACTS, contractExample));
      serviceService.importServiceDefinition(contract.toFile(), null,
            new ArtifactInfo(contract.getFileName().toString(), true));
   }

   private static String repositoryUrl(String path) {
      return "http://localhost:" + repositoryServer.getAddress().getPort() + "/repo/" + path;
   }

   private static void serveRepository(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath().substring("/repo/".length());
      if (path.startsWith(PRIVATE)) {
         if (!("Bearer " + TOKEN).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
            respond(exchange, 401, null);
            return;
         }
         path = path.substring(PRIVATE.length());
      }
      if (contents.containsKey(path)) {
         respond(exchange, 200, contents.get(path).getBytes(StandardCharsets.UTF_8));
         return;
      }
      String example = repository.getOrDefault(path, path);
      for (String fixture : List.of(ARAZZO, CONTRACTS)) {
         try {
            respond(exchange, 200, exampleOf(fixture, example).getBytes(StandardCharsets.UTF_8));
            return;
         } catch (IllegalArgumentException iae) {
            // Not an example of this fixture.
         }
      }
      respond(exchange, 404, null);
   }

   private static void respond(HttpExchange exchange, int status, byte[] content) throws IOException {
      exchange.sendResponseHeaders(status, content == null ? -1 : content.length);
      if (content != null) {
         try (OutputStream out = exchange.getResponseBody()) {
            out.write(content);
         }
      }
      exchange.close();
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoImportersTest.class.getResourceAsStream(FIXTURES + fileName)) {
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
