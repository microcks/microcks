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
import io.github.microcks.domain.Service;
import io.github.microcks.web.dto.GenericResourceServiceDTO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

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

         HttpHeaders headers = new HttpHeaders();
         headers.setContentType(MediaType.APPLICATION_JSON);

         HttpEntity<GenericResourceServiceDTO> request = new HttpEntity<>(dto, headers);
         EntityExchangeResult<String> response = postForEntity("/api/services/generic", request, String.class);
         assertEquals(201, response.getStatus().value(), "Generic REST service should be created");
      }
   }

   @Test
   void testCreateResource() throws Exception {
      String body = "{\"productId\": \"ABC123\", \"quantity\": 2, \"price\": 19.99}";

      EntityExchangeResult<String> response = postForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body, String.class);

      assertEquals(201, response.getStatus().value());
      assertNotNull(response.getResponseBody());

      JsonNode node = mapper.readTree(response.getResponseBody());
      assertTrue(node.has("id"), "Created resource should contain an 'id' field");
      assertEquals("ABC123", node.get("productId").asText());
      assertEquals(2, node.get("quantity").asInt());
   }

   @Test
   void testCreateResourceWithInvalidJson() {
      String invalidBody = "this is not json";

      EntityExchangeResult<String> response = postForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, invalidBody, String.class);

      assertEquals(422, response.getStatus().value());
   }

   @Test
   void testCreateResourceForUnknownService() {
      String body = "{\"foo\": \"bar\"}";

      EntityExchangeResult<String> response = postForEntity(
            "/dynarest/UnknownService/" + SERVICE_VERSION + "/" + RESOURCE, body, String.class);

      assertEquals(400, response.getStatus().value());
   }

   @Test
   void testFindResources() throws Exception {
      // First, create two resources.
      String body1 = "{\"productId\": \"FIND1\", \"quantity\": 1}";
      String body2 = "{\"productId\": \"FIND2\", \"quantity\": 5}";
      postForEntity("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body1, String.class);
      postForEntity("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body2, String.class);

      // Now list resources.
      EntityExchangeResult<String> response = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, String.class);

      assertEquals(200, response.getStatus().value());
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
         postForEntity("/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body,
               String.class);
      }

      // Request with small page size.
      EntityExchangeResult<String> response = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "?page=0&size=2",
            String.class);

      assertEquals(200, response.getStatus().value());
      assertNotNull(response.getResponseBody());

      JsonNode array = mapper.readTree(response.getResponseBody());
      assertTrue(array.isArray());
      assertTrue(array.size() <= 2, "Page size should be respected");
   }

   @Test
   void testGetResourceById() throws Exception {
      // Create a resource first.
      String body = "{\"productId\": \"GET1\", \"quantity\": 10}";
      EntityExchangeResult<String> createResponse = postForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body, String.class);
      assertEquals(201, createResponse.getStatus().value());

      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();

      // Now get the resource by id.
      EntityExchangeResult<String> response = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            String.class);

      assertEquals(200, response.getStatus().value());
      assertNotNull(response.getResponseBody());

      JsonNode node = mapper.readTree(response.getResponseBody());
      assertEquals("GET1", node.get("productId").asText());
      assertEquals(10, node.get("quantity").asInt());
      assertEquals(resourceId, node.get("id").asText());
   }

   @Test
   void testGetResourceByIdNotFound() {
      EntityExchangeResult<String> response = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/nonexistent-id-12345",
            String.class);

      assertEquals(404, response.getStatus().value());
   }

   @Test
   void testUpdateResource() throws Exception {
      // Create a resource.
      String body = "{\"productId\": \"UPD1\", \"quantity\": 3}";
      EntityExchangeResult<String> createResponse = postForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body, String.class);
      assertEquals(201, createResponse.getStatus().value());

      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();

      // Update the resource.
      String updatedBody = "{\"productId\": \"UPD1\", \"quantity\": 42}";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> requestEntity = new HttpEntity<>(updatedBody, headers);

      EntityExchangeResult<String> response = exchange(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            HttpMethod.PUT, requestEntity, String.class);

      assertEquals(200, response.getStatus().value());
      assertNotNull(response.getResponseBody());

      JsonNode node = mapper.readTree(response.getResponseBody());
      assertEquals(42, node.get("quantity").asInt());

      // Verify the update by retrieving the resource.
      EntityExchangeResult<String> getResponse = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            String.class);
      assertEquals(200, getResponse.getStatus().value());
      JsonNode retrieved = mapper.readTree(getResponse.getResponseBody());
      assertEquals(42, retrieved.get("quantity").asInt());
   }

   @Test
   void testUpdateResourceWithInvalidJson() throws Exception {
      // Create a resource first.
      String body = "{\"productId\": \"UPD_INV\", \"quantity\": 1}";
      EntityExchangeResult<String> createResponse = postForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body, String.class);
      assertEquals(201, createResponse.getStatus().value());

      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();

      // Try to update with invalid JSON.
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> requestEntity = new HttpEntity<>("this is not json", headers);

      EntityExchangeResult<String> response = exchange(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            HttpMethod.PUT, requestEntity, String.class);

      assertEquals(422, response.getStatus().value());
   }

   @Test
   void testUpdateResourceNotFound() {
      String updatedBody = "{\"productId\": \"GHOST\", \"quantity\": 99}";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> requestEntity = new HttpEntity<>(updatedBody, headers);

      EntityExchangeResult<String> response = exchange(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/nonexistent-id-12345",
            HttpMethod.PUT, requestEntity, String.class);

      assertEquals(404, response.getStatus().value());
   }

   @Test
   void testDeleteResource() throws Exception {
      // Create a resource.
      String body = "{\"productId\": \"DEL1\", \"quantity\": 7}";
      EntityExchangeResult<String> createResponse = postForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body, String.class);
      assertEquals(201, createResponse.getStatus().value());

      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();

      // Delete the resource.
      EntityExchangeResult<String> response = exchange(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            HttpMethod.DELETE, null, String.class);

      assertEquals(204, response.getStatus().value());

      // Verify it's deleted.
      EntityExchangeResult<String> getResponse = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            String.class);
      assertEquals(404, getResponse.getStatus().value());
   }

   @Test
   void testDeleteResourceForUnknownService() {
      EntityExchangeResult<String> response = exchange(
            "/dynarest/UnknownService/" + SERVICE_VERSION + "/" + RESOURCE + "/some-id", HttpMethod.DELETE, null,
            String.class);

      assertEquals(400, response.getStatus().value());
   }

   @Test
   void testFullCrudLifecycle() throws Exception {
      // CREATE
      String body = "{\"name\": \"Widget\", \"status\": \"new\"}";
      EntityExchangeResult<String> createResponse = postForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, body, String.class);
      assertEquals(201, createResponse.getStatus().value());
      JsonNode created = mapper.readTree(createResponse.getResponseBody());
      String resourceId = created.get("id").asText();
      assertNotNull(resourceId);

      // READ
      EntityExchangeResult<String> getResponse = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            String.class);
      assertEquals(200, getResponse.getStatus().value());
      JsonNode retrieved = mapper.readTree(getResponse.getResponseBody());
      assertEquals("Widget", retrieved.get("name").asText());
      assertEquals("new", retrieved.get("status").asText());

      // UPDATE
      String updatedBody = "{\"name\": \"Widget\", \"status\": \"shipped\"}";
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      HttpEntity<String> updateEntity = new HttpEntity<>(updatedBody, headers);

      EntityExchangeResult<String> updateResponse = exchange(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            HttpMethod.PUT, updateEntity, String.class);
      assertEquals(200, updateResponse.getStatus().value());
      JsonNode updated = mapper.readTree(updateResponse.getResponseBody());
      assertEquals("shipped", updated.get("status").asText());

      // LIST - should contain the resource
      EntityExchangeResult<String> listResponse = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE, String.class);
      assertEquals(200, listResponse.getStatus().value());
      JsonNode list = mapper.readTree(listResponse.getResponseBody());
      assertTrue(list.isArray());
      assertFalse(list.isEmpty());

      // DELETE
      EntityExchangeResult<String> deleteResponse = exchange(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            HttpMethod.DELETE, null, String.class);
      assertEquals(204, deleteResponse.getStatus().value());

      // VERIFY DELETED
      EntityExchangeResult<String> deletedGetResponse = getForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "/" + resourceId,
            String.class);
      assertEquals(404, deletedGetResponse.getStatus().value());
   }

   @Test
   void testCreateResourceWithDelay() {
      String body = "{\"productId\": \"DELAY1\", \"quantity\": 1}";
      long delay = 200L;

      long startTime = System.currentTimeMillis();
      EntityExchangeResult<String> response = postForEntity(
            "/dynarest/" + encodedServiceName() + "/" + SERVICE_VERSION + "/" + RESOURCE + "?delay=" + delay, body,
            String.class);
      long elapsed = System.currentTimeMillis() - startTime;

      assertEquals(201, response.getStatus().value());
      assertTrue(elapsed >= delay, "Response should be delayed by at least " + delay + "ms, was " + elapsed + "ms");
   }

   @Test
   void testServiceNameWithEncodedSpaces() {
      // The service name contains spaces, test with '+' encoding.
      String body = "{\"productId\": \"ENC1\"}";
      EntityExchangeResult<String> response = postForEntity(
            "/dynarest/DynaMock+API/" + SERVICE_VERSION + "/" + RESOURCE, body, String.class);

      assertEquals(201, response.getStatus().value());
      assertNotNull(response.getResponseBody());
   }

   /** Encode service name for URL path (spaces as +). */
   private String encodedServiceName() {
      return SERVICE_NAME.replace(" ", "+");
   }
}

