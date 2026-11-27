/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.web;

import io.microcks.domain.ResourceType;
import io.microcks.repository.ResourceRepository;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test case for DocumentationController URL encoding of the spec resource.
 * @author laurent
 */
class DocumentationControllerTest {

   private static final String OPEN_API = ResourceType.OPEN_API_SPEC.toString();

   private final DocumentationController sut = new DocumentationController(Mockito.mock(ResourceRepository.class));

   @Test
   void shouldEncodeResourceNameInSpecUrl() {
      Response response = sut.getDocumentationByResourceName("API Pastry - 2.0-2.0.0.yaml", OPEN_API);

      assertEquals(200, response.getStatus());
      assertTrue(body(response).contains("\"/api/resources/API%20Pastry%20-%202.0-2.0.0.yaml\""));
      assertFalse(body(response).contains("API Pastry - 2.0-2.0.0.yaml\""));
   }

   @Test
   void shouldEncodeQuotesInResourceName() {
      Response response = sut.getDocumentationByResourceName("a\"b.yaml", OPEN_API);

      assertTrue(body(response).contains("\"/api/resources/a%22b.yaml\""));
      assertFalse(body(response).contains("a\"b.yaml"));
   }

   @Test
   void shouldEncodeQuotesInResourceId() {
      Response response = sut.getDocumentationByResourceId("x\"y", OPEN_API);

      assertTrue(body(response).contains("\"/api/resources/id/x%22y\""));
      assertFalse(body(response).contains("x\"y"));
   }

   private static String body(Response response) {
      return new String((byte[]) response.getEntity(), StandardCharsets.UTF_8);
   }
}
