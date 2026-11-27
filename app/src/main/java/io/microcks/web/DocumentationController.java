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

import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.repository.ResourceRepository;
import io.microcks.util.ResourceUtil;
import io.microcks.util.SafeLogger;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A controller for generating API documentation for contracts mocked within Microcks.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class DocumentationController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(DocumentationController.class);

   private static final String RESOURCE_URL = "{RESOURCE_URL}";

   final ResourceRepository resourceRepository;

   /**
    * Build a new DocumentationController with a resource repository.
    * @param resourceRepository Repository to access resource.
    */
   @Inject
   public DocumentationController(ResourceRepository resourceRepository) {
      this.resourceRepository = resourceRepository;
   }

   @GET
   @Path("/documentation/{name}/{resourceType}")
   public Response getDocumentationByResourceName(@PathParam("name") String name,
         @PathParam("resourceType") String resourceType) {
      log.info("Requesting {} documentation for resource {}", resourceType, name);

      Resource resource = null;
      if (ResourceType.ASYNC_API_SPEC.toString().equals(resourceType)) {
         List<Resource> resources = resourceRepository.findByName(name);
         if (!resources.isEmpty()) {
            Optional<Resource> resourceOpt = resources.stream().filter(Resource::isMainArtifact).findFirst();
            if (resourceOpt.isPresent()) {
               resource = resourceOpt.get();
            } else {
               return Response.status(Response.Status.BAD_REQUEST).build();
            }
         } else {
            return Response.status(Response.Status.BAD_REQUEST).build();
         }
      }

      return responseWithResource("/api/resources/" + encodePathSegment(name), resourceType, resource);
   }

   @GET
   @Path("/documentation/id/{id}/{resourceType}")
   public Response getDocumentationByResourceId(@PathParam("id") String id,
         @PathParam("resourceType") String resourceType) {
      log.info("Requesting {} documentation for resource with id {}", resourceType, id);

      Resource resource = null;
      if (ResourceType.ASYNC_API_SPEC.toString().equals(resourceType)) {
         resource = resourceRepository.findById(id);
         if (resource == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
         }
      }

      return responseWithResource("/api/resources/id/" + encodePathSegment(id), resourceType, resource);
   }

   private static String encodePathSegment(String segment) {
      return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
   }

   private Response responseWithResource(String resourceUrl, String resourceType, Resource resource) {
      // Prepare HttpHeaders.
      InputStream stream = null;
      String templatePath = null;
      String contentType = null;

      // Get the correct template depending on resource type.
      if (ResourceType.OPEN_API_SPEC.toString().equals(resourceType)
            || ResourceType.SWAGGER.toString().equals(resourceType)) {
         templatePath = "templates/redoc.html";
         contentType = MediaType.TEXT_HTML;
      } else if (ResourceType.ASYNC_API_SPEC.toString().equals(resourceType)) {

         if (resource.getContent().contains("asyncapi: 3") || resource.getContent().contains("\"asyncapi\": \"3")
               || resource.getContent().contains("'asyncapi': '3")) {
            templatePath = "templates/asyncapi-v3.html";
         } else {
            templatePath = "templates/asyncapi.html";
         }
         contentType = MediaType.TEXT_HTML;
      }

      if (templatePath != null) {
         try {
            stream = ResourceUtil.getClasspathResource(templatePath);
         } catch (IOException e) {
            log.error("IOException while reading template {}", templatePath, e);
         }

         // Now process the stream, replacing patterns by value.
         BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
         StringWriter writer = new StringWriter();

         try (Stream<String> lines = reader.lines()) {
            lines.map(line -> replaceResourceUrlInLing(line, resourceUrl)).forEach(line -> writer.write(line + "\n"));
         }
         return Response.ok(writer.toString().getBytes(StandardCharsets.UTF_8)).type(contentType).build();
      }
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   private String replaceResourceUrlInLing(String line, String resourceUrl) {
      return line.replace(RESOURCE_URL, resourceUrl);
   }
}
