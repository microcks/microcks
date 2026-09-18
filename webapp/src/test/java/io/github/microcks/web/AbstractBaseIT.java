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
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Base class for Integration tests using Http layers as well as testcontainers for MongoDB persistence.
 * @author laurent
 */
@SpringBootTest(classes = MicrocksApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@ActiveProfiles("it")
@TestPropertySource(locations = { "classpath:/config/test.properties" })
public abstract class AbstractBaseIT {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AbstractBaseIT.class);

   @LocalServerPort
   private int port;

   @Autowired
   protected RestTestClient restTestClient;

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
      registry.add("spring.mongodb.uri", () -> url);
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

      ResponseEntity<String> response;
      if (isMainArtifact) {
         var result = restTestClient.post().uri("/api/artifact/upload").headers(h -> h.addAll(headers)).body(body)
               .exchange().returnResult(String.class);
         response = responseEntity(result);
      } else {
         var result = restTestClient.post().uri("/api/artifact/upload?mainArtifact=false")
               .headers(h -> h.addAll(headers)).body(body).exchange().returnResult(String.class);
         response = responseEntity(result);
      }

      assertEquals(201, response.getStatusCode().value());
      log.info("Just uploaded: {}", response.getBody());
   }

   protected void assertResponseIsOkAndContains(ResponseEntity<String> response, String substring) {
      assertEquals(200, response.getStatusCode().value());
      assertNotNull(response.getBody());
      assertTrue(response.getBody().contains(substring));
   }

   protected <T> ResponseEntity<T> getForEntity(String uri, Class<T> responseType) {
      var result = restTestClient.get().uri(uri).exchange().returnResult(responseType);
      return responseEntity(result);
   }

   protected <T> ResponseEntity<T> postForEntity(String uri, Object request, Class<T> responseType) {
      var requestSpec = restTestClient.post().uri(uri);
      configureRequest(requestSpec, request);
      var result = requestSpec.exchange().returnResult(responseType);
      return responseEntity(result);
   }

   protected <T> ResponseEntity<T> exchange(String uri, HttpMethod method, HttpEntity<?> request,
         Class<T> responseType) {
      var requestSpec = restTestClient.method(method).uri(uri);
      configureRequest(requestSpec, request);
      var result = requestSpec.exchange().returnResult(responseType);
      return responseEntity(result);
   }

   protected <T> ResponseEntity<T> exchange(String uri, HttpMethod method, HttpEntity<?> request,
         ParameterizedTypeReference<T> responseType) {
      var requestSpec = restTestClient.method(method).uri(uri);
      configureRequest(requestSpec, request);
      var result = requestSpec.exchange().returnResult(responseType);
      return responseEntity(result);
   }

   protected void executeSse(String uri, Consumer<InputStream> responseConsumer) {
      RestClient.builder().baseUrl(getServerUrl()).build().get().uri(uri).exchange((request, response) -> {
         responseConsumer.accept(response.getBody());
         return null;
      });
   }

   protected <T> ResponseEntity<T> getForEntityWithoutRedirects(String uri, Class<T> responseType) {
      var requestFactory = new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
      var client = RestTestClient.bindToServer(requestFactory).baseUrl(getServerUrl()).build();
      var result = client.get().uri(uri).exchange().returnResult(responseType);
      return responseEntity(result);
   }

   private void configureRequest(RestTestClient.RequestBodySpec requestSpec, Object request) {
      if (request instanceof HttpEntity<?> entity) {
         requestSpec.headers(headers -> headers.addAll(entity.getHeaders()));
         if (entity.getBody() != null) {
            requestSpec.body(entity.getBody());
         }
      } else if (request != null) {
         requestSpec.body(request);
      }
   }

   private <T> ResponseEntity<T> responseEntity(EntityExchangeResult<T> result) {
      return new ResponseEntity<>(result.getResponseBody(), result.getResponseHeaders(), result.getStatus());
   }
}
