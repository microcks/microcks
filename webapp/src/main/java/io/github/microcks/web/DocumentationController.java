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

import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.util.MockRepositoryImporterFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.stream.Stream;

/**
 * A controller for generating API documentation for contracts mocked within Microcks.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class DocumentationController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(DocumentationController.class);

   private static final String RESOURCE_URL = "{RESOURCE_URL}";

   final ResourceRepository resourceRepository;

   /**
    * Build a new DocumentationCoontroller with a resource repository.
    * @param resourceRepository Repostiory to access resource.
    */
   public DocumentationController(ResourceRepository resourceRepository) {
      this.resourceRepository = resourceRepository;
   }

   @GetMapping(value = "/documentation/{name}/{resourceType}")
   public ResponseEntity<byte[]> execute(@PathVariable("name") String name,
         @PathVariable("resourceType") String resourceType) {
      log.info("Requesting {} documentation for resource {}", resourceType, name);

      // Prepare HttpHeaders.
      InputStream stream = null;
      HttpHeaders headers = new HttpHeaders();
      org.springframework.core.io.Resource template = null;

      // Get the correct template depending on resource type.
      if (ResourceType.OPEN_API_SPEC.toString().equals(resourceType)
            || ResourceType.SWAGGER.toString().equals(resourceType)) {
         template = new ClassPathResource("templates/redoc.html");
         headers.setContentType(MediaType.TEXT_HTML);
      } else if (ResourceType.ASYNC_API_SPEC.toString().equals(resourceType)) {

         Resource resource = resourceRepository.findByName(name);
         if (resource.getContent().contains("asyncapi: 3") || resource.getContent().contains("\"asyncapi\": \"3")
               || resource.getContent().contains("'asyncapi': '3")) {
            template = new ClassPathResource("templates/asyncapi-v3.html");
         } else {
            template = new ClassPathResource("templates/asyncapi.html");
         }
         headers.setContentType(MediaType.TEXT_HTML);
      }

      if (template != null) {
         try {
            stream = template.getInputStream();
         } catch (IOException e) {
            log.error("IOException while reading template {}", template.getDescription(), e);
         }

         // Now process the stream, replacing patterns by value.
         BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
         StringWriter writer = new StringWriter();

         try (Stream<String> lines = reader.lines()) {
            lines.map(line -> replaceInLine(line, name)).forEach(line -> writer.write(line + "\n"));
         }
         return new ResponseEntity<>(writer.toString().getBytes(), headers, HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }

   private String replaceInLine(String line, String resourceName) {
      line = line.replace(RESOURCE_URL, "/api/resources/" + resourceName);
      return line;
   }
}
