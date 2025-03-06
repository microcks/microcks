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
package io.github.microcks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.event.ChangeType;
import io.github.microcks.event.ServiceChangeEvent;
import io.github.microcks.repository.EventMessageRepository;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.security.AuthorizationChecker;
import io.github.microcks.security.UserInfo;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.MockRepositoryExportException;
import io.github.microcks.util.MockRepositoryExporter;
import io.github.microcks.util.MockRepositoryExporterFactory;
import io.github.microcks.util.metadata.ExamplesExporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * A Service for managing imports and exports of Microcks repository part.
 * @author laurent
 */
@org.springframework.stereotype.Service
public class ImportExportService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ImportExportService.class);

   private final ServiceService serviceService;
   private final MessageService messageService;
   private final RequestRepository requestRepository;
   private final ResourceRepository resourceRepository;
   private final ResponseRepository responseRepository;
   private final EventMessageRepository eventMessageRepository;
   private final ServiceRepository serviceRepository;
   private final ApplicationContext applicationContext;
   private final AuthorizationChecker authorizationChecker;

   /**
    * Create a new ImportExportService with required dependencies.
    * @param serviceService         The service for services
    * @param messageService         The service for messages
    * @param requestRepository      The repository for requests
    * @param resourceRepository     The repository for resources
    * @param responseRepository     The repository for responses
    * @param eventMessageRepository The repository for event messages
    * @param serviceRepository      The repository for services
    * @param applicationContext     The Spring application context
    * @param authorizationChecker   The authorization checker service
    */
   public ImportExportService(ServiceService serviceService, MessageService messageService,
         RequestRepository requestRepository, ResourceRepository resourceRepository,
         ResponseRepository responseRepository, EventMessageRepository eventMessageRepository,
         ServiceRepository serviceRepository, ApplicationContext applicationContext,
         AuthorizationChecker authorizationChecker) {
      this.serviceService = serviceService;
      this.messageService = messageService;
      this.requestRepository = requestRepository;
      this.resourceRepository = resourceRepository;
      this.responseRepository = responseRepository;
      this.eventMessageRepository = eventMessageRepository;
      this.serviceRepository = serviceRepository;
      this.applicationContext = applicationContext;
      this.authorizationChecker = authorizationChecker;
   }

   /**
    * Import a repository from JSON definitions.
    * @param json A String encoded into json and representing repository object definitions.
    * @return A boolean indicating operation success.
    */
   public boolean importRepository(String json) {
      ObjectMapper mapper = new ObjectMapper();
      ImportExportModel model = null;
      try {
         model = mapper.readValue(json, ImportExportModel.class);
      } catch (Exception e) {
         log.error("Exception while reading json import", e);
      }

      if (model != null) {
         log.info("Retrieve {} services to import into repository", model.getServices().size());
         log.info("Retrieve {} resources to import into repository", model.getResources().size());
         log.info("Retrieve {} responses to import into repository", model.getResponses().size());
         log.info("Retrieve {} requests to import into repository", model.getRequests().size());
         log.info("Retrieve {} event messages to import into repository",
               model.getEventMessages() != null ? model.getEventMessages().size() : 0);

         serviceRepository.saveAll(model.getServices());
         resourceRepository.saveAll(model.getResources());
         responseRepository.saveAll(model.getResponses());
         requestRepository.saveAll(model.getRequests());
         // Make it optional to allow importing an old snapshot.
         if (model.getEventMessages() != null) {
            eventMessageRepository.saveAll(model.getEventMessages());
         }

         // Once everything is saved, be sure to fire a change event to allow
         // propagation.
         publishServiceChangeEvent(model);
         return true;
      }
      log.info("No services, resources or messages to import into repository");
      return false;
   }

   /** Publish a ServiceChangeEvent towards minions or some other consumers. */
   private void publishServiceChangeEvent(ImportExportModel model) {
      for (Service service : model.getServices()) {
         ServiceChangeEvent event = new ServiceChangeEvent(this, service.getId(), ChangeType.UPDATED);
         applicationContext.publishEvent(event);
         log.debug("Service change event has been published");
      }
   }

   /**
    * Get a partial export of repository using the specified services identifiers.
    * @param ids    The list of service ids to export
    * @param format The format for this export (reserved for future usage)
    * @return A string representation of this repository export
    */
   public String exportRepository(List<String> ids, String format) {
      StringBuilder result = new StringBuilder("{");
      ObjectMapper mapper = new ObjectMapper();

      // First, retrieve service list.
      List<Service> services = serviceRepository.findByIdIn(ids);
      try {
         String jsonArray = mapper.writeValueAsString(services);
         result.append("\"services\":").append(jsonArray).append(", ");
      } catch (Exception e) {
         log.error("Exception while serializing services for export", e);
      }

      // Then, get resources associated to services.
      List<Resource> resources = resourceRepository.findByServiceIdIn(ids);
      try {
         String jsonArray = mapper.writeValueAsString(resources);
         result.append("\"resources\":").append(jsonArray).append(", ");
      } catch (Exception e) {
         log.error("Exception while serializing resources for export", e);
      }

      // Finally, get requests and responses associated to the services.
      List<String> operationIds = new ArrayList<>();
      for (Service service : services) {
         for (Operation operation : service.getOperations()) {
            operationIds.add(IdBuilder.buildOperationId(service, operation));
         }
      }

      List<Request> requests = requestRepository.findByOperationIdIn(operationIds);
      List<Response> responses = responseRepository.findByOperationIdIn(operationIds);
      List<EventMessage> eventMessages = eventMessageRepository.findByOperationIdIn(operationIds);
      try {
         String jsonArray = mapper.writeValueAsString(requests);
         result.append("\"requests\":").append(jsonArray).append(", ");
         jsonArray = mapper.writeValueAsString(responses);
         result.append("\"responses\":").append(jsonArray).append(", ");
         jsonArray = mapper.writeValueAsString(eventMessages);
         result.append("\"eventMessages\":").append(jsonArray);
      } catch (Exception e) {
         log.error("Exception while serializing messages for export", e);
      }

      return result.append("}").toString();
   }

   public static class ImportExportModel {
      private List<Service> services;
      private List<Resource> resources;
      private List<Request> requests;
      private List<Response> responses;
      private List<EventMessage> eventMessages;

      public List<Service> getServices() {
         return services;
      }

      public void setServices(List<Service> services) {
         this.services = services;
      }

      public List<Resource> getResources() {
         return resources;
      }

      public void setResources(List<Resource> resources) {
         this.resources = resources;
      }

      public List<Request> getRequests() {
         return requests;
      }

      public void setRequests(List<Request> requests) {
         this.requests = requests;
      }

      public List<Response> getResponses() {
         return responses;
      }

      public void setResponses(List<Response> responses) {
         this.responses = responses;
      }

      public List<EventMessage> getEventMessages() {
         return eventMessages;
      }

      public void setEventMessages(List<EventMessage> eventMessages) {
         this.eventMessages = eventMessages;
      }
   }

   /**
    * Export a selection of exchanges for a service.
    * @param serviceId         The unique identifier of the service
    * @param exchangeSelection The selection of exchanges to export
    * @param exportFormat      The format for this export
    * @param userInfo          The user information
    * @return A string representation of this exchange selection export
    */
   public String exportExchangeSelection(String serviceId, ExchangeSelection exchangeSelection, String exportFormat,
         UserInfo userInfo) throws MockRepositoryExportException {
      Service service = serviceService.getServiceById(serviceId);
      if (service != null
            && authorizationChecker.hasRoleForService(userInfo, AuthorizationChecker.ROLE_MANAGER, service)) {

         // Create an exporter and add the service definition.
         MockRepositoryExporter exporter = MockRepositoryExporterFactory.getMockRepositoryExporter(exportFormat);
         exporter.addServiceDefinition(service);

         for (Operation operation : service.getOperations()) {
            if (exchangeSelection.getExchanges().containsKey(operation.getName())) {
               log.debug("Exporting selected exchanges for operation '{}'", operation.getName());
               String operationId = IdBuilder.buildOperationId(service, operation);

               List<? extends Exchange> selectedExchanges = null;
               if (ServiceType.EVENT.equals(service.getType())) {
                  selectedExchanges = messageService.getEventByOperation(operationId).stream()
                        .filter(unidirectionalEvent -> exchangeSelection.getExchanges().get(operation.getName())
                              .contains(unidirectionalEvent.getEventMessage().getName()))
                        .toList();
               } else {
                  selectedExchanges = messageService.getRequestResponseByOperation(operationId).stream()
                        .filter(pair -> exchangeSelection.getExchanges().get(operation.getName())
                              .contains(pair.getRequest().getName()))
                        .toList();
               }
               exporter.addMessageDefinitions(service, operation, selectedExchanges);
            }
         }
         return exporter.exportAsString();
      }
      log.warn("Didn't find any service with id {} to export or unauthorized, returning empty content", serviceId);
      return "";
   }
}
