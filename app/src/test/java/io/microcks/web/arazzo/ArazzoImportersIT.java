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
import io.microcks.repository.workflow.WorkflowDocumentRepository;
import io.microcks.repository.workflow.WorkflowMetadataRepository;
import io.microcks.repository.ImportJobRepository;
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
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpMethod;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of the import of Arazzo documents by the import jobs of Importers, and of the endpoints that link
 * their sources again. The repository the jobs import from is served by a local HTTP server, so that these tests do not
 * depend on the internet.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoImportersIT extends AbstractBaseIT {

   private static final Path FIXTURES = Path.of("target/test-classes/io/microcks/service/arazzo/");
   private static final String DOCUMENT = "journeys/place-order.arazzo.yaml";

   private static HttpServer repositoryServer;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ImportJobRepository jobRepository;

   @Inject
   private WorkflowDocumentRepository workflowDocumentRepository;

   @Inject
   private WorkflowMetadataRepository workflowMetadataRepository;

   @TempDir
   private Path importedContracts;

   /** Serves the repository at /repo/: the example of each path. */
   @BeforeAll
   static void startRepositoryServer() throws IOException {
      repositoryServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      repositoryServer.createContext("/repo/", ArazzoImportersIT::serveRepository);
      repositoryServer.start();
   }

   @AfterAll
   static void stopRepositoryServer() {
      repositoryServer.stop(0);
   }

   @AfterEach
   void deleteJobsApisDocumentsAndOverlays() {
      jobRepository.deleteAll();
      serviceRepository.deleteAll();
      workflowDocumentRepository.deleteAll();
      workflowMetadataRepository.deleteAll();
   }

   @Test
   void forcingTheImportOfAJobWhoseUrlPointsToAnArazzoDocumentImportsIt() throws Exception {
      apiIsImported("contracts/customers.yaml");

      JsonNode job = startJob(createJob("Order fulfilment journeys", DOCUMENT, true));

      assertEquals("Order fulfilment 1.0.0 [\"orderApi\"]", job.at("/arazzoDocumentRef/title").asText() + " "
            + job.at("/arazzoDocumentRef/version").asText() + " " + job.at("/arazzoDocumentRef/unlinkedSources"));
      JsonNode documents = get("/api/workflows/documents");
      assertEquals("place-order.arazzo.yaml Customer API",
            documents.at("/0/fileName").asText() + " " + documents.at("/0/sources/0/link/apiName").asText());
      assertEquals(documents.at("/0/id").asText(), job.at("/arazzoDocumentRef/documentId").asText());
   }

   @Test
   void forcingTheImportOfAnOverlayAsASecondaryArtifactLinksTheSources() throws Exception {
      startJob(createJob("Order fulfilment journeys", DOCUMENT, true));
      apiIsImported("contracts/customers.yaml");
      apiIsImported("journeys/order-api.openapi.yaml");

      JsonNode job = startJob(
            createJob("Order fulfilment links", "journeys/order-fulfilment.arazzo-metadata.yaml", false));

      assertEquals("Order fulfilment []",
            job.at("/arazzoDocumentRef/title").asText() + " " + job.at("/arazzoDocumentRef/unlinkedSources"));
      assertEquals(2, workflowMetadataRepository.findByNameAndVersion("Order fulfilment", "1.0.0").getSources().size());
   }

   @Test
   void analysingTheLinksOfAnImportedDocumentAnswersItsSourcesAndStoresNothing() throws Exception {
      apiIsImported("contracts/customers.yaml");
      startJob(createJob("Order fulfilment journeys", DOCUMENT, true));
      apiIsImported("journeys/order-api.openapi.yaml");

      ResponseEntity<String> response = restTemplate
            .postForEntity("/api/workflows/documents/" + documentId() + "/analysis", Map.of(), String.class);

      assertEquals(200, response.getStatusCode().value());
      JsonNode analysis = new ObjectMapper().readTree(response.getBody());
      assertEquals("./order-api.openapi.yaml MATCHED Order API", analysis.at("/sources/1/declaredUrl").asText() + " "
            + analysis.at("/sources/1/match/status").asText() + " " + analysis.at("/sources/1/link/apiName").asText());
      assertEquals(1, workflowMetadataRepository.findByNameAndVersion("Order fulfilment", "1.0.0").getSources().size());
   }

   @Test
   void analysingWithTheChosenLinksRecomputesTheChecks() throws Exception {
      apiIsImported("contracts/customers.yaml");
      startJob(createJob("Order fulfilment journeys", DOCUMENT, true));
      Map<String, Object> links = new HashMap<>();
      links.put("customerApi", null);

      ResponseEntity<String> response = restTemplate
            .postForEntity("/api/workflows/documents/" + documentId() + "/analysis", Map.of("links", links),
            String.class);

      JsonNode analysis = new ObjectMapper().readTree(response.getBody());
      assertEquals("searchThenRead [\"customerApi\"]",
            analysis.at("/checks/0/workflowId").asText() + " " + analysis.at("/checks/0/unlinkedSources"));
   }

   @Test
   void savingTheLinksReplacesTheSourcesOfTheOverlay() throws Exception {
      apiIsImported("contracts/customers.yaml");
      apiIsImported("journeys/order-api.openapi.yaml");
      startJob(createJob("Order fulfilment journeys", DOCUMENT, true));
      Map<String, Object> links = new HashMap<>();
      links.put("customerApi", null);
      links.put("orderApi", Map.of("name", "Order API", "version", "1.0.0"));

      ResponseEntity<String> response = put("/api/workflows/documents/" + documentId() + "/sources",
            Map.of("links", links));

      assertEquals(200, response.getStatusCode().value());
      JsonNode summary = new ObjectMapper().readTree(response.getBody());
      assertEquals("false true", summary.at("/workflows/0/status/runnable").asText() + " "
            + summary.at("/workflows/1/status/runnable").asText());
      assertEquals(List.of("orderApi"), List.copyOf(
            workflowMetadataRepository.findByNameAndVersion("Order fulfilment", "1.0.0").getSources().keySet()));
   }

   @Test
   void aRefusedLinkAnswersItsReasonAndStoresNothing() throws Exception {
      apiIsImported("contracts/customers.yaml");
      startJob(createJob("Order fulfilment journeys", DOCUMENT, true));

      ResponseEntity<String> response = put("/api/workflows/documents/" + documentId() + "/sources",
            Map.of("links", Map.of("paymentApi", Map.of("name", "Payment API", "version", "1.0.0"))));

      assertEquals(400, response.getStatusCode().value());
      assertEquals("Unknown source description: paymentApi", response.getBody());
      assertEquals(List.of("customerApi"), List.copyOf(
            workflowMetadataRepository.findByNameAndVersion("Order fulfilment", "1.0.0").getSources().keySet()));
   }

   @Test
   void anUnknownDocumentIsNotFound() {
      assertEquals(404, restTemplate.postForEntity("/api/workflows/documents/unknown/analysis", Map.of(), String.class)
            .getStatusCode().value());
      assertEquals(404,
            put("/api/workflows/documents/unknown/sources", Map.of("links", Map.of())).getStatusCode().value());
   }

   private String createJob(String name, String path, boolean mainArtifact) throws IOException {
      ResponseEntity<String> response = restTemplate.postForEntity("/api/jobs",
            Map.of("name", name, "repositoryUrl",
                  "http://localhost:" + repositoryServer.getAddress().getPort() + "/repo/" + path, "mainArtifact",
                  mainArtifact),
            String.class);
      assertEquals(201, response.getStatusCode().value());
      return new ObjectMapper().readTree(response.getBody()).path("id").asText();
   }

   /** Force the import of a job, as the Importers page does. */
   private JsonNode startJob(String jobId) throws IOException {
      ResponseEntity<String> response = put("/api/jobs/" + jobId + "/start", null);
      assertEquals(200, response.getStatusCode().value());
      return new ObjectMapper().readTree(response.getBody());
   }

   private ResponseEntity<String> put(String path, Object body) {
      return restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(body), String.class);
   }

   private JsonNode get(String path) throws IOException {
      return new ObjectMapper().readTree(restTemplate.getForEntity(path, String.class).getBody());
   }

   private String documentId() {
      return workflowDocumentRepository.findByTitleAndVersion("Order fulfilment", "1.0.0").getId();
   }

   private void apiIsImported(String contractExample) throws Exception {
      Path contract = importedContracts.resolve(Path.of(contractExample).getFileName());
      Files.writeString(contract, exampleOf("importers.openapi.yaml", contractExample));
      serviceService.importServiceDefinition(contract.toFile(), null,
            new ArtifactInfo(contract.getFileName().toString(), true));
   }

   private static void serveRepository(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath().substring("/repo/".length());
      byte[] content = null;
      for (String fixture : List.of("importers.arazzo.yaml", "importers.openapi.yaml")) {
         try {
            content = exampleOf(fixture, path).getBytes(StandardCharsets.UTF_8);
         } catch (IllegalArgumentException iae) {
            // Not an example of this fixture.
         }
      }
      exchange.sendResponseHeaders(content == null ? 404 : 200, content == null ? -1 : content.length);
      if (content != null) {
         try (OutputStream out = exchange.getResponseBody()) {
            out.write(content);
         }
      }
      exchange.close();
   }

   private static String exampleOf(String fileName, String example) throws IOException {
      String marker = "# example: " + example + " ";
      return Arrays.stream(Files.readString(FIXTURES.resolve(fileName)).split("\n---\n"))
            .filter(document -> document.contains(marker)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fileName));
   }
}
