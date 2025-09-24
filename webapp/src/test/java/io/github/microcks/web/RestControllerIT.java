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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.ArraySizeComparator;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings.Redirects;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test case for all the Rest mock controller.
 * @author laurent
 */
class RestControllerIT extends AbstractBaseIT {

   @SpyBean
   private RestController restController;

   @Test
   void testOpenAPIMocking() {
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json", true);

      // Check its different mocked operations.
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/PetStore+API/1.0.0/pets", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("[4]", response.getBody(), new ArraySizeComparator(JSONCompareMode.LENIENT));
         JSONAssert.assertEquals(
               "[{\"id\":1,\"name\":\"Zaza\",\"tag\":\"cat\"},{\"id\":2,\"name\":\"Tigresse\",\"tag\":\"cat\"},{\"id\":3,\"name\":\"Maki\",\"tag\":\"cat\"},{\"id\":4,\"name\":\"Toufik\",\"tag\":\"cat\"}]",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      response = restTemplate.getForEntity("/rest/PetStore+API/1.0.0/pets/1", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"id\":1,\"name\":\"Zaza\",\"tag\":\"cat\"}", response.getBody(),
               JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   void testOpenAPIMockingWithValidation() {
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-details-openapi.yaml", true);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));

      // Check its validation endpoint with correct payload
      String patchedPastry = "{\"price\":2.6}";
      HttpEntity<String> requestEntity = new HttpEntity<>(patchedPastry, headers);
      ResponseEntity<String> response = restTemplate.exchange("/rest-valid/pastry-details/1.0.0/pastry/Eclair+Cafe",
            HttpMethod.PATCH, requestEntity, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals(
               "{\"name\":\"Eclair Cafe\",\"description\":\"Delicieux Eclair au Cafe pas calorique du tout\",\"size\":\"M\",\"price\":2.6,\"status\":\"available\"}",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check its validation endpoint with invalid payload
      patchedPastry = "{\"price\":\"2.6\"}";
      requestEntity = new HttpEntity<>(patchedPastry, headers);
      response = restTemplate.exchange("/rest-valid/pastry-details/1.0.0/pastry/Eclair+Cafe", HttpMethod.PATCH,
            requestEntity, String.class);
      assertEquals(400, response.getStatusCode().value());
      assertEquals("[string found, number expected]", response.getBody());
   }

   @Test
   void testSwaggerMocking() {
      // Upload Beer Catalog API swagger and then Postman collection artifacts.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-swagger.json", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-collection.json", false);

      // Check its different mocked operations.
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/Beer+Catalog+API/0.9/beer?page=0",
            String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("[3]", response.getBody(), new ArraySizeComparator(JSONCompareMode.LENIENT));
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      response = restTemplate.getForEntity("/rest/Beer+Catalog+API/0.9/beer/Weissbier", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\n" + "    \"name\": \"Weissbier\",\n" + "    \"country\": \"Germany\",\n"
               + "    \"type\": \"Wheat\",\n" + "    \"rating\": 4.1,\n" + "    \"status\": \"out_of_stock\"\n" + "}",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   void testNoFallbackMatchingWithRegex() {
      // Upload modified pastry spec
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-details-openapi.yaml", true);

      ObjectMapper mapper = new ObjectMapper();

      // Check operation with a defined mock (name: 'Millefeuille')
      ResponseEntity<String> response = restTemplate
            .getForEntity("/rest/pastry-details/1.0.0/pastry/Millefeuille/details", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode details = mapper.readTree(response.getBody());
         String description = details.get("description").asText();
         assertTrue(description.startsWith("Detail -"));
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check operation with an undefined defined mock (name: 'Dummy'), should now return a 400 error as
      // per issue #819 and #1132 to have a consistent behaviour, allow proxying support and this kind of stuff.
      response = restTemplate.getForEntity("/rest/pastry-details/1.0.0/pastry/Dummy/details", String.class);
      assertEquals(400, response.getStatusCode().value());
   }

   @Test
   void testHeadersTemplating() {
      // Upload modified pastry-with-headers-openapi spec
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-headers-openapi.yaml", true);

      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-headers/1.0.0/pastry", String.class);
      assertEquals(200, response.getStatusCode().value());
      assertEquals("some-static-header", response.getHeaders().getFirst("x-some-static-header"));

      String someGenericHeader = response.getHeaders().getFirst("x-some-generic-header");
      assertDoesNotThrow(() -> UUID.fromString(someGenericHeader));

      response = restTemplate.getForEntity("/rest/pastry-headers/1.0.0/pastry?size=XL", String.class);
      String requestBasedHeader = response.getHeaders().getFirst("x-request-based-header");
      assertEquals("XL size", requestBasedHeader);
   }

   @Test
   void testHeadersOnlyResponse() {
      // Upload simple-oidc-redirect-openapi spec
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/simple-oidc-redirect-openapi.yaml", true);

      ResponseEntity<String> response = restTemplate.withRedirects(Redirects.DONT_FOLLOW)
            .getForEntity("/rest/Simple+OIDC/1.0/login/oauth/authorize?"
                  + "response_type=code&client_id=GHCLIENT&scope=openid+user:email&redirect_uri=http://localhost:8080/Login/githubLoginSuccess&state=e956e017-5e13-4c9d-b83b-6dd6337a6a86",
                  String.class);
      assertEquals(302, response.getStatusCode().value());

      String content = response.getBody();
      assertNull(content);

      String location = response.getHeaders().getFirst("location");
      assertNotNull(location);
      assertTrue(location.startsWith("http://localhost:8080/Login/githubLoginSuccess?"));
      assertTrue(location.contains("state=e956e017-5e13-4c9d-b83b-6dd6337a6a86"));
   }

   @Test
   void testProxyFallback() {
      // Upload pastry-with-proxy-fallback and pastry-for-proxy specs
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-proxy-fallback-openapi.yaml",
            true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-for-proxy-openapi.yaml", true);

      // Set real port to the dispatcher
      Service service = serviceRepository.findByNameAndVersion("pastry-proxy", "1.0.0");
      Operation op = service.getOperations().stream().filter(o -> o.getName().endsWith("GET /pastry")).findFirst()
            .orElseThrow();
      op.setDispatcherRules(op.getDispatcherRules().replaceFirst("http://localhost", getServerUrl()));
      serviceRepository.save(service);

      // If we have the mock, we should get the response from the mock.
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry?name=donut",
            String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"name\":\"Mocked One\"}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // If we don't have the mock, we should get the response from real backend.
      response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry?name=croissant", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"name\":\"Croissant from Real One\"}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   void testProxyFallbackWithDelay() {
      // Upload pastry-with-proxy-fallback and pastry-for-proxy specs
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-proxy-fallback-openapi.yaml",
            true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-for-proxy-openapi.yaml", true);

      // Set real port to the dispatcher
      Service service = serviceRepository.findByNameAndVersion("pastry-proxy", "1.0.0");
      Operation op = service.getOperations().stream().filter(o -> o.getName().endsWith("GET /pastry")).findFirst()
            .orElseThrow();
      op.setDispatcherRules(op.getDispatcherRules().replaceFirst("http://localhost", getServerUrl()));
      serviceRepository.save(service);

      // If we have the mock, we should get the response from the mock.
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry?name=donut",
            String.class);
      long mockedResponseTime = System.currentTimeMillis() - startTime;
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"name\":\"Mocked One\"}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // If we don't have the mock, we should get the response from real backend.
      startTime = System.currentTimeMillis();
      response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry?name=croissant", String.class);
      long realResponseTime = System.currentTimeMillis() - startTime;
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"name\":\"Croissant from Real One\"}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Introduce request delay.
      long delay = 150l;
      op.setDefaultDelay(delay);
      op.setDefaultDelayStrategy("fixed");
      serviceRepository.save(service);

      // If we have the mock, we should get the response from the mock.
      startTime = System.currentTimeMillis();
      response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry?name=donut", String.class);
      long mockedResponseTimeDelayed = System.currentTimeMillis() - startTime;
      // Assert that the response time is greater than the delay and greater that .
      assertTrue(mockedResponseTimeDelayed >= delay,
            "mocked response time delayed: " + mockedResponseTimeDelayed + "ms");
      assertTrue(mockedResponseTimeDelayed >= mockedResponseTime,
            "mocked response time: " + mockedResponseTime + "ms, delayed: " + mockedResponseTimeDelayed + "ms");
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"name\":\"Mocked One\"}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // If we don't have the mock, we should get the response from real backend.
      startTime = System.currentTimeMillis();
      response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry?name=croissant", String.class);
      long realResponseTimeDelayed = System.currentTimeMillis() - startTime;
      // Assert that the response time is greater than the delay and greater that .
      assertTrue(realResponseTimeDelayed >= delay, "real response time delayed: " + realResponseTimeDelayed + "ms");
      assertTrue(realResponseTimeDelayed >= realResponseTime,
            "real response time: " + mockedResponseTime + "ms, delayed: " + realResponseTimeDelayed + "ms");
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"name\":\"Croissant from Real One\"}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   void testProxyFallbackWithEqualsOriginAndExternalUrls() {
      // Upload pastry-with-proxy-fallback and pastry-for-proxy specs
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-proxy-fallback-openapi.yaml",
            true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-for-proxy-openapi.yaml", true);

      // Set original URL to the dispatcher
      Service service = serviceRepository.findByNameAndVersion("pastry-proxy", "1.0.0");
      Operation op = service.getOperations().stream().filter(o -> o.getName().endsWith("GET /pastry")).findFirst()
            .orElseThrow();
      op.setDispatcherRules(op.getDispatcherRules().replaceFirst("http://localhost", getServerUrl())
            .replaceFirst("pastry-real", "pastry-proxy"));
      serviceRepository.save(service);

      // Check that we don't fall into infinite loop and that we can't locally handle the call (error 400)
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry?name=realDonut",
            String.class);
      assertEquals(400, response.getStatusCode().value());
      verify(restController, times(1)).execute(any(), any(), any(), any(), any(), any(), any(), any());
   }

   @Test
   void testProxyFallbackWithHttpError() {
      // Upload pastry-with-proxy-fallback and pastry-for-proxy specs
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-proxy-fallback-openapi.yaml",
            true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-for-proxy-openapi.yaml", true);

      // Broke external URL in the dispatcher
      Service service = serviceRepository.findByNameAndVersion("pastry-proxy", "1.0.0");
      Operation op = service.getOperations().stream().filter(o -> o.getName().endsWith("GET /pastry")).findFirst()
            .orElseThrow();
      op.setDispatcherRules(op.getDispatcherRules().replaceFirst("http://localhost", getServerUrl())
            .replaceFirst("pastry-real", "not-found"));
      serviceRepository.save(service);

      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry?name=realDonut",
            String.class);
      assertEquals(404, response.getStatusCode().value());
   }

   @Test
   void testProxy() {
      // Upload pastry-with-proxy and pastry-for-proxy specs
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-proxy-openapi.yaml", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-for-proxy-openapi.yaml", true);

      // Set real port to the dispatcher
      Service service = serviceRepository.findByNameAndVersion("pastry-proxy", "1.0.0");
      Operation op = service.getOperations().stream().filter(o -> o.getName().endsWith("GET /pastry/{name}"))
            .findFirst().orElseThrow();
      op.setDispatcherRules(op.getDispatcherRules().replaceFirst("http://localhost", getServerUrl()));
      serviceRepository.save(service);

      // Event if `donut` is defined on our mock, we should always have the response coming for real backend.
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry/donut",
            String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"name\":\"Real One\"}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   void testScriptDispatcher() {
      // Upload modified pastry spec
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-script-openapi.yaml", true);

      ObjectMapper mapper = new ObjectMapper();

      // Check operation with a defined mock (name: 'Eclair Cafe')
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-script/1.0.0/pastry/Eclair Cafe/taste",
            String.class);
      assertEquals(200, response.getStatusCode().value());
      assertEquals("Delicious", response.getBody());

      // Check operation with a defined mock (name: 'Millefeuille')
      response = restTemplate.getForEntity("/rest/pastry-script/1.0.0/pastry/Millefeuille/taste", String.class);
      assertEquals(200, response.getStatusCode().value());
      assertEquals("Awesome", response.getBody());

      // Check operation with an undefined mock (name: 'Dummy')
      response = restTemplate.getForEntity("/rest/pastry-script/1.0.0/pastry/Dummy/taste", String.class);
      assertEquals(200, response.getStatusCode().value());
      assertEquals("Ok", response.getBody());
   }

   @Test
   void testDelay() {
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json", true);

      // Check a delayed mocked operations.
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/PetStore+API/1.0.0/pets?delay=200",
            String.class);
      long mockedResponseTime = System.currentTimeMillis() - startTime;
      // Assert that the response time is greater than the delay and greater that .
      assertTrue(mockedResponseTime >= 200, "mocked response time delayed: " + mockedResponseTime + "ms");
      assertEquals(200, response.getStatusCode().value());

      // Now use a header to specify mock response time.
      HttpHeaders headers = new HttpHeaders();
      headers.set("x-microcks-delay", "400");
      HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

      startTime = System.currentTimeMillis();
      response = restTemplate.exchange("/rest/PetStore+API/1.0.0/pets?delay=200", HttpMethod.GET, requestEntity,
            String.class);
      mockedResponseTime = System.currentTimeMillis() - startTime;
      // Assert that the response time is greater than the delay and greater that .
      assertTrue(mockedResponseTime >= 400, "mocked response time delayed: " + mockedResponseTime + "ms");
      assertEquals(200, response.getStatusCode().value());
   }


   @Test
   void testDelayWithStrategyFixed() {
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json", true);

      // Check a delayed mocked operations with fixed strategy.
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate
            .getForEntity("/rest/PetStore+API/1.0.0/pets?delay=200&delayStrategy=fixed", String.class);
      long mockedResponseTime = System.currentTimeMillis() - startTime;
      // Assert that the response time is greater than the delay and greater that .
      assertTrue(mockedResponseTime >= 200, "mocked response time delayed: " + mockedResponseTime + "ms");
      assertEquals(200, response.getStatusCode().value());

      // Now use a header to specify mock response time with random strategy.
      HttpHeaders headers = new HttpHeaders();
      headers.set("x-microcks-delay", "400");
      headers.set("x-microcks-delay-strategy", "fixed");
      HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

      startTime = System.currentTimeMillis();
      // delay and delayStrategy query params should be ignored in favour of headers. (It's a reason why we set random here).
      response = restTemplate.exchange("/rest/PetStore+API/1.0.0/pets?delay=200&delayStrategy=random", HttpMethod.GET,
            requestEntity, String.class);
      mockedResponseTime = System.currentTimeMillis() - startTime;
      // Assert that the response time is greater than the delay and greater that .
      assertTrue(mockedResponseTime >= 400, "mocked response time delayed: " + mockedResponseTime + "ms");
      assertEquals(200, response.getStatusCode().value());
   }

   @Test
   void testDelayStrategyRandom() {
      Long schedulingDelay = 50l;
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json", true);

      // Check a delayed mocked operations with random strategy.
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate
            .getForEntity("/rest/PetStore+API/1.0.0/pets?delay=200&delayStrategy=random", String.class);
      long mockedResponseTime = System.currentTimeMillis() - startTime;
      // Assert that the response time is between 0 and 200ms (random strategy)
      // Note: we can't be sure that the delay is <= 200 because of JVM scheduling but it's very likely.
      assertTrue(mockedResponseTime >= 0 && mockedResponseTime <= (200 + schedulingDelay),
            "mocked response time delayed: " + mockedResponseTime + "ms");
      assertEquals(200, response.getStatusCode().value());

      // Now use a header to specify mock response time with random strategy.
      HttpHeaders headers = new HttpHeaders();
      headers.set("x-microcks-delay", "400");
      headers.set("x-microcks-delay-strategy", "random");
      HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

      startTime = System.currentTimeMillis();
      // delay and delayStrategy query params should be ignored in favour of headers. (It's a reason why we set fixed here).
      response = restTemplate.exchange("/rest/PetStore+API/1.0.0/pets?delay=200&delayStrategy=fixed", HttpMethod.GET,
            requestEntity, String.class);
      mockedResponseTime = System.currentTimeMillis() - startTime;
      // Assert that the response time is between 0 and the delay.
      // Note: we can't be sure that the delay is <= 400 because of JVM scheduling but it's very likely.
      assertTrue(mockedResponseTime >= 0 && mockedResponseTime <= (400 + schedulingDelay),
            "mocked response time delayed: " + mockedResponseTime + "ms");
      assertEquals(200, response.getStatusCode().value());
   }

   @Test
   void testDelayStrategyRangedRandom() {
      Long schedulingDelay = 50l;
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json", true);

      // Check a delayed mocked operations with random strategy.
      long startTime = System.currentTimeMillis();
      ResponseEntity<String> response = restTemplate
            .getForEntity("/rest/PetStore+API/1.0.0/pets?delay=100&delayStrategy=random-20", String.class);
      long mockedResponseTime = System.currentTimeMillis() - startTime;
      // Assert that the response time is between 80 and 120 (100 + or - 20%).
      assertTrue(mockedResponseTime >= 80 && mockedResponseTime <= (120 + schedulingDelay),
            "mocked response time delayed: " + mockedResponseTime + "ms");
      assertEquals(200, response.getStatusCode().value());

      // Now use a header to specify mock response time with random strategy.
      HttpHeaders headers = new HttpHeaders();
      headers.set("x-microcks-delay", "400");
      headers.set("x-microcks-delay-strategy", "random-20");
      HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
      startTime = System.currentTimeMillis();
      // delay and delayStrategy query params should be ignored in favour of headers. (It's a reason why we set fixed here).
      response = restTemplate.exchange("/rest/PetStore+API/1.0.0/pets?delay=200&delayStrategy=fixed", HttpMethod.GET,
            requestEntity, String.class);
      mockedResponseTime = System.currentTimeMillis() - startTime;
      // Assert that the response time is between 320 and 480 (400 + or - 20%).
      // Note: we can't be sure that the delay is <= 480 because of JVM scheduling but it's very likely.
      assertTrue(mockedResponseTime >= 320 && mockedResponseTime <= (480 + schedulingDelay),
            "mocked response time delayed: " + mockedResponseTime + "ms");
      assertEquals(200, response.getStatusCode().value());
   }

}
