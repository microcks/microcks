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
import io.microcks.domain.arazzo.ArazzoSourceLink;
import io.microcks.domain.arazzo.ArazzoWorkflowStatus;
import io.microcks.domain.Service;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoExamplesRepository;
import io.microcks.repository.arazzo.ArazzoMetadataRepository;
import io.microcks.service.arazzo.ArazzoDocumentService;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of the import of ArazzoMetadata and ArazzoExamples through the artifact upload endpoint, as
 * secondary artifacts.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoMetadataUploadIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoMetadataRepository arazzoMetadataRepository;

   @Inject
   private ArazzoExamplesRepository arazzoExamplesRepository;

   @AfterEach
   void deleteDocumentsAndMetadata() {
      arazzoDocumentRepository.deleteAll();
      arazzoMetadataRepository.deleteAll();
      arazzoExamplesRepository.deleteAll();
      // Order API is missing at the start of each test.
      Service orderApi = serviceRepository.findByNameAndVersion("Order API", "1.0.0");
      if (orderApi != null) {
         serviceRepository.delete(orderApi);
      }
   }

   @Test
   void uploadingAnArazzoMetadataAsASecondaryArtifactLinksTheSourcesOfItsDocument() {
      uploadArtifactFile(FIXTURES + "customer-api.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "order-fulfilment.arazzo.yaml", true);

      ResponseEntity<String> response = upload("order-fulfilment.arazzo-metadata.yaml");

      assertEquals(201, response.getStatusCode().value());
      assertEquals("Order fulfilment:1.0.0", response.getBody());
      assertEquals(List.of("customerApi -> Customer API 1.0.0: linked", "orderApi -> Order API 1.0.0: not linked"),
            arazzoDocumentService.getSourceLinks("Order fulfilment", "1.0.0").stream()
                  .map(ArazzoMetadataUploadIT::describe).toList());
      assertEquals(List.of("searchThenRead: runnable", "placeOrder: not runnable [API Order API 1.0.0 not found]"),
            arazzoDocumentService.getWorkflowStatuses("Order fulfilment", "1.0.0").stream()
                  .map(ArazzoMetadataUploadIT::describe).toList());
   }

   @Test
   void uploadingTheMissingApiLaterMakesItsWorkflowsRunnable() {
      uploadArtifactFile(FIXTURES + "customer-api.openapi.yaml", true);
      uploadArtifactFile(FIXTURES + "order-fulfilment.arazzo.yaml", true);
      upload("order-fulfilment.arazzo-metadata.yaml");

      uploadArtifactFile(FIXTURES + "order-api.openapi.yaml", true);

      assertEquals(List.of("searchThenRead: runnable", "placeOrder: runnable"), arazzoDocumentService
            .getWorkflowStatuses("Order fulfilment", "1.0.0").stream().map(ArazzoMetadataUploadIT::describe).toList());
   }

   @Test
   void uploadingExamplesBeforeTheirDocumentIsRefusedAndAfterIsAccepted() {
      ResponseEntity<String> refused = upload("recorded-examples.arazzo-examples.yaml");

      assertEquals(400, refused.getStatusCode().value());
      assertEquals(0, arazzoExamplesRepository.count());

      uploadArtifactFile(FIXTURES + "recorded-examples.arazzo.yaml", true);
      ResponseEntity<String> accepted = upload("recorded-examples.arazzo-examples.yaml");

      assertEquals(201, accepted.getStatusCode().value());
      assertEquals("Pastry examples:1.0.0", accepted.getBody());
      assertEquals(1, arazzoExamplesRepository.count());
   }

   private ResponseEntity<String> upload(String fixture) {
      io.restassured.response.Response response = RestAssured.given()
            .multiPart("file", new File(FIXTURES + fixture)).when().post("/api/artifact/upload");
      return new ResponseEntity<>(new HttpStatus(response.statusCode()), new HttpHeaders(), response.asString());
   }

   private static String describe(ArazzoSourceLink link) {
      return link.sourceName() + " -> " + link.apiName() + " " + link.apiVersion() + ": "
            + (link.linked() ? "linked" : "not linked");
   }

   private static String describe(ArazzoWorkflowStatus status) {
      return status.workflowId() + ": " + (status.runnable() ? "runnable" : "not runnable " + status.reasons());
   }
}
