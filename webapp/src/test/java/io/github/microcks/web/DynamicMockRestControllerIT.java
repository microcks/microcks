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
import io.github.microcks.web.dto.GenericResourceServiceDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for the DynamicMockRestController covering CRUD operations on dynamic mock resources.
 * @author laurent
 */
class DynamicMockRestControllerIT extends AbstractBaseIT {

   private static final String SERVICE_NAME = "DynaMock API";
   private static final String SERVICE_VERSION = "1.0";
   private static final String RESOURCE = "order";

   private final ObjectMapper mapper = new ObjectMapper();


   @BeforeEach
   void setupGenericService() {
      // Create the generic resource service if not already present.
      Service existing = serviceRepository.findByNameAndVersion(SERVICE_NAME, SERVICE_VERSION);
      if (existing == null) {
         GenericResourceServiceDTO dto = new GenericResourceServiceDTO();
         dto.setName(SERVICE_NAME);
         dto.setVersion(SERVICE_VERSION);
         dto.setResource(RESOURCE);

         restTestClient.post().uri("/api/services/generic").contentType(MediaType.APPLICATION_JSON).body(dto).exchange()
               .expectStatus().isCreated();
      }
   }

   @Test
   void testCreateResource() throws Exception {
      String body = "{\"productId\": \"ABC123\", \"quantity\": 2, \"price\": 19.99}";

      var response = restTestClient.post()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated()
            .returnResult(String.class);

      assertNotNull(response.getResponseBody());

      JsonNode node = mapper.readTree(response.getResponseBody());
      assertTrue(node.has("id"), "Created resource should contain an 'id' field");
      assertEquals("ABC123", node.get("productId").asText());
      assertEquals(2, node.get("quantity").asInt());
   }

   @Test
   void testCreateResourceWithInvalidJson() {
      String invalidBody = "this is not json";

      restTestClient.post().uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(invalidBody).exchange().expectStatus().isEqualTo(422);
   }

   @Test
   void testCreateResourceForUnknownService() {
      String body = "{\"foo\": \"bar\"}";

      restTestClient.post().uri("/dynarest/UnknownService/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isBadRequest();
   }

   @Test
   void testFindResources() throws Exception {
      // First, create two resources.
      String body1 = "{\"productId\": \"FIND1\", \"quantity\": 1}";
      String body2 = "{\"productId\": \"FIND2\", \"quantity\": 5}";
      restTestClient.post().uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body1).exchange().expectStatus().isCreated();
      restTestClient.post().uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body2).exchange().expectStatus().isCreated();

      // Now list resources.
      var response = restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE).exchange().expectStatus()
            .isOk().returnResult(String.class);

      assertNotNull(response.getResponseBody());

      JsonNode array = mapper.readTree(response.getResponseBody());
      assertTrue(array.isArray(), "Response should be a JSON array");
      assertTrue(array.size() >= 2, "Should have at least 2 resources");
   }

   @Test
   void testFindResourcesWithPagination() throws Exception {
      // Create a few resources for pagination.
      for (int i = 0; i < 3; i++) {
         String body = "{\"productId\": \"PAGE" + i + "\"}";
         restTestClient.post().uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
               .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated();
      }

      // Request with small page size.
      var response = restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "?page=0&size=2")
            .exchange().expectStatus().isOk().returnResult(String.class);

      assertNotNull(response.getResponseBody());

      JsonNode array = mapper.readTree(response.getResponseBody());
      assertTrue(array.isArray());
      assertTrue(array.size() <= 2, "Page size should be respected");
   }

   @Test
   void testGetResourceById() throws Exception {
      // Create a resource first.
      String body = "{\"productId\": \"GET1\", \"quantity\": 10}";
      var createResponse = restTestClient.post()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated()
            .returnResult(String.class);

      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();

      // Now get the resource by id.
      var response = restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .exchange().expectStatus().isOk().returnResult(String.class);

      assertNotNull(response.getResponseBody());

      JsonNode node = mapper.readTree(response.getResponseBody());
      assertEquals("GET1", node.get("productId").asText());
      assertEquals(10, node.get("quantity").asInt());
      assertEquals(resourceId, node.get("id").asText());
   }

   @Test
   void testGetResourceByIdNotFound() {
      restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/nonexistent-id-12345")
            .exchange().expectStatus().isNotFound();
   }

   @Test
   void testUpdateResource() throws Exception {
      // Create a resource.
      String body = "{\"productId\": \"UPD1\", \"quantity\": 3}";
      var createResponse = restTestClient.post()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated()
            .returnResult(String.class);

      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();

      // Update the resource.
      String updatedBody = "{\"productId\": \"UPD1\", \"quantity\": 42}";

      var response = restTestClient.put()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .contentType(MediaType.APPLICATION_JSON).body(updatedBody).exchange().expectStatus().isOk()
            .returnResult(String.class);

      assertNotNull(response.getResponseBody());

      JsonNode node = mapper.readTree(response.getResponseBody());
      assertEquals(42, node.get("quantity").asInt());

      // Verify the update by retrieving the resource.
      var getResponse = restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .exchange().expectStatus().isOk().returnResult(String.class);
      JsonNode retrieved = mapper.readTree(getResponse.getResponseBody());
      assertEquals(42, retrieved.get("quantity").asInt());
   }

   @Test
   void testUpdateResourceWithInvalidJson() throws Exception {
      // Create a resource first.
      String body = "{\"productId\": \"UPD_INV\", \"quantity\": 1}";
      var createResponse = restTestClient.post()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated()
            .returnResult(String.class);

      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();

      // Try to update with invalid JSON.
      restTestClient.put()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .contentType(MediaType.APPLICATION_JSON).body("this is not json").exchange().expectStatus().isEqualTo(422);
   }

   @Test
   void testUpdateResourceNotFound() {
      String updatedBody = "{\"productId\": \"GHOST\", \"quantity\": 99}";

      restTestClient.put()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/nonexistent-id-12345")
            .contentType(MediaType.APPLICATION_JSON).body(updatedBody).exchange().expectStatus().isNotFound();
   }

   @Test
   void testDeleteResource() throws Exception {
      // Create a resource.
      String body = "{\"productId\": \"DEL1\", \"quantity\": 7}";
      var createResponse = restTestClient.post()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated()
            .returnResult(String.class);

      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();

      // Delete the resource.
      restTestClient.delete()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .exchange().expectStatus().isNoContent();

      // Verify it's deleted.
      restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .exchange().expectStatus().isNotFound();
   }

   @Test
   void testDeleteResourceForUnknownService() {
      restTestClient.delete().uri("/dynarest/UnknownService/" + SERVICE_VERSION + "/" + RESOURCE + "/some-id")
            .exchange().expectStatus().isBadRequest();
   }

   @Test
   void testFullCrudLifecycle() throws Exception {
      // CREATE
      String body = "{\"name\": \"Widget\", \"status\": \"new\"}";
      var createResponse = restTestClient.post()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated()
            .returnResult(String.class);
      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();
      assertNotNull(resourceId);

      // READ
      var getResponse = restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .exchange().expectStatus().isOk().returnResult(String.class);
      JsonNode retrieved = mapper.readTree(getResponse.getResponseBody());
      assertEquals("Widget", retrieved.get("name").asText());
      assertEquals("new", retrieved.get("status").asText());

      // UPDATE
      String updatedBody = "{\"name\": \"Widget\", \"status\": \"shipped\"}";

      var updateResponse = restTestClient.put()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .contentType(MediaType.APPLICATION_JSON).body(updatedBody).exchange().expectStatus().isOk()
            .returnResult(String.class);
      JsonNode updated = mapper.readTree(updateResponse.getResponseBody());
      assertEquals("shipped", updated.get("status").asText());

      // LIST - should contain the resource
      var listResponse = restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE).exchange().expectStatus()
            .isOk().returnResult(String.class);
      JsonNode list = mapper.readTree(listResponse.getResponseBody());
      assertTrue(list.isArray());
      assertFalse(list.isEmpty());

      // DELETE
      restTestClient.delete()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .exchange().expectStatus().isNoContent();

      // VERIFY DELETED
      restTestClient.get()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId)
            .exchange().expectStatus().isNotFound();
   }

   @Test
   void testCreateResourceWithDelay() {
      String body = "{\"productId\": \"DELAY1\", \"quantity\": 1}";
      long delay = 200L;

      long startTime = System.currentTimeMillis();
      restTestClient.post()
            .uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "?delay=" + delay)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated();
      long elapsed = System.currentTimeMillis() - startTime;

      assertTrue(elapsed >= delay, "Response should be delayed by at least " + delay + "ms, was " + elapsed + "ms");
   }

   @Test
   void testCreateResourceWithOperationDefaultDelay() {
      // First, get the service and set default delay and strategy on its POST operation
      Service service = serviceRepository.findByNameAndVersion(SERVICE_NAME, SERVICE_VERSION);
      for (Operation operation : service.getOperations()) {
         if (("POST /" + RESOURCE).equals(operation.getName())) {
            operation.setDefaultDelay(200L);
            operation.setDefaultDelayStrategy("FIXED");
         }
      }
      serviceRepository.save(service);

      String body = "{\"productId\": \"DELAY2\", \"quantity\": 1}";

      long startTime = System.currentTimeMillis();
      restTestClient.post().uri("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated();
      long elapsed = System.currentTimeMillis() - startTime;

      assertTrue(elapsed >= 200L, "Response should be delayed by at least 200ms, was " + elapsed + "ms");

      // Cleanup the operation state
      for (Operation operation : service.getOperations()) {
         if (("POST /" + RESOURCE).equals(operation.getName())) {
            operation.setDefaultDelay(null);
            operation.setDefaultDelayStrategy(null);
         }
      }
      serviceRepository.save(service);
   }

   @Test
   void testServiceNameWithEncodedSpaces() {
      // The service name contains spaces, test with '+' encoding.
      String body = "{\"productId\": \"ENC1\"}";
      var response = restTestClient.post().uri("/dynarest/DynaMock+API/" + SERVICE_VERSION + "/" + RESOURCE)
            .contentType(MediaType.APPLICATION_JSON).body(body).exchange().expectStatus().isCreated()
            .returnResult(String.class);

      assertNotNull(response.getResponseBody());
   }

   /** Encode service name for URL path (spaces as +). */
   private String encodedServiceName() {
      return SERVICE_NAME.replace(" ", "+");
   }
}

