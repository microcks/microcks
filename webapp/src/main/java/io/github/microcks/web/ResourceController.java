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
import io.github.microcks.util.SafeLogger;
import io.github.microcks.util.openapi.OpenAPISchemaBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static io.github.microcks.web.DynamicMockRestController.ID_FIELD;

/**
 * A controller for distributing or generating resources associated to services mocked within Microcks.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class ResourceController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(ResourceController.class);

   private static final String SWAGGER_20 = "swagger_20";
   private static final String OPENAPI_30 = "openapi_30";

   private final ResourceRepository resourceRepository;
   private final ServiceRepository serviceRepository;
   private final GenericResourceRepository genericResourceRepository;


   /**
    * Create a new ResourceController with mandatory components.
    * @param resourceRepository        The repository to access resource definitions.
    * @param serviceRepository         The repository to access service definitions.
    * @param genericResourceRepository The repository to access generic resource definitions.
    */
   public ResourceController(ResourceRepository resourceRepository, ServiceRepository serviceRepository,
         GenericResourceRepository genericResourceRepository) {
      this.resourceRepository = resourceRepository;
      this.serviceRepository = serviceRepository;
      this.genericResourceRepository = genericResourceRepository;
   }

   @GetMapping(value = "/resources/{name}")
   public ResponseEntity<Object> getResourceByName(@PathVariable("name") String name, HttpServletRequest request) {
      name = URLDecoder.decode(name, StandardCharsets.UTF_8);
      log.info("Requesting resource named {}", name);

      List<Resource> resources = resourceRepository.findByName(name);
      if (!resources.isEmpty()) {
         Optional<Resource> resourceOpt = resources.stream().filter(Resource::isMainArtifact).findFirst();
         return resourceOpt.map(this::responseWithResource).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
      }
      return new ResponseEntity<>(HttpStatus.NOT_FOUND);
   }

   @GetMapping(value = "/resources/id/{id}")
   public ResponseEntity<Object> getResourceById(@PathVariable("id") String id, HttpServletRequest request) {
      log.info("Requesting resource with id {}", id);
      Optional<Resource> resourceOpt = resourceRepository.findById(id);

      return resourceOpt.map(this::responseWithResource).orElseGet(() -> new ResponseEntity<>(HttpStatus.NOT_FOUND));
   }

   @GetMapping(value = "/resources/service/{serviceId}")
   public List<Resource> getServiceResources(@PathVariable("serviceId") String serviceId) {
      log.debug("Request resources for service {}", serviceId);
      return resourceRepository.findByServiceId(serviceId);
   }

   @GetMapping(value = "/resources/{serviceId}/{resourceType}")
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

   private ResponseEntity<Object> responseWithResource(Resource resource) {
      String extension = resource.getName().substring(resource.getName().lastIndexOf('.'));
      HttpHeaders headers = new HttpHeaders();

      if (".json".equals(extension)) {
         headers.setContentType(MediaType.APPLICATION_JSON);
      } else if (".yaml".equals(extension) || ".yml".equals(extension)) {
         headers.set("Content-Type", "text/yaml");
         headers.setContentDisposition(ContentDisposition.builder("inline").filename(resource.getName()).build());
      } else if (".wsdl".equals(extension) || ".xsd".equals(extension)) {
         headers.setContentType(MediaType.TEXT_XML);
      } else if (".avsc".equals(extension)) {
         headers.setContentType(MediaType.APPLICATION_JSON);
      }
      return new ResponseEntity<>(resource.getContent(), headers, HttpStatus.OK);
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
