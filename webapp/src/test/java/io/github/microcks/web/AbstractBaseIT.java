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

import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpRedirects;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
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

   protected RestTestClient restClient;

   protected RestTestClient noRedirectRestClient;

   protected RestClient streamingRestClient;

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

   @BeforeEach
   void initRestClients() {
      restClient = RestTestClient.bindToServer().baseUrl(getServerUrl()).build();

      var noRedirectRequestFactory = ClientHttpRequestFactoryBuilder.httpClient()
            .withHttpClientSettings(HttpClientSettings.defaults().withRedirects(HttpRedirects.DONT_FOLLOW)).build();
      noRedirectRestClient = RestTestClient.bindToServer(noRedirectRequestFactory).baseUrl(getServerUrl()).build();

      streamingRestClient = RestClient.builder().baseUrl(getServerUrl()).build();
   }

   /** */
   protected void uploadArtifactFile(String artifactFilePath, boolean isMainArtifact) {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.MULTIPART_FORM_DATA);

      MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
      body.add("file", new FileSystemResource(new File(artifactFilePath)));

      EntityExchangeResult<String> response;
      if (isMainArtifact) {
         response = restClient.post().uri("/api/artifact/upload").headers(httpHeaders -> httpHeaders.addAll(headers))
               .body(body).exchange().expectBody(String.class).returnResult();
      } else {
         response = restClient.post().uri("/api/artifact/upload?mainArtifact=false")
               .headers(httpHeaders -> httpHeaders.addAll(headers)).body(body).exchange().expectBody(String.class)
               .returnResult();
      }

      assertEquals(201, response.getStatus().value());
      log.info("Just uploaded: {}", response.getResponseBody());
   }

   protected void assertResponseIsOkAndContains(EntityExchangeResult<String> response, String substring) {
      assertEquals(200, response.getStatus().value());
      assertNotNull(response.getResponseBody());
      assertTrue(response.getResponseBody().contains(substring));
   }

   protected <T> EntityExchangeResult<T> getForEntity(String uri, Class<T> responseType) {
      return restClient.get().uri(uri).exchange().expectBody(responseType).returnResult();
   }

   protected <T> EntityExchangeResult<T> postForEntity(String uri, Object body, Class<T> responseType) {
      return restClient.post().uri(uri).body(body).exchange().expectBody(responseType).returnResult();
   }

   protected <T> EntityExchangeResult<T> postForEntity(String uri, HttpEntity<?> requestEntity, Class<T> responseType) {
      return restClient.post().uri(uri).headers(headers -> headers.addAll(requestEntity.getHeaders()))
            .body(requestEntity.getBody()).exchange().expectBody(responseType).returnResult();
   }

   protected <T> EntityExchangeResult<T> exchange(String uri, HttpMethod method, HttpEntity<?> requestEntity,
         Class<T> responseType) {
      return request(method, uri, requestEntity).expectBody(responseType).returnResult();
   }

   protected <T> EntityExchangeResult<T> exchange(String uri, HttpMethod method, HttpEntity<?> requestEntity,
         ParameterizedTypeReference<T> responseType) {
      return request(method, uri, requestEntity).expectBody(responseType).returnResult();
   }

   protected <T> EntityExchangeResult<T> getForEntityWithoutRedirects(String uri, Class<T> responseType) {
      return noRedirectRestClient.get().uri(uri).exchange().expectBody(responseType).returnResult();
   }

   private RestTestClient.ResponseSpec request(HttpMethod method, String uri, HttpEntity<?> requestEntity) {
      if (requestEntity == null) {
         return restClient.method(method).uri(uri).exchange();
      }
      if (requestEntity.getBody() == null) {
         return restClient.method(method).uri(uri).headers(headers -> headers.addAll(requestEntity.getHeaders()))
               .exchange();
      }
      return restClient.method(method).uri(uri).headers(headers -> headers.addAll(requestEntity.getHeaders()))
            .body(requestEntity.getBody()).exchange();
   }
}
