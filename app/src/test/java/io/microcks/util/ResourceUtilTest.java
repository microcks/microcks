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
package io.github.microcks.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.microcks.domain.Service;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test for ResourceUtil helper class.
 */
class ResourceUtilTest {

   @Test
   void replaceTemplatesInSpecStream() throws Exception {
      // Arrange
      Service service = new Service();
      service.setName("TestService");
      service.setVersion("1.0");

      String resource = "TestResource";

      ObjectMapper mapper = new ObjectMapper();
      JsonNode referenceSchema = mapper.readTree("{\"type\":\"string\"}");

      String referencePayload = "{\"data\":\"TestPayload\"}";

      String template = "{service} {version} {resource} {resourceSchema} {reference}";
      InputStream stream = new ByteArrayInputStream(template.getBytes());

      // Act
      String result = ResourceUtil.replaceTemplatesInSpecStream(stream, service, resource, referenceSchema,
            referencePayload);

      // Assert
      String expected = """
            TestService 1.0 TestResource type: string {"data":"TestPayload"}
            """;
      assertEquals(expected, result);
   }

   @Test
   void replaceTemplatesInSpecOpenApi() throws IOException {
      // Arrange
      Service service = new Service();
      service.setName("Book Service");
      service.setVersion("1.0.0");

      String resource = "Book";

      ObjectMapper mapper = new ObjectMapper();
      JsonNode referenceSchema = mapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "title": {
                      "type": "string"
                    },
                    "author": {
                      "type": "string"
                    }
                  }
                }
            """);

      String referencePayload = "";

      InputStream template = ResourceUtil.getClasspathResource("templates/openapi-3.0.yaml");

      // Act
      String result = ResourceUtil.replaceTemplatesInSpecStream(template, service, resource, referenceSchema,
            referencePayload);

      // Assert
      String expected = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
            Thread.currentThread().getContextClassLoader().getResourceAsStream("filled-templates/openapi-3.0.yaml"))))
                  .lines().collect(Collectors.joining("\n"));


      assertEquals(expected, result);
   }

   @Test
   void replaceTemplatesInSpecAsyncApi() throws IOException {
      // Arrange
      Service service = new Service();
      service.setName("Book Service");
      service.setVersion("1.0.0");

      String resource = "Book";

      ObjectMapper mapper = new ObjectMapper();
      JsonNode referenceSchema = mapper.readTree("""
                {
                  "type": "object",
                  "properties": {
                    "title": {
                      "type": "string"
                    },
                    "author": {
                      "type": "string"
                    }
                  }
                }
            """);

      String referencePayload = new ObjectMapper().writeValueAsString(mapper.readTree("""
                {
                  "title": "Example Title",
                  "author": "Example Author",
                  "isbn": "Example ISBN"
                }
            """));


      InputStream template = ResourceUtil.getClasspathResource("templates/asyncapi-2.4.yaml");

      // Act
      String result = ResourceUtil.replaceTemplatesInSpecStream(template, service, resource, referenceSchema,
            referencePayload);

      // Assert
      String expected = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
            Thread.currentThread().getContextClassLoader().getResourceAsStream("filled-templates/asyncapi-2.4.yaml"))))
                  .lines().collect(Collectors.joining("\n"));

      assertEquals(expected, result);
   }

}
