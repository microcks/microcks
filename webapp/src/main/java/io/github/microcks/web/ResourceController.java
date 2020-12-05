/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.web;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * A controller for distributing or genetation resources associated to services mocked within Microcks.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class ResourceController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ResourceController.class);

   private static final String SWAGGER_20 = "swagger_20";
   private static final String OPENAPI_30 = "openapi_30";

   private static final String SERVICE_PATTERN = "\\{service\\}";
   private static final String VERSION_PATTERN = "\\{version\\}";
   private static final String RESOURCE_PATTERN = "\\{resource\\}";

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private ServiceRepository serviceRepository;


   @RequestMapping(value = "/resources/{name}", method = RequestMethod.GET)
   public ResponseEntity<?> execute(
         @PathVariable("name") String name,
         HttpServletRequest request
   ) {
      String extension = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('.'));

      log.info("Requesting resource named " + name);

      Resource resource = resourceRepository.findByName(name);
      if (resource != null){
         HttpHeaders headers = new HttpHeaders();

         if (".json".equals(extension)) {
            headers.set("Content-Type", "application/json");
         } else if (".yaml".equals(extension) || ".yml".equals(extension)) {
            headers.set("Content-Type", "text/yaml");
         } else if (".wsdl".equals(extension) || ".xsd".equals(extension)) {
            headers.set("Content-Type", "text/xml");
         } else if (".avsc".equals(extension)) {
            headers.set("Content-Type", "application/json");
         }
         return new ResponseEntity<Object>(resource.getContent(), headers, HttpStatus.OK);
      }
      return new ResponseEntity<Object>(HttpStatus.NOT_FOUND);
   }

   @RequestMapping(value = "/resources/service/{serviceId}", method = RequestMethod.GET)
   public List<Resource> getServiceResources(@PathVariable("serviceId") String serviceId) {
      log.debug("Request resources for service {}", serviceId);
      return resourceRepository.findByServiceId(serviceId);
   }

   @RequestMapping(value = "/resources/{serviceId}/{resourceType}", method = RequestMethod.GET)
   public ResponseEntity<byte[]> getServiceResource(
         @PathVariable("serviceId") String serviceId,
         @PathVariable("resourceType") String resourceType,
         HttpServletResponse response
   ) {
      log.info("Requesting {} resource for service {}", resourceType, serviceId);

      Service service = serviceRepository.findById(serviceId).orElse(null);
      if (service != null && ServiceType.GENERIC_REST.equals(service.getType())) {
         // Prepare HttpHeaders.
         InputStream stream = null;
         String resource = findResource(service);
         HttpHeaders headers = new HttpHeaders();

         // Get the correct template depending on resource type.
         if (SWAGGER_20.equals(resourceType)) {
            org.springframework.core.io.Resource template = new ClassPathResource("templates/swagger-2.0.json");
            try {
               stream = template.getInputStream();
            } catch (IOException e) {
               log.error("IOException while reading swagger-2.0.json template", e);
            }
            headers.setContentType(MediaType.APPLICATION_JSON);
         } else if (OPENAPI_30.equals(resourceType)) {
            org.springframework.core.io.Resource template = new ClassPathResource("templates/openapi-3.0.yaml");
            try {
               stream = template.getInputStream();
            } catch (IOException e) {
               log.error("IOException while reading openapi-3.0.yaml template", e);
            }
            headers.set("Content-Type", "text/yaml");
         }

         // Now process the stream, replacing patterns by value.
         if (stream != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
            StringWriter writer = new StringWriter();

            try (Stream<String> lines = reader.lines()) {
               lines.map(line -> replaceInLine(line, service, resource)).forEach(line -> writer.write(line + "\n"));
            }
            return new ResponseEntity<>(writer.toString().getBytes(), headers, HttpStatus.OK);
         }
      }
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
   }

   private String findResource(Service service) {
      for (Operation operation : service.getOperations()) {
         if (operation.getName().startsWith("GET /") && !operation.getName().endsWith("/:id")) {
            return operation.getName().substring("GET /".length());
         }
      }
      return null;
   }

   private String replaceInLine(String line, Service service, String resource) {
      line = line.replaceAll(SERVICE_PATTERN, service.getName());
      line = line.replaceAll(VERSION_PATTERN, service.getVersion());
      line = line.replaceAll(RESOURCE_PATTERN, resource);
      return line;
   }
}
