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

import io.microcks.repository.ServiceRepository;
import io.microcks.testsupport.http.ResponseEntity;
import io.microcks.testsupport.http.TestRestTemplate;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for Integration tests using the real HTTP layer and the Quarkus Dev Services MongoDB persistence.
 * @author laurent
 */
@QuarkusTest
public abstract class AbstractBaseIT {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AbstractBaseIT.class);

   @TestHTTPResource
   URL serverUrl;

   @Inject
   protected ServiceRepository serviceRepository;

   protected TestRestTemplate restTemplate;

   @BeforeEach
   void initRestTemplate() {
      restTemplate = new TestRestTemplate(getServerUrl());
   }

   public int getServerPort() {
      return serverUrl.getPort();
   }

   public String getServerUrl() {
      return "http://localhost:" + serverUrl.getPort();
   }

   /** */
   protected void uploadArtifactFile(String artifactFilePath, boolean isMainArtifact) {
      String response = RestAssured.given().multiPart("file", new File(artifactFilePath))
            .queryParam("mainArtifact", isMainArtifact).when().post("/api/artifact/upload").then().statusCode(201)
            .extract().asString();
      log.info("Just uploaded: {}", response);
   }

   protected void assertResponseIsOkAndContains(ResponseEntity<String> response, String substring) {
      assertEquals(200, response.getStatusCode().value());
      assertNotNull(response.getBody());
      assertTrue(response.getBody().contains(substring));
   }
}
