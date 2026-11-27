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
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoDocumentSummary;
import io.microcks.domain.arazzo.ArazzoImportAnalysis;
import io.microcks.domain.arazzo.ArazzoSourceAnalysis;
import io.microcks.domain.arazzo.ArazzoStepDetail;
import io.microcks.domain.arazzo.ArazzoStepMessaging;
import io.microcks.domain.arazzo.ArazzoStepOperation;
import io.microcks.domain.arazzo.ArazzoWorkflowDetail;
import io.microcks.domain.arazzo.ArazzoWorkflowStatus;
import io.microcks.domain.arazzo.ArazzoWorkflowSummary;
import io.microcks.domain.ServiceRef;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import io.microcks.artifact.MockRepositoryImportException;

import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of S28, reading Arazzo 1.1 documents whose sources include AsyncAPI descriptions, one or more per
 * acceptance criterion of the story. They enter through the services of the webapp; the contracts the source
 * descriptions declare are served by a local HTTP server, and the Arazzo minion stands at the boundary.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoAsyncStepsTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String DOCUMENTS = "async-steps.arazzo.yaml";
   private static final String CONTRACTS = "async-steps.contracts.yaml";

   private static HttpServer contractServer;

   @Inject
   private ArazzoImportService arazzoImportService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ArazzoWorkflowService arazzoWorkflowService;

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   private final RecordingMinionClient minion = new RecordingMinionClient();

   @TempDir
   private Path importedContracts;

   @BeforeEach
   void handTheRunsToTheRecordingMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   /** Serves the contract example named by the {@code example} query parameter, whatever the path. */
   @BeforeAll
   static void startContractServer() throws IOException {
      contractServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      contractServer.createContext("/", ArazzoAsyncStepsTest::serveContract);
      contractServer.start();
   }

   @AfterAll
   static void stopContractServer() {
      contractServer.stop(0);
   }

   // AC-01: an Arazzo 1.1 document with an AsyncAPI source imports, its source linked to the matching AsyncAPI API

   @Test
   void aDocumentWithAnAsyncApiSourceImportsWithTheSourceLinkedToTheAsyncApiOfSameTitleAndVersion() throws Exception {
      apisAreImported("order-api", "order-events");
      String content = document("order-events");

      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(content, null);
      ArazzoDocumentSummary imported = arazzoImportService.importArazzoDocument(content, "order-events.arazzo.yaml",
            linksOf(analysis));

      assertEquals("Arazzo 1.1.0, refused: null",
            "Arazzo " + analysis.arazzoVersion() + ", refused: " + analysis.refusal());
      assertEquals(
            List.of("orderApi: MATCHED Order API 1.0.0, 1 of 1 called found",
                  "orderEvents: MATCHED Order events 1.0.0, 4 of 5 called found"),
            analysis.sources().stream().map(ArazzoAsyncStepsTest::describe).toList());
      assertEquals(List.of("orderApi -> Order API 1.0.0 REST", "orderEvents -> Order events 1.0.0 EVENT"),
            imported.sources().stream().map(source -> source.link().sourceName() + " -> " + source.link().apiName()
                  + " " + source.link().apiVersion() + " " + source.apiType()).toList());
   }

   // AC-02: the workflow page shows each async step with its action, channel or operation, correlationId and timeout

   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "order-events; placeThenConfirm; createOrder calls POST /orders (CreateOrder) | requestShipping sends on "
               + "shipping/requests (SEND sendShippingRequest) | confirmOrder receives on orders/confirmed (RECEIVE "
               + "receiveOrderConfirmed) correlationId $steps.createOrder.outputs.orderId timeout 6000 ms",
         "order-events; listenOnChannels; confirmed receives on orders/confirmed | cancelled receives on "
               + "orderCancelled",
         "order-events; listenOnUnknownChannel; shipped receives on orderShipped: Channel orderShipped not found in "
               + "Order events 1.0.0",
         "legacy-events; listen; confirmOrder receives on orders/confirmed (SUBSCRIBE orders/confirmed)" })
   void eachStepOfAnAsyncApiSourceShowsWhatItSendsOrReceives(String example, String workflowId, String steps)
         throws Exception {
      ArazzoDocument document = importedAndLinked(example);

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), workflowId)
            .orElseThrow();

      assertEquals(steps, String.join(" | ", workflow.steps().stream().map(ArazzoAsyncStepsTest::describe).toList()));
   }

   @Test
   void theCorrelationIdOfAReceiveStepShowsTheDataItIsGiven() throws Exception {
      ArazzoDocument document = importedAndLinked("order-events");

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), "placeThenConfirm")
            .orElseThrow();

      assertEquals(
            List.of("createOrder.outputs.orderId -> requestShipping body /orderId",
                  "createOrder.outputs.orderId -> confirmOrder correlationId null"),
            workflow
                  .dataPassings().stream().map(passing -> passing.fromStepId() + ".outputs." + passing.outputName()
                        + " -> " + passing.to().stepId() + " " + passing.to().place() + " " + passing.to().target())
                  .toList());
   }

   // AC-03: async step fields out of place or malformed are refused whole, with a reason

   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "action-on-http-step; Invalid Arazzo document: action only applies to a step of an AsyncAPI source: "
               + "createOrder",
         "correlation-id-on-send; Invalid Arazzo document: only a receive step has a correlationId: requestShipping",
         "negative-timeout; Invalid Arazzo document: a step timeout is a number of milliseconds, not -1: "
               + "confirmOrder",
         "unknown-action; Invalid Arazzo document: a step action is send or receive, not subscribe: confirmOrder",
         "depends-on; Unsupported step field: dependsOn" })
   void aDocumentWithAsyncStepFieldsOutOfPlaceOrMalformedIsRefusedWholeWithItsReason(String example, String reason)
         throws Exception {
      String content = document(example);

      ArazzoImportAnalysis analysis = arazzoImportService.analyseArazzoDocument(content, null);
      MockRepositoryImportException refusal = assertThrows(MockRepositoryImportException.class,
            () -> arazzoImportService.importArazzoDocument(content, example + ".arazzo.yaml", Map.of()));

      assertEquals(reason, analysis.refusal());
      assertEquals(reason, refusal.getMessage());
      assertEquals(0, arazzoDocumentRepository.count());
   }

   // AC-04: a run of a workflow with async steps is refused with its reason, and no run is created

   @Test
   void aRunOfAWorkflowWithAsyncStepsIsRefusedWithoutCreatingARun() throws Exception {
      ArazzoDocument document = importedAndLinked("order-events");

      ArazzoRunRefusedException refusal = assertThrows(ArazzoRunRefusedException.class, () -> arazzoRunService
            .launchRun(document.getId(), "placeThenConfirm", Map.of("customer", "laurent"), null));

      assertEquals("Async steps cannot be run yet: requestShipping, confirmOrder", refusal.getMessage());
      assertEquals(0, workflowResultRepository.count());
      assertEquals(0, minion.requests().size());
   }

   @ParameterizedTest
   @CsvSource(delimiter = ';', value = {
         "placeThenConfirm; not runnable [Async steps cannot be run yet: requestShipping, confirmOrder]",
         "listenOnChannels; not runnable [Async steps cannot be run yet: confirmed, cancelled]",
         "placeOnly; runnable []" })
   void theWorkflowPagesTellThatAWorkflowWithAsyncStepsCannotRunYet(String workflowId, String status) throws Exception {
      ArazzoDocument document = importedAndLinked("order-events");

      ArazzoWorkflowDetail workflow = arazzoWorkflowService.getWorkflowDetail(document.getId(), workflowId)
            .orElseThrow();
      ArazzoWorkflowStatus listed = arazzoDocumentService.getDocumentSummaries(null).get(0).workflows().stream()
            .map(ArazzoWorkflowSummary::status).filter(candidate -> candidate.workflowId().equals(workflowId))
            .findFirst().orElseThrow();

      assertEquals(status,
            (workflow.status().runnable() ? "runnable " : "not runnable ") + workflow.status().reasons());
      assertEquals(status, (listed.runnable() ? "runnable " : "not runnable ") + listed.reasons());
   }

   @Test
   void aWorkflowOfTheSameDocumentWithHttpStepsOnlyStillRuns() throws Exception {
      ArazzoDocument document = importedAndLinked("order-events");

      arazzoRunService.launchRun(document.getId(), "placeOnly", Map.of(), null).orElseThrow();

      assertEquals(1, workflowResultRepository.count());
      assertEquals(List.of("placeOnly"), minion.requests().stream().map(ArazzoRunRequest::workflowId).toList());
   }

   private ArazzoDocument importedAndLinked(String example) throws Exception {
      apisAreImported("order-api", "order-events", "legacy-order-events");
      String content = document(example);
      arazzoImportService.importArazzoDocument(content, example + ".arazzo.yaml",
            linksOf(arazzoImportService.analyseArazzoDocument(content, null)));
      return arazzoDocumentRepository.listAll().get(0);
   }

   /** Each source linked to the API its declared contract matches. */
   private static Map<String, ServiceRef> linksOf(ArazzoImportAnalysis analysis) {
      Map<String, ServiceRef> links = new HashMap<>();
      analysis.sources().forEach(source -> links.put(source.sourceName(),
            new ServiceRef(null, source.match().contractTitle(), source.match().contractVersion())));
      return links;
   }

   private static String describe(ArazzoSourceAnalysis source) {
      return source.sourceName() + ": " + source.match().status() + " " + source.match().contractTitle() + " "
            + source.match().contractVersion() + ", " + source.foundOperationCount() + " of " + source.operationCount()
            + " called found";
   }

   private static String describe(ArazzoStepDetail step) {
      ArazzoStepOperation operation = step.operation();
      ArazzoStepMessaging messaging = step.messaging();
      if (messaging == null) {
         return step.stepId() + " calls " + operation.method() + " " + operation.path() + " (" + operation.operationId()
               + ")";
      }
      String line = step.stepId() + " " + messaging.action() + "s on " + operation.channel();
      if (operation.reason() != null) {
         line += ": " + operation.reason();
      } else if (operation.operationId() != null) {
         line += " (" + operation.method() + " " + operation.path() + ")";
      }
      if (messaging.correlationId() != null) {
         line += " correlationId " + messaging.correlationId();
      }
      if (messaging.timeout() != null) {
         line += " timeout " + messaging.timeout() + " ms";
      }
      return line;
   }

   private void apisAreImported(String... contractExamples) throws Exception {
      for (String contractExample : contractExamples) {
         Path contract = importedContracts.resolve(contractExample + ".yaml");
         Files.writeString(contract, exampleOf(CONTRACTS, contractExample));
         serviceService.importServiceDefinition(contract.toFile(), null,
               new ArtifactInfo(contract.getFileName().toString(), true));
      }
   }

   private static String document(String example) throws IOException {
      return exampleOf(DOCUMENTS, example).replace("CONTRACTS",
            "http://localhost:" + contractServer.getAddress().getPort());
   }

   private static void serveContract(HttpExchange exchange) throws IOException {
      String query = exchange.getRequestURI().getQuery();
      byte[] content = exampleOf(CONTRACTS, query.substring(query.indexOf("example=") + "example=".length()))
            .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, content.length);
      try (OutputStream out = exchange.getResponseBody()) {
         out.write(content);
      }
   }

   /**
    * @param fileName The fixture of a category, holding one YAML document per example, each announced by a
    *                 {@code # example: <name> - <what>} comment
    * @param example  The name of the example
    * @return The YAML document of that example
    */
   private static String exampleOf(String fileName, String example) throws IOException {
      try (InputStream stream = ArazzoAsyncStepsTest.class.getResourceAsStream(FIXTURES + fileName)) {
         String marker = "# example: " + example + " ";
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.contains(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fileName));
      }
   }
}
