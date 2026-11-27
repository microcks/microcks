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

import io.microcks.domain.Exchange;
import io.microcks.domain.Operation;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.repository.ResourceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.security.UserInfo;
import io.microcks.service.AICopilotRunnerService;
import io.microcks.service.ExchangeSelection;
import io.microcks.service.ImportExportService;
import io.microcks.service.ServiceService;
import io.microcks.artifact.MockRepositoryExporterFactory;
import io.microcks.util.SafeLogger;
import io.microcks.util.ai.AICopilot;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A controller for interacting with optional AI Copilot in Microcks.
 * @author laurent
 */
@Path("/api/copilot")
@Produces(MediaType.APPLICATION_JSON)

@RunOnVirtualThread
public class AICopilotController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(AICopilotController.class);

   private AICopilot copilot;

   private final ServiceService serviceService;
   private final ImportExportService importExportService;
   private final ServiceRepository serviceRepository;
   private final ResourceRepository resourceRepository;
   private final AICopilotRunnerService copilotRunnerService;

   // A map to track async task status.
   private final Map<String, TaskStatus> taskStatus = new ConcurrentHashMap<>();

   /**
    * Build a AICopilotController with required dependencies.
    * @param serviceService      The service to managed Services objects
    * @param importExportService The service to manage import/export of data
    * @param serviceRepository   The repository for Services
    * @param resourceRepository  The repository for Resources
    * @param copilot             The optional AI Copilot
    */
   @Inject
   public AICopilotController(ServiceService serviceService, ImportExportService importExportService,
         ServiceRepository serviceRepository, ResourceRepository resourceRepository, Instance<AICopilot> copilot,
         AICopilotRunnerService copilotRunnerService) {
      this.serviceService = serviceService;
      this.importExportService = importExportService;
      this.serviceRepository = serviceRepository;
      this.resourceRepository = resourceRepository;
      if (copilot.isResolvable()) {
         this.copilot = copilot.get();
      }
      this.copilotRunnerService = copilotRunnerService;
   }

   @GET
   @Path("/samples/{id:.+}")
   public Response getSamplesSuggestions(@PathParam("id") String serviceId,
         @QueryParam("operation") String operationName) {
      log.debug("Retrieving service with id {}", serviceId);

      Service service = null;
      // serviceId may have the form of <service_name>:<service_version>
      if (serviceId.contains(":")) {
         String name = serviceId.substring(0, serviceId.indexOf(':'));
         String version = serviceId.substring(serviceId.indexOf(':') + 1);

         // If service name was encoded with '+' instead of '%20', replace them.
         if (name.contains("+")) {
            name = name.replace('+', ' ');
         }
         service = serviceRepository.findByNameAndVersion(name, version);
      } else {
         service = serviceRepository.findById(serviceId);
      }

      if (service != null) {
         log.debug("We found service, now looking for required contract...");
         List<Resource> resources = null;
         if (service.getType() == ServiceType.REST) {
            resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.OPEN_API_SPEC);
         } else if (service.getType() == ServiceType.GRAPHQL) {
            resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.GRAPHQL_SCHEMA);
         } else if (service.getType() == ServiceType.EVENT) {
            resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.ASYNC_API_SPEC);
         } else if (service.getType() == ServiceType.GRPC) {
            resources = resourceRepository.findByServiceIdAndType(service.getId(), ResourceType.PROTOBUF_SCHEMA);
         }

         if (operationName != null) {
            // Find the matching operation on service.
            Optional<Operation> operation = service.getOperations().stream()
                  .filter(op -> operationName.equals(op.getName())).findFirst();

            // Generate samples in a synchronous way.
            if (resources != null && !resources.isEmpty() && operation.isPresent()) {
               try {
                  List<? extends Exchange> exchanges = copilot.suggestSampleExchanges(service, operation.get(),
                        resources.getFirst(), 2);
                  return Response.status(Response.Status.OK).entity(exchanges).build();
               } catch (Exception e) {
                  log.error("Caught and exception while generating samples", e);
                  return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
               }
            }
         } else {
            // Launch an async generation of samples for all operations.
            final String taskId = UUID.randomUUID().toString();
            taskStatus.put(taskId, TaskStatus.PENDING);
            copilotRunnerService.generateSamplesForService(service, resources.getFirst(), null).thenApply(success -> {
               if (Boolean.TRUE.equals(success)) {
                  taskStatus.put(taskId, TaskStatus.SUCCESS);
               } else {
                  taskStatus.put(taskId, TaskStatus.FAILURE);
               }
               return success;
            });
            return Response.status(Response.Status.CREATED).entity("{\"taskId\": \"" + taskId + "\"}").build();
         }
      }
      log.error("At least one mandatory parameters (serviceId, operationName or contract) is missing");
      return Response.status(Response.Status.BAD_REQUEST).build();
   }

   @GET
   @Path("/samples/task/{id}/status")
   public Response getGenerationTaskStatus(@PathParam("id") String taskId) {
      log.debug("Retrieving status for task {}", taskId);
      TaskStatus status = taskStatus.get(taskId);
      if (status == null) {
         return Response.status(Response.Status.NOT_FOUND).entity("{\"status\": \"NOT_FOUND\"}").build();
      }
      switch (status) {
         case PENDING:
            return Response.status(Response.Status.ACCEPTED).entity("{\"status\": \"PENDING\"}").build();
         case SUCCESS:
            taskStatus.remove(taskId);
            return Response.status(Response.Status.CREATED).entity("{\"status\": \"SUCCESS\"}").build();
         case FAILURE:
            taskStatus.remove(taskId);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("{\"status\": \"FAILURE\"}").build();
         default:
            return Response.status(Response.Status.NOT_FOUND).entity("{\"status\": \"NOT_FOUND\"}").build();
      }
   }

   @POST
   @Path("/samples/{id:.+}")
   public Response addSamplesSuggestions(@PathParam("id") String serviceId,
         @QueryParam("operation") String operationName, List<Exchange> exchanges) {
      log.debug("Adding new AI samples to service {} and operation {}", serviceId, operationName);
      boolean result = serviceService.addAICopilotExchangesToServiceOperation(serviceId, operationName, exchanges,
            null);
      if (result) {
         return Response.status(Response.Status.CREATED).build();
      }
      return Response.status(Response.Status.FORBIDDEN).build();
   }

   @POST
   @Path("/samples/{id:.+}/cleanup")
   public Response removeExchanges(@PathParam("id") String serviceId, ExchangeSelection exchangeSelection) {
      log.debug("Cleaning AI samples from service {} and multiple operations", serviceId);
      boolean result = serviceService.removeAICopilotExchangesFromService(serviceId, exchangeSelection, null);
      if (result) {
         return Response.status(Response.Status.NO_CONTENT).build();
      }
      return Response.status(Response.Status.FORBIDDEN).build();
   }

   @POST
   @Path("/samples/{id:.+}/export")
   public Response exportExchanges(@PathParam("id") String serviceId, @QueryParam("format") String exportFormat,
         ExchangeSelection exchangeSelection) {
      log.debug("Generating AI samples export for service {}", serviceId);

      try {
         byte[] body = importExportService.exportExchangeSelection(serviceId, exchangeSelection, exportFormat, null)
               .getBytes(StandardCharsets.UTF_8);

         return Response.ok(body).type(MediaType.APPLICATION_JSON)
               .header("Content-Disposition", "attachment; filename=microcks-repository.json")
               .header("Content-Length", body.length).build();
      } catch (Exception e) {
         log.error("Exception while exporting the Exchanges selection", e);
         return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
               .entity("Exception while exporting the Exchanges selection. Check server logs.").build();
      }
   }

   private enum TaskStatus {
      PENDING,
      SUCCESS,
      FAILURE
   }
}
