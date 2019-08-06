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

import io.github.microcks.domain.*;
import io.github.microcks.repository.*;
import io.github.microcks.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Bean defining service operations around Service domain objects.
 * @author laurent
 */
@org.springframework.stereotype.Service
public class ServiceService {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ServiceService.class);

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private ResourceRepository resourceRepository;

   @Autowired
   private RequestRepository requestRepository;

   @Autowired
   private ResponseRepository responseRepository;

   @Autowired
   private TestResultRepository testResultRepository;

   @Value("${network.username}")
   private final String username = null;

   @Value("${network.password}")
   private final String password = null;


   /**
    * Import definitions of services and bounded resources and messages into Microcks
    * repository. This uses a MockRepositoryImporter underhood.
    * @param repositoryUrl The String representing mock repository url.
    * @param repositorySecret The authentication secret associated with the repository url. Can be set to null if none.
    * @param disableSSLValidation Whether SSL certificates validation should be turned off.
    * @return The list of imported Services
    * @throws MockRepositoryImportException if something goes wrong (URL not reachable nor readable, etc...)
    */
   public List<Service> importServiceDefinition(String repositoryUrl, Secret repositorySecret, boolean disableSSLValidation) throws MockRepositoryImportException {
      log.info("Importing service definitions from " + repositoryUrl);
      File localFile = null;

      if (repositoryUrl.startsWith("http")) {
         try {
            localFile = HTTPDownloader.handleHTTPDownloadToFile(repositoryUrl, repositorySecret, disableSSLValidation);
         } catch (IOException ioe) {
            log.error("Exception while downloading " + repositoryUrl, ioe);
            throw new MockRepositoryImportException(repositoryUrl + " cannot be downloaded", ioe);
         }
      } else {
         // Simply build locaFile from repository url.
         localFile = new File(repositoryUrl);
      }

      return importServiceDefinition(localFile);
   }


   /**
    * Import definitions of services and bounded resources and messages into Microcks
    * repository. This uses a MockRepositoryImporter underhood.
    * @param repositoryFile The File for mock repository.
    * @return The list of imported Services
    * @throws MockRepositoryImportException if something goes wrong (URL not reachable nor readable, etc...)
    */
   public List<Service> importServiceDefinition(File repositoryFile) throws MockRepositoryImportException {
      // Retrieve the correct importer based on file path.
      MockRepositoryImporter importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(repositoryFile);
      } catch (IOException ioe) {
         log.error("Exception while accessing file " + repositoryFile.getPath(), ioe);
         throw new MockRepositoryImportException(repositoryFile.getPath() + " cannot be found", ioe);
      }

      List<Service> services = importer.getServiceDefinitions();
      for (Service service : services){
         Service existingService = serviceRepository.findByNameAndVersion(service.getName(), service.getVersion());
         log.debug("Service [{}, {}] exists ? {}", service.getName(), service.getVersion(), existingService != null);
         if (existingService != null){
            // Retrieve its previous identifier and metadatas.
            service.setId(existingService.getId());
            service.setMetadata(existingService.getMetadata());

            // Keep its overriden operation properties
            copyOverridenOperations(existingService, service);
         }
         if (service.getMetadata() == null) {
            service.setMetadata(new Metadata());
         }

         service.getMetadata().objectUpdated();
         service = serviceRepository.save(service);

         // Remove resources previously attached to service.
         List<Resource> existingResources = resourceRepository.findByServiceId(service.getId());
         if (existingResources != null && existingResources.size() > 0){
            resourceRepository.delete(existingResources);
         }

         // Save new resources.
         List<Resource> resources = importer.getResourceDefinitions(service);
         for (Resource resource : resources){
            resource.setServiceId(service.getId());
         }
         resourceRepository.save(resources);

         for (Operation operation : service.getOperations()){
            String operationId = IdBuilder.buildOperationId(service, operation);

            // Remove messages previously attached to service.
            requestRepository.delete(requestRepository.findByOperationId(operationId));
            responseRepository.delete(responseRepository.findByOperationId(operationId));

            Map<Request, Response> messages = importer.getMessageDefinitions(service, operation);

            // Associate response with operation before saving.
            for (Response response : messages.values()){
               response.setOperationId(operationId);
            }
            responseRepository.save(messages.values());
            // Associate request with response and operation before saving.
            for (Request request : messages.keySet()){
               request.setOperationId(operationId);
               request.setResponseId(messages.get(request).getId());
            }
            requestRepository.save(messages.keySet());
         }

         // When extracting message informations, we may have modified Operation because discovered new resource paths
         // depending on variable URI parts. As a consequence, we got to update Service in repository.
         serviceRepository.save(service);
      }
      log.info("Having imported {} services definitions into repository", services.size());
      return services;
   }

   /**
    * Create a new Service concerning a GenericResource for dynamic mocking.
    * @param name The name of the new Service to create
    * @param version The version of the new Service to create
    * @param resource The resource that will be exposed as CRUD operations for this service
    * @return The newly created Service object
    * @throws EntityAlreadyExistsException if a Service with same name and version is already present in store
    */
   public Service createGenericResourceService(String name, String version, String resource)
         throws EntityAlreadyExistsException {
      log.info("Creating a new Service '{}-{}' for generic resource {}", name, version, resource);

      // Check if corresponding Service already exists.
      Service existingService = serviceRepository.findByNameAndVersion(name, version);
      if (existingService != null) {
         log.warn("A Service '{}-{}' is already existing. Throwing an Exception", name, version);
         throw new EntityAlreadyExistsException(String.format("Service '%s-%s' is already present in store", name, version));
      }
      // Create new service with GENERIC_REST type.
      Service service = new Service();
      service.setName(name);
      service.setVersion(version);
      service.setType(ServiceType.GENERIC_REST);
      service.setMetadata(new Metadata());

      // Now create basic crud operations for the resource.
      Operation createOp = new Operation();
      createOp.setName("POST /" + resource);
      createOp.setMethod("POST");
      service.addOperation(createOp);

      Operation getOp = new Operation();
      getOp.setName("GET /" + resource + "/:id");
      getOp.setMethod("GET");
      getOp.setDispatcher(DispatchStyles.URI_PARTS);
      getOp.setDispatcherRules("id");
      service.addOperation(getOp);

      Operation updateOp = new Operation();
      updateOp.setName("PUT /" + resource + "/:id");
      updateOp.setMethod("PUT");
      updateOp.setDispatcher(DispatchStyles.URI_PARTS);
      updateOp.setDispatcherRules("id");
      service.addOperation(updateOp);

      Operation listOp = new Operation();
      listOp.setName("GET /" + resource);
      listOp.setMethod("GET");
      service.addOperation(listOp);

      Operation delOp = new Operation();
      delOp.setName("DELETE /" + resource + "/:id");
      delOp.setMethod("DELETE");
      delOp.setDispatcher(DispatchStyles.URI_PARTS);
      delOp.setDispatcherRules("id");
      service.addOperation(delOp);

      serviceRepository.save(service);
      log.info("Having create Service '{}' for generic resource {}", service.getId(), resource);

      return service;
   }

   /**
    * Remove a Service and its bound documents using the service id.
    * @param id The identifier of service to remove.
    */
   public void deleteService(String id){
      // Get service to remove.
      Service service = serviceRepository.findOne(id);

      // Delete all resources first.
      resourceRepository.delete(resourceRepository.findByServiceId(id));

      // Delete all requests and responses bound to service operation.
      for (Operation operation : service.getOperations()){
         String operationId = IdBuilder.buildOperationId(service, operation);
         requestRepository.delete(requestRepository.findByOperationId(operationId));
         responseRepository.delete(responseRepository.findByOperationId(operationId));
      }

      // Delete all tests related to service.
      testResultRepository.delete(testResultRepository.findByServiceId(id));

      // Finally delete service.
      serviceRepository.delete(service);
      log.info("Service [{}] has been fully deleted", id);
   }

   /**
    * Update the default delay of a Service operation
    * @param id The identifier of service to update operation for
    * @param operationName The name of operation to update delay for
    * @param dispatcher The dispatcher to use for this operation
    * @param dispatcherRules The dispatcher rules to use for this operation
    * @param delay The new delay value for operation
    * @param constraints Constraints for this operation parameters
    * @return True if operation has been found and updated, false otherwise.
    */
   public Boolean updateOperation(String id, String operationName, String dispatcher, String dispatcherRules, Long delay, List<ParameterConstraint> constraints) {
      Service service = serviceRepository.findOne(id);
      for (Operation operation : service.getOperations()){
         if (operation.getName().equals(operationName)){
            operation.setDispatcher(dispatcher);
            operation.setDispatcherRules(dispatcherRules);
            operation.setParameterConstraints(constraints);
            operation.setDefaultDelay(delay);
            operation.setOverride(true);
            serviceRepository.save(service);
            return true;
         }
      }
      return false;
   }


   /** Recopy overriden operation mutable properties into newService. */
   private void copyOverridenOperations(Service existingService, Service newService) {
      for (Operation existingOperation : existingService.getOperations()) {
         if (existingOperation.hasOverride()) {
            for (Operation op : newService.getOperations()) {
               if (existingOperation.getName().equals(op.getName())) {
                  op.setDefaultDelay(existingOperation.getDefaultDelay());
                  op.setDispatcher(existingOperation.getDispatcher());
                  op.setDispatcherRules(existingOperation.getDispatcherRules());
                  op.setParameterConstraints(existingOperation.getParameterConstraints());
               }
            }
         }
      }
   }
}
