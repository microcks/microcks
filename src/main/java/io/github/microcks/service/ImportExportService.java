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
package io.github.microcks.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.microcks.domain.*;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.util.IdBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

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
   private ServiceRepository serviceRepository;

   /**
    * Import a repository from JSON definitions.
    * @param json A String encoded into json and representing repository object definitions.
    * @return A boolean indicating operation success.
    */
   public boolean importRepository(String json){
      ObjectMapper mapper = new ObjectMapper();
      ImportExportModel model = null;
      try{
         model = mapper.readValue(json, ImportExportModel.class);
      } catch (Exception e){
         log.error("Exception while reading json import", e);
      }

      if (log.isInfoEnabled()){
         log.info("Retrieve " + model.getServices().size() + " services to import into repository");
         log.info("Retrieve " + model.getResources().size() + " resources to import into repository");
         log.info("Retrieve " + model.getResponses().size() + " responses to import into repository");
         log.info("Retrieve " + model.getRequests().size() + " requests to import into repository");
      }
      if (model != null){
         serviceRepository.save(model.getServices());
         resourceRepository.save(model.getResources());
         responseRepository.save(model.getResponses());
         requestRepository.save(model.getRequests());
         return true;
      }

      return false;
   }

   /**
    * Get a partial export of repository using the specified services identifiers.
    * @param ids The list of service ids to export
    * @param format The format for this export (reserved for future usage)
    * @return A string representation of this repository export
    */
   public String exportRepository(List<String> ids, String format){
      StringBuilder result = new StringBuilder("{");
      ObjectMapper mapper = new ObjectMapper();

      // First, retrieve service list.
      List<Service> services = serviceRepository.findByIdIn(ids);
      try{
         String jsonArray = mapper.writeValueAsString(services);
         result.append("\"services\":").append(jsonArray).append(", ");
      } catch (Exception e){
         log.error("Exception while serializing services for export", e);
      }

      // Then, get resources associated to services.
      List<Resource> resources = resourceRepository.findByServiceIdIn(ids);
      try{
         String jsonArray = mapper.writeValueAsString(resources);
         result.append("\"resources\":").append(jsonArray).append(", ");
      } catch (Exception e){
         log.error("Exception while serializing resources for export", e);
      }

      // Finally, get requests and responses associated to services.
      List<String> operationIds = new ArrayList<>();
      for (Service service : services){
         for (Operation operation : service.getOperations()){
            operationIds.add(IdBuilder.buildOperationId(service, operation));
         }
      }
      List<Request> requests = requestRepository.findByOperationIdIn(operationIds);
      List<Response> responses = responseRepository.findByOperationIdIn(operationIds);
      try{
         String jsonArray = mapper.writeValueAsString(requests);
         result.append("\"requests\":").append(jsonArray).append(", ");
         jsonArray = mapper.writeValueAsString(responses);
         result.append("\"responses\":").append(jsonArray);
      } catch (Exception e){
         log.error("Exception while serializing messages for export", e);
      }

      return result.append("}").toString();
   }

   public static class ImportExportModel {
      private List<Service> services;
      private List<Resource> resources;
      private List<Request> requests;
      private List<Response> responses;

      public ImportExportModel() {
      }

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
   }
}
