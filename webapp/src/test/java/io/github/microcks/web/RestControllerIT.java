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
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.ServiceRepository;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.comparator.ArraySizeComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test case for all the Rest mock controller.
 * @author laurent
 */
public class RestControllerIT extends AbstractBaseIT {

   @Autowired
   private ServiceRepository serviceRepository;

   @SpyBean
   private RestController restController;

   @Test
   public void testOpenAPIMocking() {
      // Upload PetStore reference artifact.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/petstore-openapi.json", true);

      // Check its different mocked operations.
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/PetStore+API/1.0.0/pets", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("[4]", response.getBody(), new ArraySizeComparator(JSONCompareMode.LENIENT));
         JSONAssert.assertEquals("[{\"id\":1,\"name\":\"Zaza\",\"tag\":\"cat\"},{\"id\":2,\"name\":\"Tigresse\",\"tag\":\"cat\"},{\"id\":3,\"name\":\"Maki\",\"tag\":\"cat\"},{\"id\":4,\"name\":\"Toufik\",\"tag\":\"cat\"}]",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      response = restTemplate.getForEntity("/rest/PetStore+API/1.0.0/pets/1", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"id\":1,\"name\":\"Zaza\",\"tag\":\"cat\"}",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   public void testSwaggerMocking() {
      // Upload Beer Catalog API swagger and then Postman collection artifacts.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-swagger.json", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-collection.json", false);

      // Check its different mocked operations.
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/Beer+Catalog+API/0.9/beer?page=0", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("[3]", response.getBody(), new ArraySizeComparator(JSONCompareMode.LENIENT));
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      response = restTemplate.getForEntity("/rest/Beer+Catalog+API/0.9/beer/Weissbier", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\n" +
                     "    \"name\": \"Weissbier\",\n" +
                     "    \"country\": \"Germany\",\n" +
                     "    \"type\": \"Wheat\",\n" +
                     "    \"rating\": 4.1,\n" +
                     "    \"status\": \"out_of_stock\"\n" +
                     "}",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   public void testFallbackMatchingWithRegex() {
      // Upload modified pastry spec
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-details-openapi.yaml", true);

      ObjectMapper mapper = new ObjectMapper();

      // Check operation with a defined mock (name: 'Millefeuille')
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-details/1.0.0/pastry/Millefeuille/details", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode details = mapper.readTree(response.getBody());
         String description = details.get("description").asText();
         assertTrue(description.startsWith("Detail -"));
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check operation with an undefined defined mock (name: 'Dummy'), should use fallback dispatching based on regular expression matching
      response = restTemplate.getForEntity("/rest/pastry-details/1.0.0/pastry/Dummy/details", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode details = mapper.readTree(response.getBody());
         String description = details.get("description").asText();
         assertTrue(description.startsWith("Detail -"));
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   public void testProxy() {
      // Upload pastry-with-proxy spec
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-proxy-openapi.yaml", true);

      // Set real port to the dispatcher
      Service service = serviceRepository.findByNameAndVersion("pastry-proxy", "1.0.0");
      Operation op = service.getOperations().stream().filter(o -> o.getName().endsWith("GET /pastry")).findFirst().orElseThrow();
      op.setDispatcherRules(op.getDispatcherRules().replaceFirst("http://localhost", getServerUrl()));
      serviceRepository.save(service);

      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry", String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\"name\":\"Real One\"}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   public void testProxyWithEqualsOriginAndExternalUrls() {
      // Upload pastry-with-proxy spec
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-proxy-openapi.yaml", true);

      // Set original URL to the dispatcher
      Service service = serviceRepository.findByNameAndVersion("pastry-proxy", "1.0.0");
      Operation op = service.getOperations().stream().filter(o -> o.getName().endsWith("GET /pastry")).findFirst().orElseThrow();
      op.setDispatcherRules(op.getDispatcherRules().replaceFirst("http://localhost", getServerUrl()).replaceFirst("real-pastry", "pastry"));
      serviceRepository.save(service);

      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry", String.class);
      assertEquals(200, response.getStatusCode().value());
      verify(restController, times(1)).execute(any(), any(), any(), any(), any(), any(), any());
   }

   @Test
   public void testProxyWithHttpError() {
      // Upload pastry-with-proxy spec
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-proxy-openapi.yaml", true);

      // Broke external URL in the dispatcher
      Service service = serviceRepository.findByNameAndVersion("pastry-proxy", "1.0.0");
      Operation op = service.getOperations().stream().filter(o -> o.getName().endsWith("GET /pastry")).findFirst().orElseThrow();
      op.setDispatcherRules(op.getDispatcherRules().replaceFirst("http://localhost", getServerUrl()).replaceFirst("real-pastry", "not-found"));
      serviceRepository.save(service);

      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-proxy/1.0.0/pastry", String.class);
      assertEquals(404, response.getStatusCode().value());
   }
}
