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

import io.github.microcks.domain.Binding;
import io.github.microcks.domain.BindingType;
import io.github.microcks.domain.EventMessage;
import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.GenericResource;
import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.ParameterConstraint;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Secret;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.event.ChangeType;
import io.github.microcks.event.ServiceChangeEvent;
import io.github.microcks.repository.EventMessageRepository;
import io.github.microcks.repository.GenericResourceRepository;
import io.github.microcks.repository.RequestRepository;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.repository.TestResultRepository;
import io.github.microcks.security.AuthorizationChecker;
import io.github.microcks.security.UserInfo;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.EntityAlreadyExistsException;
import io.github.microcks.util.HTTPDownloader;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.MockRepositoryImporter;
import io.github.microcks.util.MockRepositoryImporterFactory;
import io.github.microcks.util.ReferenceResolver;
import io.github.microcks.util.RelativeReferenceURLBuilder;
import io.github.microcks.util.RelativeReferenceURLBuilderFactory;
import io.github.microcks.util.ResourceUtil;
import io.github.microcks.util.openapi.OpenAPISchemaBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bean defining service operations around Service domain objects.
 * @author laurent
 */
@org.springframework.stereotype.Service
public class ServiceService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ServiceService.class);

   private static final String AI_COPILOT_SOURCE = "AI Copilot";

   private final ServiceRepository serviceRepository;
   private final ResourceRepository resourceRepository;
   private final GenericResourceRepository genericResourceRepository;
   private final RequestRepository requestRepository;
   private final ResponseRepository responseRepository;
   private final EventMessageRepository eventMessageRepository;
   private final TestResultRepository testResultRepository;

   private final ApplicationContext applicationContext;
   private final AuthorizationChecker authorizationChecker;

   @Value("${async-api.default-binding}")
   private final String defaultAsyncBinding = null;

   @Value("${async-api.default-frequency}")
   private final Long defaultAsyncFrequency = 30L;


   /**
    * Build a ServiceService with required dependencies.
    * @param serviceRepository         The repository to access service definitions
    * @param resourceRepository        The repository to access resource definitions
    * @param genericResourceRepository The repository to access generic resource definitions
    * @param requestRepository         The repository to access request definitions
    * @param responseRepository        The repository to access response definitions
    * @param eventMessageRepository    The repository to access event message definitions
    * @param testResultRepository      The repository to access test result definitions
    * @param applicationContext        The Spring application context
    * @param authorizationChecker      The authorization checker service
    */
   public ServiceService(ServiceRepository serviceRepository, ResourceRepository resourceRepository,
         GenericResourceRepository genericResourceRepository, RequestRepository requestRepository,
         ResponseRepository responseRepository, EventMessageRepository eventMessageRepository,
         TestResultRepository testResultRepository, ApplicationContext applicationContext,
         AuthorizationChecker authorizationChecker) {
      this.serviceRepository = serviceRepository;
      this.resourceRepository = resourceRepository;
      this.genericResourceRepository = genericResourceRepository;
      this.requestRepository = requestRepository;
      this.responseRepository = responseRepository;
      this.eventMessageRepository = eventMessageRepository;
      this.testResultRepository = testResultRepository;
      this.applicationContext = applicationContext;
      this.authorizationChecker = authorizationChecker;
   }

   /**
    * Retrieve the corresponding Service from its identifier.
    * @param serviceId The technical or functional (service_name:service_version) identifier of service to retrieve
    * @return The corresponding Service or null if not found
    */
   public Service getServiceById(String serviceId) {
      // serviceId may have the form of <service_name>:<service_version>
      if (serviceId.contains(":")) {
         String name = serviceId.substring(0, serviceId.indexOf(':'));
         String version = serviceId.substring(serviceId.indexOf(':') + 1);

         // If service name was encoded with '+' instead of '%20', replace them.
         if (name.contains("+")) {
            name = name.replace('+', ' ');
         }
         return serviceRepository.findByNameAndVersion(name, version);
      }
      return serviceRepository.findById(serviceId).orElse(null);
   }

   /**
    * Import definitions of services and bounded resources and messages into Microcks repository. This uses a
    * MockRepositoryImporter under the hood.
    * @param repositoryUrl        The String representing mock repository url.
    * @param repositorySecret     The authentication secret associated with the repository url. Can be set to null if
    *                             none.
    * @param disableSSLValidation Whether SSL certificates validation should be turned off.
    * @param mainArtifact         Whether this repository should be considered as main artifact for Services to import.
    * @return The list of imported Services
    * @throws MockRepositoryImportException if something goes wrong (URL not reachable nor readable, etc...)
    */
   public List<Service> importServiceDefinition(String repositoryUrl, Secret repositorySecret,
         boolean disableSSLValidation, boolean mainArtifact) throws MockRepositoryImportException {
      log.info("Importing service definitions from {}", repositoryUrl);
      File localFile = null;
      Map<String, List<String>> fileProperties = null;

      if (repositoryUrl.startsWith("http")) {
         try {
            HTTPDownloader.FileAndHeaders fileAndHeaders = HTTPDownloader
                  .handleHTTPDownloadToFileAndHeaders(repositoryUrl, repositorySecret, disableSSLValidation);
            localFile = fileAndHeaders.getLocalFile();
            fileProperties = fileAndHeaders.getResponseHeaders();
         } catch (IOException ioe) {
            throw new MockRepositoryImportException(repositoryUrl + " cannot be downloaded", ioe);
         }
      } else {
         // Simply build localFile from repository url.
         localFile = new File(repositoryUrl);
      }

      RelativeReferenceURLBuilder referenceURLBuilder = RelativeReferenceURLBuilderFactory
            .getRelativeReferenceURLBuilder(fileProperties);
      String artifactName = referenceURLBuilder.getFileName(repositoryUrl, fileProperties);

      // Initialize a reference resolver to the folder of this repositoryUrl.
      ReferenceResolver referenceResolver = new ReferenceResolver(repositoryUrl, repositorySecret, disableSSLValidation,
            referenceURLBuilder);
      return importServiceDefinition(localFile, referenceResolver, new ArtifactInfo(artifactName, mainArtifact));
   }


   /**
    * Import definitions of services and bounded resources and messages into Microcks repository. This uses a
    * MockRepositoryImporter under hood.
    * @param repositoryFile    The File for mock repository.
    * @param referenceResolver The Resolver to be used during import (may be null).
    * @param artifactInfo      The essential information on Artifact to import.
    * @return The list of imported Services
    * @throws MockRepositoryImportException if something goes wrong (URL not reachable nor readable, etc...)
    */
   public List<Service> importServiceDefinition(File repositoryFile, ReferenceResolver referenceResolver,
         ArtifactInfo artifactInfo) throws MockRepositoryImportException {
      // Retrieve the correct importer based on file path.
      MockRepositoryImporter importer = null;
      try {
         importer = MockRepositoryImporterFactory.getMockRepositoryImporter(repositoryFile, referenceResolver);
      } catch (IOException ioe) {
         throw new MockRepositoryImportException(ioe.getMessage(), ioe);
      }

      Service reference = null;
      boolean serviceUpdate = false;

      List<Service> services = importer.getServiceDefinitions();
      for (Service service : services) {
         Service existingService = serviceRepository.findByNameAndVersion(service.getName(), service.getVersion());
         log.debug("Service [{}, {}] exists ? {}", service.getName(), service.getVersion(), existingService != null);

         // If it's the main artifact: retrieve previous id and props if update, save anyway.
         if (artifactInfo.isMainArtifact()) {
            if (existingService != null) {
               // Retrieve its previous identifier and metadata
               // (backup metadata that may have been imported with extensions).
               Metadata backup = service.getMetadata();
               service.setId(existingService.getId());
               service.setMetadata(existingService.getMetadata());
               // If there was metadata found through extensions, overwrite historical ones.
               if (backup != null) {
                  existingService.getMetadata().setLabels(backup.getLabels());
                  existingService.getMetadata().setAnnotations(backup.getAnnotations());
               }

               // Keep its overriden operation properties.
               copyOverridenOperations(existingService, service);
               serviceUpdate = true;
            }
            if (service.getMetadata() == null) {
               service.setMetadata(new Metadata());
            }

            // For services of type EVENT, we should put default values on frequency and bindings.
            if (ServiceType.EVENT.equals(service.getType())) {
               manageEventServiceDefaults(service);
            }

            service.getMetadata().objectUpdated();
            service.setSourceArtifact(artifactInfo.getArtifactName());
            service = serviceRepository.save(service);

            // We're dealing with main artifact so reference is saved or updated one.
            reference = service;
         } else {
            // It's a secondary artifact just for messages or metadata. We'll have problems if not having an existing service...
            if (existingService == null) {
               log.warn(
                     "Trying to import {} as a secondary artifact but there's no existing [{}, {}] Service. Just skipping.",
                     artifactInfo.getArtifactName(), service.getName(), service.getVersion());
               break;
            }

            // If metadata and operation properties found through metadata import,
            // update the existing service with them.
            if (service.getMetadata() != null) {
               existingService.getMetadata().setLabels(service.getMetadata().getLabels());
               existingService.getMetadata().setAnnotations(service.getMetadata().getAnnotations());
            }
            for (Operation operation : service.getOperations()) {
               Operation existingOp = existingService.getOperations().stream()
                     .filter(op -> op.getName().equals(operation.getName())).findFirst().orElse(null);
               if (existingOp != null) {
                  if (operation.getDefaultDelay() != null) {
                     existingOp.setDefaultDelay(operation.getDefaultDelay());
                  }
                  if (operation.getDefaultDelayStrategy() != null) {
                     existingOp.setDefaultDelayStrategy(operation.getDefaultDelayStrategy());
                  }
                  if (operation.getDispatcher() != null) {
                     existingOp.setDispatcher(operation.getDispatcher());
                  }
                  if (operation.getDispatcherRules() != null) {
                     existingOp.setDispatcherRules(operation.getDispatcherRules());
                  }
                  if (operation.getParameterConstraints() != null) {
                     if (existingOp.getParameterConstraints() == null) {
                        existingOp.setParameterConstraints(operation.getParameterConstraints());
                     } else {
                        existingOp.getParameterConstraints().addAll(operation.getParameterConstraints());
                     }
                  }
               }
            }

            // We're dealing with secondary artifact so reference is the pre-existing one.
            // Moreover, we should replace current imported service (unbound/unsaved)
            // by reference in the results list.
            reference = existingService;
            services.remove(service);
            services.add(reference);
         }

         // Remove resources and messages previously attached to service.
         updateArtifactResources(reference, importer, service, artifactInfo);
         updateArtifactMessages(reference, importer, service, artifactInfo);

         // When extracting message information, we may have modified Operation because discovered new resource paths
         // depending on variable URI parts. As a consequence, we got to update Service in repository.
         serviceRepository.save(reference);

         // Publish a Service update event before returning.
         publishServiceChangeEvent(reference, serviceUpdate ? ChangeType.UPDATED : ChangeType.CREATED);
      }
      log.info("Having imported {} services definitions into repository", services.size());
      return services;
   }

   /**
    * Create a new Service concerning a GenericResource for dynamic mocking.
    * @param name             The name of the new Service to create
    * @param version          The version of the new Service to create
    * @param resource         The resource that will be exposed as CRUD operations for this service
    * @param referencePayload An optional reference payload if provided
    * @return The newly created Service object
    * @throws EntityAlreadyExistsException if a Service with same name and version is already present in store
    */
   public Service createGenericResourceService(String name, String version, String resource, String referencePayload)
         throws EntityAlreadyExistsException {
      log.info("Creating a new Service '{}-{}' for generic resource {}", name, version, resource);

      // Check if corresponding Service already exists.
      Service existingService = serviceRepository.findByNameAndVersion(name, version);
      if (existingService != null) {
         log.warn("A Service '{}-{}' is already existing. Throwing an Exception", name, version);
         throw new EntityAlreadyExistsException(
               String.format("Service '%s-%s' is already present in store", name, version));
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
      log.info("Having created Service '{}' for generic resource {}", service.getId(), resource);

      // If reference payload is provided, record a first resource.
      if (referencePayload != null) {
         GenericResource genericResource = new GenericResource();
         genericResource.setServiceId(service.getId());
         genericResource.setReference(true);

         try {
            Document document = Document.parse(referencePayload);
            genericResource.setPayload(document);
            genericResourceRepository.save(genericResource);
         } catch (JsonParseException jpe) {
            log.error("Cannot parse the provided reference payload as JSON: {}", referencePayload);
            log.error("Reference is ignored, please provide JSON the next time");
         }
      }

      // Publish a Service create event before returning.
      publishServiceChangeEvent(service, ChangeType.CREATED);

      return service;
   }

   /**
    * Create a new Service concerning a Generic Event for dynamic mocking.
    * @param name             The name of the new Service to create
    * @param version          The version of the new Service to create
    * @param event            The event that will be exposed through a SUBSCRIBE operation
    * @param referencePayload An optional reference payload if provided
    * @return The newly created Service object
    * @throws EntityAlreadyExistsException if a Service with same name and version is already present in store
    */
   public Service createGenericEventService(String name, String version, String event, String referencePayload)
         throws EntityAlreadyExistsException {
      log.info("Creating a new Service '{}-{}' for generic event {}", name, version, event);

      // Check if corresponding Service already exists.
      Service existingService = serviceRepository.findByNameAndVersion(name, version);
      if (existingService != null) {
         log.warn("A Service '{}-{}' is already existing. Throwing an Exception", name, version);
         throw new EntityAlreadyExistsException(
               String.format("Service '%s-%s' is already present in store", name, version));
      }
      // Create new service with GENERIC_EVENT type.
      Service service = new Service();
      service.setName(name);
      service.setVersion(version);
      service.setType(ServiceType.GENERIC_EVENT);
      service.setMetadata(new Metadata());

      // Now create basic crud operations for the resource.
      Operation subscribeOp = new Operation();
      subscribeOp.setName("SUBSCRIBE " + event);
      subscribeOp.setMethod("SUBSCRIBE");

      subscribeOp.setDefaultDelay(defaultAsyncFrequency);
      // Create bindings for Kafka and Websockets.
      Binding kafkaBinding = new Binding(BindingType.KAFKA);
      kafkaBinding.setKeyType("string");
      Binding wsBinding = new Binding(BindingType.WS);
      wsBinding.setMethod("POST");
      subscribeOp.addBinding(BindingType.KAFKA.name(), kafkaBinding);
      subscribeOp.addBinding(BindingType.WS.name(), wsBinding);
      service.addOperation(subscribeOp);

      serviceRepository.save(service);
      log.info("Having created Service '{}' for generic event {}", service.getId(), event);

      // If reference payload is provided, record a first resource.
      if (referencePayload != null) {
         Resource artifact = new Resource();
         artifact.setName(event + "-asyncapi.yaml");
         artifact.setType(ResourceType.ASYNC_API_SPEC);
         artifact.setServiceId(service.getId());
         artifact.setSourceArtifact(event + "-asyncapi.yaml");
         artifact.setContent(buildAsyncAPISpecContent(service, event, referencePayload));
         resourceRepository.save(artifact);

         EventMessage eventMessage = new EventMessage();
         eventMessage.setName("Reference");
         eventMessage.setContent(referencePayload);
         eventMessage.setOperationId(IdBuilder.buildOperationId(service, subscribeOp));
         eventMessage.setMediaType("application/json");
         eventMessageRepository.save(eventMessage);
         log.info("Having created resource '{}' for Service '{}'", artifact.getId(), service.getId());
      }

      // Publish a Service create event before returning.
      publishServiceChangeEvent(service, ChangeType.CREATED);

      return service;
   }

   /**
    * Remove a Service and its bound documents using the service id.
    * @param id       The identifier of service to remove.
    * @param userInfo The current user information to check if authorized to delete
    * @return True if service is not found or found and deleted, false otherwise.
    */
   public Boolean deleteService(String id, UserInfo userInfo) {
      // Get service to remove.
      Service service = getServiceById(id);

      if (service == null) {
         log.warn("Service [{}] not found for deletion", id);
         return true;
      }

      if (authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForService(userInfo, AuthorizationChecker.ROLE_MANAGER, service)) {
         // Delete all resources first.
         resourceRepository.deleteAll(resourceRepository.findByServiceId(id));

         // Delete all tests related to service.
         testResultRepository.deleteAll(testResultRepository.findByServiceId(id));

         // Delete all requests and responses bound to service operation.
         for (Operation operation : service.getOperations()) {
            String operationId = IdBuilder.buildOperationId(service, operation);
            requestRepository.deleteAll(requestRepository.findByOperationId(operationId));
            responseRepository.deleteAll(responseRepository.findByOperationId(operationId));
            eventMessageRepository.deleteAll(eventMessageRepository.findByOperationId(operationId));
         }

         // Finally delete service and publish event.
         serviceRepository.delete(service);
         publishServiceChangeEvent(service, ChangeType.DELETED);
         log.info("Service [{}] has been fully deleted", id);
         return true;
      }
      return false;
   }

   /**
    * Update the metadata for a Service. Only deals with annotations and labels and takes care of updated the
    * <b>lastUpdate</b> marker.
    * @param id       The identifier of service to update metadata for
    * @param metadata The new metadata for this Service
    * @param userInfo The current user information to check if authorized to do the update
    * @return True if service has been found and updated, false otherwise.
    */
   public Boolean updateMetadata(String id, Metadata metadata, UserInfo userInfo) {
      // Get service to update.
      Service service = getServiceById(id);
      if (service != null
            && authorizationChecker.hasRoleForService(userInfo, AuthorizationChecker.ROLE_MANAGER, service)) {
         service.getMetadata().setLabels(metadata.getLabels());
         service.getMetadata().setAnnotations(metadata.getAnnotations());
         service.getMetadata().objectUpdated();
         serviceRepository.save(service);

         // Publish a Service update event before returning.
         publishServiceChangeEvent(service, ChangeType.UPDATED);
         return true;
      }
      return false;
   }

   /**
    * Update the default delay of a Service operation
    * @param id              The identifier of service to update operation for
    * @param operationName   The name of operation to update delay for
    * @param dispatcher      The dispatcher to use for this operation
    * @param dispatcherRules The dispatcher rules to use for this operation
    * @param delay           The new delay value for operation
    * @param delayStrategy   The new delay strategy value for operation
    * @param constraints     Constraints for this operation parameters
    * @param userInfo        The current user information to check if authorized to do the update
    * @return True if operation has been found and updated, false otherwise.
    */
   public Boolean updateOperation(String id, String operationName, String dispatcher, String dispatcherRules,
         Long delay, String delayStrategy, Set<ParameterConstraint> constraints, UserInfo userInfo) {
      // Get service to update.
      Service service = getServiceById(id);
      if (service != null
            && authorizationChecker.hasRoleForService(userInfo, AuthorizationChecker.ROLE_MANAGER, service)) {
         for (Operation operation : service.getOperations()) {
            if (operation.getName().equals(operationName)) {
               operation.setDispatcher(dispatcher);
               operation.setDispatcherRules(dispatcherRules);
               operation.setParameterConstraints(constraints);
               operation.setDefaultDelay(delay);
               operation.setDefaultDelayStrategy(delayStrategy);
               operation.setOverride(true);
               serviceRepository.save(service);

               // Publish a Service update event before returning.
               publishServiceChangeEvent(service, ChangeType.UPDATED);
               return true;
            }
         }
      }
      return false;
   }

   /**
    * Add new sample exchanges to an existing service.
    * @param id            The identifier of service to add exchanges for
    * @param operationName The name of operation to add exchanges for
    * @param exchanges     A list of exchanges to add to the corresponding service operation
    * @param userInfo      The current user information to check if authorized to do the update
    * @return True if service operation has been found and updated, false otherwise.
    */
   public Boolean addAICopilotExchangesToServiceOperation(String id, String operationName, List<Exchange> exchanges,
         UserInfo userInfo) {
      // Get service to update.
      Service service = getServiceById(id);
      if (service != null
            && authorizationChecker.hasRoleForService(userInfo, AuthorizationChecker.ROLE_MANAGER, service)) {
         for (Operation operation : service.getOperations()) {
            if (operation.getName().equals(operationName)) {
               String operationId = IdBuilder.buildOperationId(service, operation);

               for (Exchange exchange : exchanges) {
                  if (exchange instanceof RequestResponsePair pair) {
                     // Associate request and response with operation and artifact.
                     pair.getRequest().setOperationId(operationId);
                     pair.getResponse().setOperationId(operationId);
                     pair.getRequest().setSourceArtifact(AI_COPILOT_SOURCE);
                     pair.getResponse().setSourceArtifact(AI_COPILOT_SOURCE);

                     // Save response and associate request with response before saving it.
                     responseRepository.save(pair.getResponse());
                     pair.getRequest().setResponseId(pair.getResponse().getId());
                     requestRepository.save(pair.getRequest());

                  } else if (exchange instanceof UnidirectionalEvent event) {
                     // Associate event message with operation and artifact before saving it.
                     event.getEventMessage().setOperationId(operationId);
                     event.getEventMessage().setSourceArtifact(AI_COPILOT_SOURCE);
                     eventMessageRepository.save(event.getEventMessage());
                  }
               }
               // Publish a Service update event before returning.
               publishServiceChangeEvent(service, ChangeType.UPDATED);
               return true;
            }
         }
      }
      return false;
   }

   /**
    * Remove sample exchanges from an existing service.
    * @param id                The identifier of service to remove exchanges for
    * @param exchangeSelection The selection of exchanges to remove
    * @param userInfo          The current user information to check if authorized to do the update
    * @return True if service exchanges have been found and removed, false otherwise.
    */
   public Boolean removeAICopilotExchangesFromService(String id, ExchangeSelection exchangeSelection,
         UserInfo userInfo) {
      // Get service to update.
      Service service = getServiceById(id);
      if (service != null
            && authorizationChecker.hasRoleForService(userInfo, AuthorizationChecker.ROLE_MANAGER, service)) {
         for (Operation operation : service.getOperations()) {
            if (exchangeSelection.getExchanges().containsKey(operation.getName())) {
               log.debug("Removing AI Copilot exchanges for operation {}", operation.getName());
               String operationId = IdBuilder.buildOperationId(service, operation);
               List<String> exchangeNames = exchangeSelection.getExchanges().get(operation.getName());

               if (!ServiceType.EVENT.equals(service.getType())) {
                  // Remove all the requests and responses associated with the operation and AI Copilot.
                  requestRepository
                        .deleteAll(requestRepository.findByOperationIdAndSourceArtifact(operationId, AI_COPILOT_SOURCE)
                              .stream().filter(request -> exchangeNames.contains(request.getName())).toList());
                  responseRepository
                        .deleteAll(responseRepository.findByOperationIdAndSourceArtifact(operationId, AI_COPILOT_SOURCE)
                              .stream().filter(response -> exchangeNames.contains(response.getName())).toList());
               } else {
                  // Remove all the event messages associated with the operation and AI Copilot.
                  eventMessageRepository.deleteAll(eventMessageRepository
                        .findByOperationIdAndSourceArtifact(operationId, AI_COPILOT_SOURCE).stream()
                        .filter(eventMessage -> exchangeNames.contains(eventMessage.getName())).toList());
               }
            }
         }
         // Publish a Service update event before returning.
         publishServiceChangeEvent(service, ChangeType.UPDATED);
         return true;
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
                  op.setDefaultDelayStrategy(existingOperation.getDefaultDelayStrategy());
                  op.setDispatcher(existingOperation.getDispatcher());
                  op.setDispatcherRules(existingOperation.getDispatcherRules());
                  op.setParameterConstraints(existingOperation.getParameterConstraints());
                  op.setOverride(true);
               }
            }
         }
      }
   }

   /** Manage the default values for Service of type EVENT */
   private void manageEventServiceDefaults(Service service) {
      // For services of type EVENT, we should put default values on frequency and bindings.
      if (service.getType().equals(ServiceType.EVENT)) {
         for (Operation operation : service.getOperations()) {
            if (operation.getDefaultDelay() == null) {
               operation.setDefaultDelay(defaultAsyncFrequency);
            }
            if (operation.getBindings() == null || operation.getBindings().isEmpty()) {
               operation.addBinding(defaultAsyncBinding, new Binding(BindingType.valueOf(defaultAsyncBinding)));
            }
         }
      }
   }

   /** Manage the update of previous artifact resources with new imported ones. */
   private void updateArtifactResources(Service reference, MockRepositoryImporter importer, Service service,
         ArtifactInfo artifactInfo) throws MockRepositoryImportException {

      // Remove resources previously attached to service.
      List<Resource> existingResources = resourceRepository.findByServiceIdAndSourceArtifact(reference.getId(),
            artifactInfo.getArtifactName());
      if (existingResources != null && !existingResources.isEmpty()) {
         resourceRepository.deleteAll(existingResources);
      }

      // Save new resources.
      List<Resource> resources = importer.getResourceDefinitions(service);
      for (Resource resource : resources) {
         resource.setServiceId(reference.getId());
         resource.setSourceArtifact(artifactInfo.getArtifactName());
         resource.setMainArtifact(artifactInfo.isMainArtifact());
      }
      resourceRepository.saveAll(resources);
   }

   /** Manage the update of previous artifact messages with new imported ones. */
   private void updateArtifactMessages(Service reference, MockRepositoryImporter importer, Service service,
         ArtifactInfo artifactInfo) throws MockRepositoryImportException {

      for (Operation operation : reference.getOperations()) {
         String operationId = IdBuilder.buildOperationId(reference, operation);

         // Remove messages previously attached to service.
         requestRepository.deleteAll(
               requestRepository.findByOperationIdAndSourceArtifact(operationId, artifactInfo.getArtifactName()));
         responseRepository.deleteAll(
               responseRepository.findByOperationIdAndSourceArtifact(operationId, artifactInfo.getArtifactName()));
         eventMessageRepository.deleteAll(
               eventMessageRepository.findByOperationIdAndSourceArtifact(operationId, artifactInfo.getArtifactName()));

         // Save new messages. We should use 'reference' here instead of 'service' as it may contain
         // additional information to proper import messages (such as dispatch criteria inferring).
         List<Exchange> exchanges = importer.getMessageDefinitions(reference, operation);

         for (Exchange exchange : exchanges) {
            if (exchange instanceof RequestResponsePair pair) {
               // Associate request and response with operation and artifact.
               pair.getRequest().setOperationId(operationId);
               pair.getResponse().setOperationId(operationId);
               pair.getRequest().setSourceArtifact(artifactInfo.getArtifactName());
               pair.getResponse().setSourceArtifact(artifactInfo.getArtifactName());

               // Save response and associate request with response before saving it.
               responseRepository.save(pair.getResponse());
               pair.getRequest().setResponseId(pair.getResponse().getId());
               requestRepository.save(pair.getRequest());

            } else if (exchange instanceof UnidirectionalEvent event) {
               // Associate event message with operation and artifact before saving it..
               event.getEventMessage().setOperationId(operationId);
               event.getEventMessage().setSourceArtifact(artifactInfo.getArtifactName());
               eventMessageRepository.save(event.getEventMessage());
            }
         }
      }
   }

   /** Build generic event service associated AsyncAPI spec content. */
   private String buildAsyncAPISpecContent(Service service, String event, String referencePayload) {
      InputStream stream = null;
      JsonNode referenceSchema = null;

      try {
         stream = ResourceUtil.getClasspathResource("templates/asyncapi-2.4.yaml");
         referenceSchema = OpenAPISchemaBuilder.buildTypeSchemaFromJson(referencePayload);
         return ResourceUtil.replaceTemplatesInSpecStream(stream, service, event, referenceSchema, referencePayload);
      } catch (IOException ioe) {
         log.error("Exception while building ASyncAPISpec for Service '{}': {}", service.getId(), ioe.getMessage());
         return "";
      }
   }

   /** Publish a ServiceChangeEvent towards minions or some other consumers. */
   private void publishServiceChangeEvent(Service service, ChangeType changeType) {
      ServiceChangeEvent event = new ServiceChangeEvent(this, service.getId(), changeType);
      applicationContext.publishEvent(event);
      log.debug("Service change event has been published");
   }
}
