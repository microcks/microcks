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

import io.github.microcks.domain.GenericResource;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.repository.GenericResourceRepository;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.ResourceUtil;
import io.github.microcks.util.openapi.OpenAPISchemaBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.github.microcks.web.DynamicMockRestController.ID_FIELD;

/**
 * A controller for distributing or generating resources associated to services mocked within Microcks.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class ResourceController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ResourceController.class);

   private static final String SWAGGER_20 = "swagger_20";
   private static final String OPENAPI_30 = "openapi_30";

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private GenericResourceRepository genericResourceRepository;


   @RequestMapping(value = "/resources/{name}", method = RequestMethod.GET)
   public ResponseEntity<?> execute(@PathVariable("name") String name, HttpServletRequest request) {
      String extension = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('.'));

      try {
         name = URLDecoder.decode(name, StandardCharsets.UTF_8.toString());
      } catch (UnsupportedEncodingException e) {
         log.error("Exception while decoding resource name: {}", e.getMessage());
      }
      log.info("Requesting resource named " + name);

      Resource resource = resourceRepository.findByName(name);
      if (resource != null) {
         HttpHeaders headers = new HttpHeaders();

         if (".json".equals(extension)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
         } else if (".yaml".equals(extension) || ".yml".equals(extension)) {
            headers.set("Content-Type", "text/yaml");
            headers.setContentDisposition(ContentDisposition.builder("inline").filename(name).build());
         } else if (".wsdl".equals(extension) || ".xsd".equals(extension)) {
            headers.setContentType(MediaType.TEXT_XML);
         } else if (".avsc".equals(extension)) {
            headers.setContentType(MediaType.APPLICATION_JSON);
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
   public ResponseEntity<byte[]> getServiceResource(@PathVariable("serviceId") String serviceId,
         @PathVariable("resourceType") String resourceType, HttpServletResponse response) {
      log.info("Requesting {} resource for service {}", resourceType, serviceId);

      Service service = serviceRepository.findById(serviceId).orElse(null);
      if (service != null && ServiceType.GENERIC_REST.equals(service.getType())) {
         // Check if there's one reference resource...
         JsonNode referenceSchema = null;
         List<GenericResource> genericResources = genericResourceRepository.findReferencesByServiceId(serviceId);
         if (genericResources != null && !genericResources.isEmpty()) {
            try {
               Document reference = genericResources.get(0).getPayload();
               reference.append(ID_FIELD, genericResources.get(0).getId());
               referenceSchema = OpenAPISchemaBuilder.buildTypeSchemaFromJson(reference.toJson());
            } catch (Exception e) {
               log.warn("Exception while building reference schema", e);
            }
         }

         // Prepare HttpHeaders.
         InputStream stream = null;
         String resource = findResource(service);
         HttpHeaders headers = new HttpHeaders();

         // Get the correct template depending on resource type.
         String templatePath = null;
         if (SWAGGER_20.equals(resourceType)) {
            templatePath = "templates/swagger-2.0.yaml";
         } else if (OPENAPI_30.equals(resourceType)) {
            templatePath = "templates/openapi-3.0.yaml";
         }

         // Read the template and set headers.
         try {
            stream = ResourceUtil.getClasspathResource(templatePath);
         } catch (IOException e) {
            log.error("IOException while reading {} template: {}", templatePath, e.getMessage());
         }
         headers.set("Content-Type", "text/yaml");

         // Now process the stream, replacing patterns by value.
         if (stream != null) {
            String result = ResourceUtil.replaceTemplatesInSpecStream(stream, service, resource, referenceSchema, null);

            return new ResponseEntity<>(result.getBytes(), headers, HttpStatus.OK);
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
}
