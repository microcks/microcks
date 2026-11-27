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
package io.microcks.web;

import io.microcks.domain.GenericResource;
import io.microcks.domain.Operation;
import io.microcks.domain.Resource;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.repository.GenericResourceRepository;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.util.ResourceUtil;
import io.microcks.util.SafeLogger;
import io.microcks.artifact.openapi.OpenAPISchemaBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.bson.Document;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * A controller for distributing or generating resources associated to services mocked within Microcks.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ResourceController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(ResourceController.class);

   private static final String ID_FIELD = "id";
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
   @Inject
   public ResourceController(ResourceRepository resourceRepository, ServiceRepository serviceRepository,
         GenericResourceRepository genericResourceRepository) {
      this.resourceRepository = resourceRepository;
      this.serviceRepository = serviceRepository;
      this.genericResourceRepository = genericResourceRepository;
   }

   @GET
   @Path("/resources/{name}")
   public Response getResourceByName(@PathParam("name") String name) {
      name = URLDecoder.decode(name, StandardCharsets.UTF_8);
      log.info("Requesting resource named {}", name);

      List<Resource> resources = resourceRepository.findByName(name);
      if (!resources.isEmpty()) {
         Optional<Resource> resourceOpt = resources.stream().filter(Resource::isMainArtifact).findFirst();
         return resourceOpt.map(this::responseWithResource)
               .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
      }
      return Response.status(Response.Status.NOT_FOUND).build();
   }

   @GET
   @Path("/resources/id/{id}")
   public Response getResourceById(@PathParam("id") String id) {
      log.info("Requesting resource with id {}", id);
      Resource resource = resourceRepository.findById(id);
      if (resource == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      return responseWithResource(resource);
   }

   @GET
   @Path("/resources/service/{serviceId}")
   public List<Resource> getServiceResources(@PathParam("serviceId") String serviceId) {
      log.debug("Request resources for service {}", serviceId);
      return resourceRepository.findByServiceId(serviceId);
   }

   @GET
   @Path("/resources/{serviceId}/{resourceType}")
   public Response getServiceResource(@PathParam("serviceId") String serviceId,
         @PathParam("resourceType") String resourceType) {
      log.info("Requesting {} resource for service {}", resourceType, serviceId);

      Service service = serviceRepository.findById(serviceId);
      if (service != null && ServiceType.GENERIC_REST.equals(service.getType())) {
         // Check if there's one reference resource...
         JsonNode referenceSchema = null;
         List<GenericResource> genericResources = genericResourceRepository.findReferencesByServiceId(serviceId);
         if (genericResources != null && !genericResources.isEmpty()) {
            try {
               Document reference = genericResources.get(0).getPayload();
               if (reference != null) {
                  reference.append(ID_FIELD, genericResources.get(0).getId());
                  referenceSchema = OpenAPISchemaBuilder.buildTypeSchemaFromJson(reference.toJson());
               } else {
                  log.warn("Reference payload is null for serviceId: {}", serviceId);
               }
            } catch (Exception e) {
               log.warn("Exception while building reference schema", e);
            }
         }

         // Prepare HttpHeaders.
         InputStream stream = null;
         String resource = findResource(service);

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

         // Now process the stream, replacing patterns by value.
         if (stream != null) {
            String result = ResourceUtil.replaceTemplatesInSpecStream(stream, service, resource, referenceSchema, null);

            return Response.ok(result.getBytes(StandardCharsets.UTF_8)).type("text/yaml").build();
         }
      }
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   private Response responseWithResource(Resource resource) {
      String extension = resource.getName().substring(resource.getName().lastIndexOf('.'));
      Response.ResponseBuilder builder = Response.ok(resource.getContent());

      if (".json".equals(extension)) {
         builder.type(MediaType.APPLICATION_JSON);
      } else if (".yaml".equals(extension) || ".yml".equals(extension)) {
         builder.type("text/yaml");
         builder.header("Content-Disposition", "inline; filename=\"" + resource.getName() + "\"");
      } else if (".wsdl".equals(extension) || ".xsd".equals(extension)) {
         builder.type(MediaType.TEXT_XML);
      } else if (".avsc".equals(extension)) {
         builder.type(MediaType.APPLICATION_JSON);
      }
      return builder.build();
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
