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
package io.microcks.web;

import io.microcks.testsupport.http.ResponseEntity;
import io.restassured.RestAssured;
import io.microcks.testsupport.http.HttpStatus;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.domain.ArazzoDocument;
import io.microcks.repository.ArazzoDocumentRepository;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration tests of the import of Arazzo documents through the artifact upload endpoint.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoDocumentUploadIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @AfterEach
   void deleteDocuments() {
      arazzoDocumentRepository.deleteAll();
   }

   @Test
   void uploadingASupportedDocumentImportsIt() {
      ResponseEntity<String> response = upload("pastry.arazzo.yaml");

      assertEquals(201, response.getStatusCode().value());
      assertEquals("API Pastry scenarios:1.0.0", response.getBody());
   }

   @Test
   void uploadingAnUnsupportedDocumentIsRefusedWithItsReason() {
      ResponseEntity<String> response = upload("unsupported-version.arazzo.yaml");

      assertEquals(400, response.getStatusCode().value());
      assertEquals("Unsupported Arazzo version: 2.0.0", response.getBody());
   }

   @Test
   void uploadingANewRevisionUpdatesTheStoredDocument() {
      upload("pastry.arazzo.yaml");

      upload("pastry-revision.arazzo.yaml");

      assertEquals(List.of("API Pastry scenarios 1.0.0: [readThenReprice, listThenReadFirst, repriceAll]"),
            arazzoDocumentRepository.listAll().stream().map(ArazzoDocumentUploadIT::describe).toList());
   }

   private ResponseEntity<String> upload(String fixture) {
      io.restassured.response.Response response = RestAssured.given()
            .multiPart("file", new File(FIXTURES + fixture)).when().post("/api/artifact/upload");
      return new ResponseEntity<>(new HttpStatus(response.statusCode()), new HttpHeaders(), response.asString());
   }

   private static String describe(ArazzoDocument document) {
      return document.getTitle() + " " + document.getVersion() + ": " + document.getWorkflowIds();
   }
}
