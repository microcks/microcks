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

import io.microcks.domain.Exchange;
import io.microcks.domain.Metadata;
import io.microcks.domain.Operation;
import io.microcks.domain.RequestResponsePair;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceType;
import io.microcks.domain.ServiceView;
import io.microcks.repository.CustomServiceRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.service.MessageService;
import io.microcks.service.ServiceService;
import io.microcks.util.EntityAlreadyExistsException;
import io.microcks.util.IdBuilder;
import io.microcks.util.SafeLogger;
import io.microcks.web.dto.GenericResourceServiceDTO;
import io.microcks.web.dto.OperationOverrideDTO;

import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A JAX-RS resource for API defined on services.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ServiceController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(ServiceController.class);

   private final ServiceService serviceService;
   private final ServiceRepository serviceRepository;
   private final MessageService messageService;

   /**
    * Build a new ServiceController with its dependencies.
    * @param serviceService    to perform business logic on Services
    * @param serviceRepository to have access to Services definition
    * @param messageService    to have acces to Services messages
    */
   @Inject
   public ServiceController(ServiceService serviceService, ServiceRepository serviceRepository,
         MessageService messageService) {
      this.serviceService = serviceService;
      this.serviceRepository = serviceRepository;
      this.messageService = messageService;
   }

   @GET
   @Path("/services")
   public List<Service> listServices(@QueryParam("page") @DefaultValue("0") int page,
         @QueryParam("size") @DefaultValue("20") int size) {
      log.debug("Getting service list for page {} and size {}", page, size);
      return serviceRepository.findAll(Sort.ascending("name", "version")).page(Page.of(page, size)).list();
   }

   @GET
   @Path("/services/search")
   public List<Service> searchServices(@jakarta.ws.rs.core.Context UriInfo uriInfo) {
      // Parse params from query parameters.
      String name = null;
      Map<String, String> labels = new HashMap<>();
      for (Map.Entry<String, List<String>> entry : uriInfo.getQueryParameters().entrySet()) {
         String value = entry.getValue().isEmpty() ? null : entry.getValue().get(0);
         if ("name".equals(entry.getKey())) {
            name = value;
         } else if (entry.getKey().startsWith("labels.")) {
            labels.put(entry.getKey().substring(entry.getKey().indexOf('.') + 1), value);
         }
      }

      if (labels.isEmpty()) {
         log.debug("Searching services corresponding to name {}", name);
         return serviceRepository.findByNameLike(name);
      }
      if (name == null || name.trim().isEmpty()) {
         log.debug("Searching services corresponding to labels {}", labels);
         return serviceRepository.findByLabels(labels);
      }
      log.debug("Searching services corresponding to name {} and labels {}", name, labels);
      return serviceRepository.findByLabelsAndNameLike(labels, name);
   }

   @GET
   @Path("/services/count")
   public Map<String, Long> countServices() {
      log.debug("Counting services...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", serviceRepository.count());
      return counter;
   }

   @GET
   @Path("/services/map")
   public Map<String, Integer> getServicesMap() {
      log.debug("Counting services by type...");
      Map<String, Integer> map = new HashMap<>();
      List<CustomServiceRepository.ServiceCount> results = serviceRepository.countServicesByType();
      for (CustomServiceRepository.ServiceCount count : results) {
         map.put(count.getType(), count.getNumber());
      }
      return map;
   }

   @GET
   @Path("/services/labels")
   public Map<String, String[]> getServicesLabels() {
      log.debug("Retrieving available services labels...");
      Map<String, String[]> labelValues = new HashMap<>();
      List<CustomServiceRepository.LabelValues> results = serviceRepository.listLabels();
      for (CustomServiceRepository.LabelValues values : results) {
         labelValues.put(values.getKey(), values.getValues());
      }
      return labelValues;
   }

   @GET
   @Path("/services/{id:.+}")
   public Response getService(@PathParam("id") String serviceId,
         @QueryParam("messages") @DefaultValue("true") boolean messages) {
      log.debug("Retrieving service with id {}", serviceId);

      // Just retrieve the service and return it.
      Service service = serviceService.getServiceById(serviceId);
      if (service == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      if (messages) {
         // Put messages into a map where key is operation name.
         Map<String, List<? extends Exchange>> messagesMap = new HashMap<>();
         for (Operation operation : service.getOperations()) {
            if (service.getType() == ServiceType.EVENT || service.getType() == ServiceType.GENERIC_EVENT) {
               // If an event, we should explicitly retrieve event messages.
               List<? extends Exchange> events = messageService
                     .getEventByOperation(IdBuilder.buildOperationId(service, operation));
               messagesMap.put(operation.getName(), events);
            } else {
               // Otherwise we have traditional request / response pairs.
               List<RequestResponsePair> pairs = messageService
                     .getRequestResponseByOperation(IdBuilder.buildOperationId(service, operation));
               messagesMap.put(operation.getName(), pairs);
            }
         }
         return Response.ok(new ServiceView(service, messagesMap)).build();
      }
      return Response.ok(service).build();
   }

   @POST
   @Path("/services/generic")
   public Response createGenericResourceService(GenericResourceServiceDTO serviceDTO) {
      log.debug("Creating a new Service '{}-{}' for generic resource '{}'", serviceDTO.getName(),
            serviceDTO.getVersion(), serviceDTO.getResource());

      try {
         Service service = serviceService.createGenericResourceService(serviceDTO.getName(), serviceDTO.getVersion(),
               serviceDTO.getResource(), serviceDTO.getReferencePayload());
         return Response.status(Response.Status.CREATED).entity(service).build();
      } catch (EntityAlreadyExistsException eaee) {
         log.error("Service '{}-{} already exists'", serviceDTO.getName(), serviceDTO.getVersion());
         return Response.status(Response.Status.CONFLICT).build();
      }
   }

   @POST
   @Path("/services/generic/event")
   public Response createGenericEventService(GenericResourceServiceDTO serviceDTO) {
      log.debug("Creating a new Service '{}-{}' for generic resource '{}'", serviceDTO.getName(),
            serviceDTO.getVersion(), serviceDTO.getResource());

      try {
         Service service = serviceService.createGenericEventService(serviceDTO.getName(), serviceDTO.getVersion(),
               serviceDTO.getResource(), serviceDTO.getReferencePayload());
         return Response.status(Response.Status.CREATED).entity(service).build();
      } catch (EntityAlreadyExistsException eaee) {
         log.error("Service '{}-{} already exists'", serviceDTO.getName(), serviceDTO.getVersion());
         return Response.status(Response.Status.CONFLICT).build();
      }
   }

   @PUT
   @Path("/services/{id}/metadata")
   public Response updateMetadata(@PathParam("id") String serviceId, Metadata metadata) {
      log.debug("Updating the metadata of service {}", serviceId);
      boolean result = serviceService.updateMetadata(serviceId, metadata, null);
      return Response.status(result ? Response.Status.OK : Response.Status.FORBIDDEN).build();
   }

   @PUT
   @Path("/services/{id}/operation")
   public Response overrideServiceOperation(@PathParam("id") String serviceId,
         @QueryParam("operationName") String operationName, OperationOverrideDTO operationOverride) {
      log.debug("Updating operation {} of service {}", operationName, serviceId);
      log.debug("ParameterConstraints?: {}", operationOverride.getParameterConstraints());
      boolean result = serviceService.updateOperation(serviceId, operationName, operationOverride.getDispatcher(),
            operationOverride.getDispatcherRules(), operationOverride.getDefaultDelay(),
            operationOverride.getDefaultDelayStrategy(), operationOverride.getParameterConstraints(), null);
      return Response.status(result ? Response.Status.OK : Response.Status.FORBIDDEN).build();
   }

   @DELETE
   @Path("/services/{id}")
   public Response deleteService(@PathParam("id") String serviceId) {
      log.debug("Removing service with id {}", serviceId);
      boolean result = serviceService.deleteService(serviceId, null);
      return Response.status(result ? Response.Status.OK : Response.Status.FORBIDDEN).build();
   }
}
