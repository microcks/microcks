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
import io.microcks.testsupport.http.ResponseEntity;
import io.restassured.RestAssured;
import io.microcks.testsupport.http.HttpStatus;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.repository.workflow.WorkflowDocumentRepository;
import io.microcks.repository.workflow.WorkflowMetadataRepository;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests of the import of Arazzo documents and ArazzoMetadata overlays through the artifact download
 * endpoint, the one behind the import by URL. The artifacts are served by a local HTTP server, so that these tests do
 * not depend on the internet.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoDownloadIT extends AbstractBaseIT {

   private static final Path FIXTURES = Path.of("target/test-classes/io/microcks/service/arazzo/");

   private static HttpServer artifactServer;

   @Inject
   private WorkflowDocumentRepository workflowDocumentRepository;

   @Inject
   private WorkflowMetadataRepository workflowMetadataRepository;

   /** Serves the fixture named by the last segment of the requested path, whatever the directories and query. */
   @BeforeAll
   static void startArtifactServer() throws IOException {
      artifactServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      artifactServer.createContext("/", ArazzoDownloadIT::serveFixture);
      artifactServer.start();
   }

   @AfterAll
   static void stopArtifactServer() {
      artifactServer.stop(0);
   }

   @AfterEach
   void deleteDocumentsAndOverlays() {
      workflowDocumentRepository.deleteAll();
      workflowMetadataRepository.deleteAll();
   }

   @Test
   void downloadingASupportedDocumentImportsItWithTheFileNameOfItsUrl() {
      ResponseEntity<String> response = download(
            artifactUrl("/workflows/pastry%20scenarios.arazzo.yaml?ref=main&served=pastry.arazzo.yaml"), true);

      assertEquals(201, response.getStatusCode().value());
      assertEquals("{\"name\": \"API Pastry scenarios:1.0.0\"}", response.getBody());
      WorkflowDocument imported = workflowDocumentRepository.findByTitleAndVersion("API Pastry scenarios", "1.0.0");
      assertNotNull(imported);
      assertEquals(List.of("readThenReprice", "listThenReadFirst"), imported.getWorkflowIds());
      assertEquals("pastry scenarios.arazzo.yaml", imported.getFileName());
   }

   @Test
   void downloadingADocumentWithoutInfoIsRefusedWithItsReason() {
      ResponseEntity<String> response = download(artifactUrl("/workflow/no-info.arazzo.yaml"), true);

      assertEquals(400, response.getStatusCode().value());
      assertEquals("Invalid Arazzo document: info.title is required", response.getBody());
      assertEquals(0, workflowDocumentRepository.count());
   }

   @Test
   void downloadingAnOverlayAsASecondaryArtifactImportsIt() {
      ResponseEntity<String> response = download(artifactUrl("/order-fulfilment.arazzo-metadata.yaml"), false);

      assertEquals(201, response.getStatusCode().value());
      assertEquals("{\"name\": \"Order fulfilment:1.0.0\"}", response.getBody());
      assertNotNull(workflowMetadataRepository.findByNameAndVersion("Order fulfilment", "1.0.0"));
   }

   private ResponseEntity<String> download(String url, boolean mainArtifact) {
      io.restassured.response.Response response = RestAssured.given().queryParam("url", url)
            .queryParam("mainArtifact", mainArtifact).when().post("/api/artifact/download");
      return new ResponseEntity<>(new HttpStatus(response.statusCode()), new HttpHeaders(), response.asString());
   }

   private static String artifactUrl(String pathAndQuery) {
      return "http://localhost:" + artifactServer.getAddress().getPort() + pathAndQuery;
   }

   /**
    * Serve the fixture given by the {@code served} query parameter, or else named by the last segment of the path.
    */
   private static void serveFixture(HttpExchange exchange) throws IOException {
      String path = exchange.getRequestURI().getPath();
      String query = exchange.getRequestURI().getQuery();
      String fixture = path.substring(path.lastIndexOf('/') + 1);
      if (query != null && query.contains("served=")) {
         fixture = query.substring(query.indexOf("served=") + "served=".length());
      }
      byte[] content = Files.readAllBytes(FIXTURES.resolve(fixture));
      exchange.sendResponseHeaders(200, content.length);
      try (OutputStream out = exchange.getResponseBody()) {
         out.write(content);
      }
   }
}
