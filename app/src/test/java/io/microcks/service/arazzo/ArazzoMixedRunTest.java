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
import jakarta.inject.Inject;
import io.quarkus.test.junit.QuarkusTest;
import io.microcks.service.arazzo.ArazzoMinionClient;
import org.junit.jupiter.api.BeforeEach;
import io.quarkus.test.junit.QuarkusMock;
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.domain.Secret;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.domain.workflow.WorkflowRunTarget;
import io.microcks.repository.SecretRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of S31: running an Arazzo workflow against the Microcks mocks of some source descriptions and the
 * implementations of the others, entering through the run use case with the Arazzo minion at its boundary. As in the
 * Deep Ink mockup, a mixed run says where each source goes ({@code perSource}: {@code mock} or {@code implementation},
 * a source it does not name going to its mocks) and gives the base URL of each source sent to its implementation.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoMixedRunTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String CUSTOMERS = "https://customers.staging.example";
   private static final String ORDER_MOCKS = "/rest/Order+API/1.0.0";
   private static final Map<String, Object> INPUTS = Map.of("customerId", "c-42");

   @Inject
   private ArazzoRunService arazzoRunService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private SecretRepository secretRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   private final RecordingMinionClient minion = new RecordingMinionClient();

   /** The minion stays at the boundary: runs are recorded, not executed. */
   @BeforeEach
   void recordTheRunsHandedToTheMinion() {
      QuarkusMock.installMockForType(minion, ArazzoMinionClient.class);
   }

   @Test
   void eachSourceRunsWhereItIsSent() throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();

      WorkflowResult run = launch(document, Map.of("customerApi", CUSTOMERS),
            perSource("customerApi", "implementation", "orderApi", "mock"));

      Map<String, ArazzoRunRequest.Source> sources = minion.requests().get(0).sources();
      assertEquals(CUSTOMERS, sources.get("customerApi").baseUrl());
      assertEquals(ORDER_MOCKS, sources.get("orderApi").baseUrl());
      assertEquals(WorkflowRunTarget.MIXED, run.getTarget());
      WorkflowResult stored = workflowResultRepository.findByIdOptional(run.getId()).orElseThrow();
      assertEquals(List.of("customerApi -> " + CUSTOMERS, "orderApi -> " + ORDER_MOCKS),
            stored.getSources().stream().map(source -> source.sourceName() + " -> " + source.baseUrl()).toList());
   }

   @Test
   void aSourceThePerSourceDoesNotNameRunsOnItsMocks() throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();

      WorkflowResult run = launch(document, Map.of("customerApi", CUSTOMERS), perSource("customerApi", "implementation"));

      assertEquals(ORDER_MOCKS, minion.requests().get(0).sources().get("orderApi").baseUrl());
      assertEquals(WorkflowRunTarget.MIXED, run.getTarget());
   }

   @Test
   void theSecretIsHandedForTheCallsToTheImplementations() throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();
      Secret secret = new Secret();
      secret.setName("customers-staging-token");
      secret.setToken("s3cr3t-t0k3n");
      secretRepository.save(secret);

      launch(document, new ArazzoRunImplementations(Map.of("customerApi", CUSTOMERS), "customers-staging-token",
            perSource("customerApi", "implementation", "orderApi", "mock")));

      assertEquals(new ArazzoRunRequest.Secret("customers-staging-token", null, null, "s3cr3t-t0k3n", null),
            minion.requests().get(0).secret());
      // The base URL of the mocks is relative to Microcks: the minion never sends them the secret.
      assertEquals(ORDER_MOCKS, minion.requests().get(0).sources().get("orderApi").baseUrl());
   }

   @ParameterizedTest(name = "{0}")
   @CsvSource(delimiter = '|', textBlock = """
         sent to the mocks with a base URL | orderApi=mock,customerApi=implementation | customerApi=https://customers.staging.example,orderApi=https://orders.staging.example | Workflow placeOrder cannot run: source orderApi is sent to its mocks and given the base URL of an implementation
         sent to its implementation without base URL | customerApi=implementation,orderApi=implementation | customerApi=https://customers.staging.example | Workflow placeOrder cannot run against implementations: no base URL for source orderApi
         not a linked source of the document | billingApi=implementation,customerApi=mock | billingApi=https://billing.staging.example | Workflow placeOrder cannot run: billingApi is not a linked source of Order fulfilment 1.0.0
         sent to an unknown target | customerApi=staging | | Workflow placeOrder cannot run: source customerApi is sent to staging, not to mock or implementation
         not named and given a base URL | customerApi=implementation | customerApi=https://customers.staging.example,orderApi=https://orders.staging.example | Workflow placeOrder cannot run: source orderApi is sent to its mocks and given the base URL of an implementation
         a base URL for a source that is not linked | customerApi=implementation | customerApi=https://customers.staging.example,billingApi=https://billing.staging.example | Workflow placeOrder cannot run: billingApi is not a linked source of Order fulfilment 1.0.0
         names checked before targets | customerApi=staging,billingApi=mock | | Workflow placeOrder cannot run: billingApi is not a linked source of Order fulfilment 1.0.0
         """)
   void aMixedRunThatDoesNotSayClearlyWhereEachSourceGoesIsRefused(String refused, String perSource, String baseUrls,
         String reason) throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();

      ArazzoRunRefusedException refusal = assertThrows(ArazzoRunRefusedException.class,
            () -> launch(document, pairs(baseUrls), pairs(perSource)));

      assertEquals(reason, refusal.getMessage());
      assertEquals(List.of(), workflowResultRepository.listAll());
      assertEquals(List.of(), minion.requests());
   }

   @Test
   void aSourceSentNowhereIsRefused() throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();
      Map<String, String> perSource = new LinkedHashMap<>();
      perSource.put("customerApi", null);

      ArazzoRunRefusedException refusal = assertThrows(ArazzoRunRefusedException.class,
            () -> launch(document, Map.of(), perSource));

      assertEquals("Workflow placeOrder cannot run: source customerApi is sent nowhere, say mock or implementation",
            refusal.getMessage());
   }

   @Test
   void aMixedRunWithEverySourceOnItsMocksIsRecordedAsLaunched() throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();

      WorkflowResult run = launch(document, Map.of(), Map.of());

      assertEquals(WorkflowRunTarget.MIXED, run.getTarget());
      assertEquals(List.of("/rest/Customer+API/1.0.0", ORDER_MOCKS),
            run.getSources().stream().map(source -> source.baseUrl()).toList());
   }

   @Test
   void theSecretIsNeitherNeededNorRecordedWhenNoSourceGoesToItsImplementation() throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();

      WorkflowResult run = launch(document,
            new ArazzoRunImplementations(Map.of(), "unknown-secret", perSource("customerApi", "mock")));

      assertNull(minion.requests().get(0).secret());
      assertNull(workflowResultRepository.findByIdOptional(run.getId()).orElseThrow().getSecretRef());
   }

   @Test
   void anUnknownSecretIsRefusedWhenASourceGoesToItsImplementation() throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();

      ArazzoRunRefusedException refusal = assertThrows(ArazzoRunRefusedException.class,
            () -> launch(document, new ArazzoRunImplementations(Map.of("customerApi", CUSTOMERS), "unknown-secret",
                  perSource("customerApi", "implementation"))));

      assertEquals("Secret 'unknown-secret' not found. Please ensure the secret exists before running the workflow.",
            refusal.getMessage());
   }

   @Test
   void withoutPerSourceARunOnImplementationsStillNeedsTheBaseUrlOfEverySource() throws Exception {
      WorkflowDocument document = orderFulfilmentIsImportedAndLinked();

      ArazzoRunRefusedException refusal = assertThrows(ArazzoRunRefusedException.class,
            () -> launch(document, new ArazzoRunImplementations(Map.of("customerApi", CUSTOMERS), null)));

      assertEquals("Workflow placeOrder cannot run against implementations: no base URL for source orderApi",
            refusal.getMessage());
   }

   private WorkflowResult launch(WorkflowDocument document, Map<String, String> baseUrls, Map<String, String> perSource)
         throws ArazzoRunRefusedException {
      return launch(document, new ArazzoRunImplementations(baseUrls, null, perSource));
   }

   private WorkflowResult launch(WorkflowDocument document, ArazzoRunImplementations implementations)
         throws ArazzoRunRefusedException {
      return arazzoRunService.launchRun(document.getId(), "placeOrder", INPUTS, 10000L, implementations).orElseThrow();
   }

   private static Map<String, String> perSource(String... sourcesAndTargets) {
      Map<String, String> perSource = new LinkedHashMap<>();
      for (int i = 0; i < sourcesAndTargets.length; i += 2) {
         perSource.put(sourcesAndTargets[i], sourcesAndTargets[i + 1]);
      }
      return perSource;
   }

   /** {@code a=x,b=y} as an ordered map; empty for a blank text. */
   private static Map<String, String> pairs(String text) {
      Map<String, String> pairs = new LinkedHashMap<>();
      if (text != null && !text.isBlank()) {
         for (String pair : text.split(",")) {
            String[] keyAndValue = pair.strip().split("=", 2);
            pairs.put(keyAndValue[0], keyAndValue[1]);
         }
      }
      return pairs;
   }

   private WorkflowDocument orderFulfilmentIsImportedAndLinked() throws Exception {
      apiIsImported("customer-api.openapi.yaml");
      apiIsImported("order-api.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(fixture("order-fulfilment.arazzo-metadata.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoMixedRunTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoMixedRunTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
