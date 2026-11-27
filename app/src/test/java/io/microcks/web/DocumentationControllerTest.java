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

import io.github.microcks.domain.ResourceType;
import io.github.microcks.repository.ResourceRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class DocumentationControllerTest {

   private static final String OPEN_API = ResourceType.OPEN_API_SPEC.toString();

   @Mock
   private ResourceRepository resourceRepository;

   @InjectMocks
   private DocumentationController sut;

   @Test
   void shouldEncodeResourceNameInSpecUrl() {
      ResponseEntity<byte[]> response = sut.getDocumentationByResourceName("API Pastry - 2.0-2.0.0.yaml", OPEN_API);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(body(response)).contains("\"/api/resources/API%20Pastry%20-%202.0-2.0.0.yaml\"")
            .doesNotContain("API Pastry - 2.0-2.0.0.yaml\"");
   }

   @Test
   void shouldEncodeQuotesInResourceName() {
      ResponseEntity<byte[]> response = sut.getDocumentationByResourceName("a\"b.yaml", OPEN_API);

      assertThat(body(response)).contains("\"/api/resources/a%22b.yaml\"").doesNotContain("a\"b.yaml");
   }

   @Test
   void shouldEncodeQuotesInResourceId() {
      ResponseEntity<byte[]> response = sut.getDocumentationByResourceId("x\"y", OPEN_API);

      assertThat(body(response)).contains("\"/api/resources/id/x%22y\"").doesNotContain("x\"y");
   }

   private static String body(ResponseEntity<byte[]> response) {
      return new String(response.getBody(), StandardCharsets.UTF_8);
   }
}
