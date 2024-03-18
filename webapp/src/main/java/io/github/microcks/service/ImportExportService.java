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
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.Service;
import io.github.microcks.event.ChangeType;
import io.github.microcks.event.ServiceChangeEvent;
import io.github.microcks.repository.EventMessageRepository;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.IdBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
   private static Logger log = LoggerFactory.getLogger(ImportExportService.class);

   @Autowired
   private RequestRepository requestRepository;

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private ResponseRepository responseRepository;

   @Autowired
   private EventMessageRepository eventMessageRepository;

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private ApplicationContext applicationContext;

   /**
    * Import a repository from JSON definitions.
    * 
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

      if (log.isInfoEnabled()) {
         log.info("Retrieve {} services to import into repository", model != null ? model.getServices().size() : 0);
         log.info("Retrieve {} resources to import into repository", model != null ? model.getResources().size() : 0);
         log.info("Retrieve {} responses to import into repository", model != null ? model.getResponses().size() : 0);
         log.info("Retrieve {} requests to import into repository", model != null ? model.getRequests().size() : 0);
         log.info("Retrieve {} event messages to import into repository",
               model != null && model.getEventMessages() != null ? model.getEventMessages().size() : 0);
      }
      if (model != null) {
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
         for (Service service : model.getServices()) {
            publishServiceChangeEvent(service);
         }
         return true;
      }
      return false;
   }

   /** Publish a ServiceChangeEvent towards minions or some other consumers. */
   private void publishServiceChangeEvent(Service service) {
      ServiceChangeEvent event = new ServiceChangeEvent(this, service.getId(), ChangeType.UPDATED);
      applicationContext.publishEvent(event);
      log.debug("Service change event has been published");
   }

   /**
    * Get a partial export of repository using the specified services identifiers.
    * 
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
}
