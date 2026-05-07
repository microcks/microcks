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
package io.github.microcks;

import com.code_intelligence.jazzer.api.BugDetectors;
import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;

import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * @author laurent
 */
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@AutoConfigureTestRestTemplate
@SpringBootTest(classes = MicrocksApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("fuzz")
@TestPropertySource(locations = { "classpath:/config/fuzz.properties" })
public class MicrocksApplicationFuzz {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(MicrocksApplicationFuzz.class);

   @LocalServerPort
   private int port;

   private static final MongoDBContainer mongoDBContainer;

   static {
      System.setProperty("JAZZER_FUZZ", "1");
      mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.4")).withReuse(false);
      mongoDBContainer.start();
   }

   @DynamicPropertySource
   public static void setDatasourceProperties(final DynamicPropertyRegistry registry) {
      String url = "mongodb://" + mongoDBContainer.getHost() + ":" + mongoDBContainer.getMappedPort(27017)
            + "/microcksIT";
      registry.add("spring.data.mongodb.uri", () -> url);
   }

   @Autowired
   protected MockMvc mockMvc;

   @Autowired
   protected TestRestTemplate restTemplate;

   private boolean beforeCalled = false;

   @BeforeEach
   void beforeEach() {
      beforeCalled = true;

      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json", true);
   }

   @FuzzTest(maxDuration = "20s")
   public void fuzzVersionInfo(FuzzedDataProvider data) throws Exception {
      if (!beforeCalled) {
         throw new RuntimeException("BeforeEach was not called");
      }

      // Allow connection to MongoDB container.
      try (var unused = BugDetectors.allowNetworkConnections(
            (host, portD) -> host.equals("localhost") && portD.equals(mongoDBContainer.getMappedPort(27017)))) {

         String name = data.consumeRemainingAsString();
         apiTest(mockMvc, get("/api/version/info").param("name", name));
      }
   }

   @FuzzTest(maxDuration = "20s")
   public void fuzzServices(FuzzedDataProvider data) throws Exception {
      if (!beforeCalled) {
         throw new RuntimeException("BeforeEach was not called");
      }

      // Allow connection to MongoDB container.
      try (var unused = BugDetectors.allowNetworkConnections(
            (host, portD) -> host.equals("localhost") && portD.equals(mongoDBContainer.getMappedPort(27017)))) {

         int page = data.consumeInt(0, 10);
         apiTest(mockMvc, get("/api/services").param("page", String.valueOf(page)));

         String serviceId = data.consumeAsciiString(32);
         String encodedServiceId = URLEncoder.encode(serviceId, StandardCharsets.UTF_8);
         apiTest(mockMvc, get("/api/services/" + encodedServiceId));
      }
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

   protected static ResultActions apiTest(MockMvc mockMvc, MockHttpServletRequestBuilder requestBuilder)
         throws Exception {
      return mockMvc.perform(requestBuilder);
   }
}
