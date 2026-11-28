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
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.Secret;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.domain.arazzo.WorkflowRunTarget;
import io.microcks.domain.arazzo.WorkflowSourceTarget;
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
 * Acceptance tests of running an Arazzo workflow against implementation endpoints, entering through the run use case,
 * with the Arazzo minion standing at its boundary: one base URL per source description, and an optional secret whose
 * authorization the minion applies to every call.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoImplementationRunTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String CUSTOMERS = "https://customers.staging.example";
   private static final String ORDERS = "https://orders.staging.example";
   private static final String PASTRY_IMPLEMENTATION = "http://localhost:8080";
   /** The inputs each workflow needs to be launched. */
   private static final Map<String, Map<String, Object>> INPUTS = Map.of("placeOrder", Map.of("customerId", "c-42"),
         "readThenReprice", Map.of("pastry", "Eclair Cafe", "newPrice", 2.6));

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
   void eachStepCallsTheBaseUrlSetForItsSource() throws Exception {
      ArazzoDocument document = orderFulfilmentIsImportedAndLinked();

      WorkflowResult run = launch(document, "placeOrder",
            new ArazzoRunImplementations(baseUrls("customerApi", CUSTOMERS, "orderApi", ORDERS), null));

      assertEquals(
            Map.of("customerApi",
                  new ArazzoRunRequest.Source(CUSTOMERS,
                        Map.of("SearchCustomers", new ArazzoRunRequest.Operation("GET", "/customers"), "GetCustomer",
                              new ArazzoRunRequest.Operation("GET", "/customers/{id}"))),
                  "orderApi",
                  new ArazzoRunRequest.Source(ORDERS,
                        Map.of("CreateOrder", new ArazzoRunRequest.Operation("POST", "/orders",
                              List.of("application/json"))))),
            minion.requests().get(0).sources());
      assertNull(minion.requests().get(0).secret());
      assertEquals(WorkflowRunTarget.IMPLEMENTATIONS, run.getTarget());
   }

   @Test
   void aSelectedSecretAuthenticatesEveryCall() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();
      Secret secret = secretIsDefined("pastry-staging-token", "s3cr3t-t0k3n", "X-Pastry-Token");

      WorkflowResult run = launch(document, "readThenReprice",
            new ArazzoRunImplementations(Map.of("pastryApi", PASTRY_IMPLEMENTATION), "pastry-staging-token"));

      assertEquals(new ArazzoRunRequest.Secret("pastry-staging-token", null, null, "s3cr3t-t0k3n", "X-Pastry-Token"),
            minion.requests().get(0).secret());
      WorkflowResult stored = workflowResultRepository.findByIdOptional(run.getId()).orElseThrow();
      assertEquals(secret.getId() + " pastry-staging-token",
            stored.getSecretRef().getSecretId() + " " + stored.getSecretRef().getName());
   }

   @Test
   void aSecretWithUsernameAndPasswordIsHandedWithThem() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();
      Secret secret = new Secret();
      secret.setName("pastry-staging-basic");
      secret.setUsername("baker");
      secret.setPassword("croissant");
      secret.setCaCertPem("-----BEGIN CERTIFICATE-----");
      secretRepository.save(secret);

      launch(document, "readThenReprice",
            new ArazzoRunImplementations(Map.of("pastryApi", PASTRY_IMPLEMENTATION), "pastry-staging-basic"));

      assertEquals(new ArazzoRunRequest.Secret("pastry-staging-basic", "baker", "croissant", null, null),
            minion.requests().get(0).secret());
   }

   @Test
   void theRunRecordsWhichImplementationItTargeted() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      WorkflowResult run = launch(document, "readThenReprice",
            new ArazzoRunImplementations(Map.of("pastryApi", PASTRY_IMPLEMENTATION), null));

      WorkflowResult opened = arazzoRunService.getRun(run.getId()).orElseThrow();
      String serviceId = serviceRepository.findByNameAndVersion("API Pastry - 2.0", "2.0.0").getId();
      assertEquals(WorkflowRunTarget.IMPLEMENTATIONS, opened.getTarget());
      assertEquals(
            List.of(
                  new WorkflowSourceTarget("pastryApi", serviceId, "API Pastry - 2.0", "2.0.0", PASTRY_IMPLEMENTATION)),
            opened.getSources());
      assertNull(opened.getSecretRef());
   }

   @Test
   void theTrailingSlashesOfABaseUrlAreLeftOut() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      WorkflowResult run = launch(document, "readThenReprice",
            new ArazzoRunImplementations(Map.of("pastryApi", " http://localhost:8080/api// "), null));

      assertEquals("http://localhost:8080/api", run.getSources().get(0).baseUrl());
      assertEquals("http://localhost:8080/api", minion.requests().get(0).sources().get("pastryApi").baseUrl());
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "orderApi | https://orders.staging.example | | Workflow placeOrder cannot run against implementations: "
               + "no base URL for source customerApi",
         "customerApi | '  ' | | Workflow placeOrder cannot run against implementations: "
               + "no base URL for source customerApi",
         "customerApi | customers.staging.example | | Workflow placeOrder cannot run against implementations: "
               + "the base URL of source customerApi is not an absolute http or https URL: customers.staging.example",
         "customerApi | ftp://customers.staging.example | | Workflow placeOrder cannot run against implementations: "
               + "the base URL of source customerApi is not an absolute http or https URL: "
               + "ftp://customers.staging.example",
         "customerApi | https://customers.staging.example | unknown-secret | Secret 'unknown-secret' not found. "
               + "Please ensure the secret exists before running the workflow." })
   void aRunAgainstImplementationsThatCannotReachThemIsRefused(String sourceName, String baseUrl, String secretName,
         String reason) throws Exception {
      ArazzoDocument document = orderFulfilmentIsImportedAndLinked();
      Map<String, String> baseUrls = baseUrls(sourceName, baseUrl, "orderApi", ORDERS);

      ArazzoRunRefusedException refused = assertThrows(ArazzoRunRefusedException.class,
            () -> arazzoRunService.launchRun(document.getId(), "placeOrder", INPUTS.get("placeOrder"), 10000L,
                  new ArazzoRunImplementations(baseUrls, secretName)));

      assertEquals(reason, refused.getMessage());
      assertEquals(List.of(), workflowResultRepository.listAll());
      assertEquals(List.of(), minion.requests());
   }

   @Test
   void aRunAgainstImplementationsWithoutBaseUrlsIsRefused() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      ArazzoRunRefusedException refused = assertThrows(ArazzoRunRefusedException.class,
            () -> launch(document, "readThenReprice", new ArazzoRunImplementations(null, null)));

      assertEquals("Workflow readThenReprice cannot run against implementations: no base URL for source pastryApi",
            refused.getMessage());
   }

   @Test
   void aRunAgainstTheMocksIsLaunchedWithoutImplementations() throws Exception {
      ArazzoDocument document = pastryScenariosAreImportedAndLinked();

      WorkflowResult run = launch(document, "readThenReprice", null);

      assertEquals(WorkflowRunTarget.MOCKS, run.getTarget());
      assertEquals("/rest/API+Pastry+-+2.0/2.0.0", minion.requests().get(0).sources().get("pastryApi").baseUrl());
   }

   private WorkflowResult launch(ArazzoDocument document, String workflowId, ArazzoRunImplementations implementations)
         throws ArazzoRunRefusedException {
      return arazzoRunService.launchRun(document.getId(), workflowId, INPUTS.get(workflowId), 10000L, implementations)
            .orElseThrow();
   }

   private static Map<String, String> baseUrls(String sourceName, String baseUrl, String otherName,
         String otherBaseUrl) {
      Map<String, String> baseUrls = new LinkedHashMap<>();
      baseUrls.put(sourceName, baseUrl);
      baseUrls.put(otherName, otherBaseUrl);
      return baseUrls;
   }

   private Secret secretIsDefined(String name, String token, String tokenHeader) {
      Secret secret = new Secret();
      secret.setName(name);
      secret.setDescription("Token of the staging implementation of API Pastry");
      secret.setToken(token);
      secret.setTokenHeader(tokenHeader);
      return secretRepository.save(secret);
   }

   private ArazzoDocument orderFulfilmentIsImportedAndLinked() throws Exception {
      apiIsImported("customer-api.openapi.yaml");
      apiIsImported("order-api.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(fixture("order-fulfilment.arazzo-metadata.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture("order-fulfilment.arazzo.yaml"));
   }

   private ArazzoDocument pastryScenariosAreImportedAndLinked() throws Exception {
      apiIsImported("api-pastry-2.0.openapi.yaml");
      arazzoDocumentService.importArazzoMetadata(fixture("pastry.arazzo-metadata.yaml"));
      return arazzoDocumentService.importArazzoDocument(fixture("pastry-scenarios.arazzo.yaml"));
   }

   private void apiIsImported(String fileName) throws Exception {
      File contract = new File(ArazzoImplementationRunTest.class.getResource(FIXTURES + fileName).toURI());
      serviceService.importServiceDefinition(contract, null, new ArtifactInfo(fileName, true));
   }

   private static String fixture(String fileName) throws IOException {
      try (InputStream stream = ArazzoImplementationRunTest.class.getResourceAsStream(FIXTURES + fileName)) {
         return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
