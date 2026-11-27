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
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSourcesRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of S28, an Arazzo 1.1 document mixing OpenAPI and AsyncAPI sources uploaded as the API developer
 * does: its steps are read through the REST endpoint of a workflow, and a run of a workflow with async steps is refused
 * through the run endpoint.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoAsyncStepsIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoSourcesRepository arazzoSourcesRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   @TempDir
   private Path uploads;

   @AfterEach
   void deleteDocumentsAndOverlays() {
      arazzoDocumentRepository.deleteAll();
      arazzoSourcesRepository.deleteAll();
   }

   /** AC-02: the workflow endpoint gives the channel, action, correlationId and timeout of each async step. */
   @Test
   void eachAsyncStepGivesItsChannelActionCorrelationIdAndTimeout() throws Exception {
      String documentId = orderEventsAreUploadedAndLinked();

      ResponseEntity<String> response = restTemplate
            .getForEntity("/api/arazzo/documents/" + documentId + "/workflows/placeThenConfirm", String.class);
      JsonNode steps = new ObjectMapper().readTree(response.getBody()).path("steps");

      assertEquals(200, response.getStatusCode().value());
      assertEquals("createOrder exchanges no message: false",
            steps.path(0).path("stepId").asText() + " exchanges no message: " + steps.path(0).hasNonNull("messaging"));
      assertEquals(
            "{\"action\":\"receive\",\"correlationId\":\"$steps.createOrder.outputs.orderId\",\"timeout\":6000} "
                  + "{\"sourceName\":\"orderEvents\",\"operationId\":\"receiveOrderConfirmed\",\"method\":\"RECEIVE\","
                  + "\"path\":\"receiveOrderConfirmed\",\"channel\":\"orders/confirmed\"}",
            steps.path(2).path("messaging") + " " + steps.path(2).path("operation"));
   }

   /** AC-04: a run of a workflow with async steps is refused with a clear reason, and no run is created. */
   @Test
   void aRunOfAWorkflowWithAsyncStepsIsABadRequestWithItsReason() throws Exception {
      String documentId = orderEventsAreUploadedAndLinked();
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      ResponseEntity<String> refused = restTemplate.postForEntity(
            "/api/arazzo/documents/" + documentId + "/workflows/placeThenConfirm/runs",
            new HttpEntity<>("{\"inputs\": {\"customer\": \"laurent\"}, \"timeout\": 10000}", headers), String.class);

      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Async steps cannot be run yet: requestShipping, confirmOrder", refused.getBody());
      assertEquals(0, workflowResultRepository.count());
   }

   private String orderEventsAreUploadedAndLinked() throws IOException {
      uploadArtifactFile(example("async-steps.contracts.yaml", "order-api"), true);
      uploadArtifactFile(example("async-steps.contracts.yaml", "order-events"), true);
      uploadArtifactFile(example("async-steps.arazzo.yaml", "order-events"), true);
      uploadArtifactFile(FIXTURES + "async-steps.arazzo-sources.yaml", false);
      return arazzoDocumentRepository.findByTitleAndVersion("Order events scenarios", "1.0.0").getId();
   }

   /** Writes the example of a multi-document fixture to its own file, as the API developer would upload it. */
   private String example(String fixture, String example) throws IOException {
      String marker = "# example: " + example + " ";
      String document = Arrays.stream(Files.readString(Path.of(FIXTURES + fixture)).split("\n---\n"))
            .filter(candidate -> candidate.contains(marker)).findFirst().orElseThrow();
      Path file = uploads.resolve(example + (fixture.contains("arazzo") ? ".arazzo.yaml" : ".yaml"));
      Files.writeString(file, document, StandardCharsets.UTF_8);
      return file.toString();
   }
}
