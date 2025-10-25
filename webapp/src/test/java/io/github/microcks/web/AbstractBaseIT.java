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
package io.github.microcks.web;

import io.github.microcks.MicrocksApplication;
import io.github.microcks.repository.ServiceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for Integration tests using Http layers as well as testcontainers for MongoDB persistence.
 * @author laurent
 */
@SpringBootTest(classes = MicrocksApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@TestPropertySource(locations = { "classpath:/config/test.properties" })
public abstract class AbstractBaseIT {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AbstractBaseIT.class);

   @LocalServerPort
   private int port;

   @Autowired
   protected TestRestTemplate restTemplate;

   @Autowired
   protected ServiceRepository serviceRepository;

   private static final MongoDBContainer mongoDBContainer;

   static {
      mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4")).withReuse(false);
      mongoDBContainer.start();
   }

   @DynamicPropertySource
   public static void setDatasourceProperties(final DynamicPropertyRegistry registry) {
      String url = "mongodb://" + mongoDBContainer.getHost() + ":" + mongoDBContainer.getMappedPort(27017)
            + "/microcksIT";
      registry.add("spring.data.mongodb.uri", () -> url);
   }

   public int getServerPort() {
      return port;
   }

   public String getServerUrl() {
      return "http://localhost:" + port;
   }

   /** */
   protected void uploadArtifactFile(String artifactFilePath, boolean isMainArtifact) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", new FileSystemResource(new File(artifactFilePath)));

      HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

      ResponseEntity<String> response;
      if (isMainArtifact) {
         response = restTemplate.postForEntity("/api/artifact/upload", requestEntity, String.class);
      } else {
         response = restTemplate.postForEntity("/api/artifact/upload?mainArtifact=false", requestEntity, String.class);
      }

      assertEquals(201, response.getStatusCode().value());
      log.info("Just uploaded: {}", response.getBody());
   }

   protected void assertResponseIsOkAndContains(ResponseEntity<String> response, String substring) {
      assertEquals(200, response.getStatusCode().value());
      assertNotNull(response.getBody());
      assertTrue(response.getBody().contains(substring));
   }
}
