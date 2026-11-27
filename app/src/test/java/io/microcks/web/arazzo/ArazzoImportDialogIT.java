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
package io.microcks.web.arazzo;

import io.microcks.web.AbstractBaseIT;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSourcesRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.microcks.testsupport.http.ResponseEntity;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests of the endpoints behind the import dialog of Arazzo documents. The contracts the source
 * descriptions declare are served by a local HTTP server, so that these tests do not depend on the internet.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoImportDialogIT extends AbstractBaseIT {

   private static final Path FIXTURES = Path.of("target/test-classes/io/microcks/service/arazzo/");

   private static HttpServer contractServer;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoSourcesRepository arazzoSourcesRepository;

   @TempDir
   private Path importedContracts;

   /** Serves the contract example named by the {@code example} query parameter, whatever the path. */
   @BeforeAll
   static void startContractServer() throws IOException {
      contractServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      contractServer.createContext("/", ArazzoImportDialogIT::serveContract);
      contractServer.start();
   }

   @AfterAll
   static void stopContractServer() {
      contractServer.stop(0);
   }

   @AfterEach
   void deleteApisDocumentsAndOverlays() {
      serviceRepository.deleteAll();
      arazzoDocumentRepository.deleteAll();
      arazzoSourcesRepository.deleteAll();
   }

   @Test
   void analysingADocumentAnswersItsValidationSourcesAndChecksAndStoresNothing() throws Exception {
      apiIsImported("customer-api");

      ResponseEntity<String> response = restTemplate.postForEntity("/api/arazzo/documents/analysis",
            Map.of("content", document("place-order")), String.class);

      assertEquals(200, response.getStatusCode().value());
      JsonNode analysis = new ObjectMapper().readTree(response.getBody());
      assertEquals("Order fulfilment 1.0.0", analysis.path("title").asText() + " " + analysis.path("version").asText());
      assertEquals("MATCHED Customer API",
            analysis.at("/sources/0/match/status").asText() + " " + analysis.at("/sources/0/link/apiName").asText());
      assertEquals("NO_API false",
            analysis.at("/sources/1/match/status").asText() + " " + analysis.at("/sources/1/link/linked").asText());
      assertEquals("placeOrder [\"orderApi\"]",
            analysis.at("/checks/1/workflowId").asText() + " " + analysis.at("/checks/1/unlinkedSources"));
      assertEquals("Customer API", analysis.at("/apis/0/name").asText());
      assertEquals(0, arazzoDocumentRepository.count());
   }

   @Test
   void analysingADocumentWithTheChosenLinksRecomputesItsChecks() throws Exception {
      apiIsImported("customer-api");
      Map<String, Object> links = new HashMap<>();
      links.put("customerApi", null);

      ResponseEntity<String> response = restTemplate.postForEntity("/api/arazzo/documents/analysis",
            Map.of("content", document("place-order"), "links", links), String.class);

      JsonNode analysis = new ObjectMapper().readTree(response.getBody());
      assertEquals("searchThenRead [\"customerApi\"]",
            analysis.at("/checks/0/workflowId").asText() + " " + analysis.at("/checks/0/unlinkedSources"));
   }

   @Test
   void importingADocumentWithItsLinksStoresBoth() throws Exception {
      apiIsImported("customer-api");

      ResponseEntity<String> response = restTemplate.postForEntity("/api/arazzo/documents",
            Map.of("content", document("place-order"), "fileName", "place-order.arazzo.yaml", "links",
                  Map.of("customerApi", Map.of("name", "Customer API", "version", "1.0.0"))),
            String.class);

      assertEquals(201, response.getStatusCode().value());
      JsonNode summary = new ObjectMapper().readTree(response.getBody());
      assertEquals("place-order.arazzo.yaml true",
            summary.path("fileName").asText() + " " + summary.at("/workflows/0/status/runnable").asText());
      assertNotNull(arazzoSourcesRepository.findByNameAndVersion("Order fulfilment", "1.0.0"));
   }

   @Test
   void importingADocumentWithoutLinksLinksNoSource() throws Exception {
      ResponseEntity<String> response = restTemplate.postForEntity("/api/arazzo/documents",
            Map.of("content", document("place-order")), String.class);

      assertEquals(201, response.getStatusCode().value());
      assertEquals("{}",
            arazzoSourcesRepository.findByNameAndVersion("Order fulfilment", "1.0.0").getSources().toString());
   }

   @Test
   void aRefusedImportAnswersItsReasonAndStoresNothing() throws Exception {
      ResponseEntity<String> response = restTemplate.postForEntity("/api/arazzo/documents", Map.of("content",
            document("place-order"), "links", Map.of("paymentApi", Map.of("name", "Payment API", "version", "1.0.0"))),
            String.class);

      assertEquals(400, response.getStatusCode().value());
      assertEquals("Unknown source description: paymentApi", response.getBody());
      assertEquals(0, arazzoDocumentRepository.count());
      assertEquals(0, arazzoSourcesRepository.count());
   }

   private void apiIsImported(String contractExample) throws Exception {
      Path contract = importedContracts.resolve(contractExample + ".openapi.yaml");
      Files.writeString(contract, exampleOf("import-dialog.openapi.yaml", contractExample));
      serviceService.importServiceDefinition(contract.toFile(), null,
            new ArtifactInfo(contract.getFileName().toString(), true));
   }

   private static String document(String example) throws IOException {
      return exampleOf("import-dialog.arazzo.yaml", example).replace("CONTRACTS",
            "http://localhost:" + contractServer.getAddress().getPort());
   }

   private static void serveContract(HttpExchange exchange) throws IOException {
      String query = exchange.getRequestURI().getQuery();
      String example = query.substring(query.indexOf("example=") + "example=".length());
      byte[] content = exampleOf("import-dialog.openapi.yaml", example).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, content.length);
      try (OutputStream out = exchange.getResponseBody()) {
         out.write(content);
      }
   }

   private static String exampleOf(String fileName, String example) throws IOException {
      String marker = "# example: " + example + " ";
      return Arrays.stream(Files.readString(FIXTURES.resolve(fileName)).split("\n---\n"))
            .filter(document -> document.contains(marker)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fileName));
   }
}
