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
package io.github.microcks.web;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.domain.UnidirectionalEvent;
import io.github.microcks.repository.CustomServiceRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.security.UserInfo;
import io.github.microcks.service.MessageService;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.EntityAlreadyExistsException;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.web.dto.GenericResourceServiceDTO;
import io.github.microcks.web.dto.OperationOverrideDTO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Rest controller for API defined on services.
 * @author laurent
 */
@RestController
@RequestMapping("/api")
public class ServiceController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ServiceController.class);

   @Autowired
   private ServiceService serviceService;

   @Autowired
   private ServiceRepository serviceRepository;

   @Autowired
   private MessageService messageService;


   @RequestMapping(value = "/services", method = RequestMethod.GET)
   public List<Service> listServices(
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size
      ) {
      log.debug("Getting service list for page {} and size {}", page, size);
      return serviceRepository.findAll(PageRequest.of(page, size,
            Sort.by(Sort.Direction.ASC, "name", "version"))).getContent();
   }

   @RequestMapping(value = "/services/search", method = RequestMethod.GET)
   public List<Service> searchServices(
         //@RequestParam(value = "name", required = false) String name,
         //@RequestParam(value = "labels", required = false) Map<String, String> labels
         @RequestParam Map<String, String> queryMap
      ) {
      // Parse params from queryMap.
      String name = null;
      Map<String, String> labels = new HashMap<>();
      for (String paramKey: queryMap.keySet()) {
         if ("name".equals(paramKey)) {
            name = queryMap.get("name");
         }
         if (paramKey.startsWith("labels.")) {
            labels.put(paramKey.substring(paramKey.indexOf('.') + 1), queryMap.get(paramKey));
         }
      }

      if (labels == null || labels.isEmpty()) {
         log.debug("Searching services corresponding to name {}", name);
         return serviceRepository.findByNameLike(name);
      }
      if (name == null || name.trim().length() == 0) {
         log.debug("Searching services corresponding to labels {}", labels);
         return serviceRepository.findByLabels(labels);
      }
      log.debug("Searching services corresponding to name {} and labels {}", name, labels);
      return serviceRepository.findByLabelsAndNameLike(labels, name);
   }

   @RequestMapping(value = "/services/count", method = RequestMethod.GET)
   public Map<String, Long> countServices() {
      log.debug("Counting services...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", serviceRepository.count());
      return counter;
   }

   @RequestMapping(value = "/services/map", method = RequestMethod.GET)
   public Map<String, Integer> getServicesMap() {
      log.debug("Counting services by type...");
      Map<String, Integer> map = new HashMap<>();
      List<CustomServiceRepository.ServiceCount> results = serviceRepository.countServicesByType();
      for (CustomServiceRepository.ServiceCount count : results) {
         map.put(count.getType(), count.getNumber());
      }
      return map;
   }

   @RequestMapping(value = "/services/labels", method = RequestMethod.GET)
   public Map<String, String[]> getServicesLabels() {
      log.debug("Retrieving available services labels...");
      Map<String, String[]> labelValues = new HashMap<>();
      List<CustomServiceRepository.LabelValues> results = serviceRepository.listLabels();
      for (CustomServiceRepository.LabelValues values : results) {
         labelValues.put(values.getKey(), values.getValues());
      }
      return labelValues;
   }

   @RequestMapping(value = "/services/{id:.+}", method = RequestMethod.GET)
   public ResponseEntity<?> getService(
         @PathVariable("id") String serviceId,
         @RequestParam(value = "messages", required = false, defaultValue = "true") boolean messages
      ) {
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
         service = serviceRepository.findById(serviceId).orElse(null);
      }

      if (messages) {
         // Put messages into a map where key is operation name.
         Map<String, List<? extends Exchange>> messagesMap = new HashMap<>();
         for (Operation operation : service.getOperations()) {
            if (service.getType() == ServiceType.EVENT) {
               // If an event, we should explicitly retrieve event messages.
               List<UnidirectionalEvent> events = messageService.getEventByOperation(
                     IdBuilder.buildOperationId(service, operation));
               messagesMap.put(operation.getName(), events);
            } else {
               // Otherwise we have traditional request / response pairs.
               List<RequestResponsePair> pairs = messageService.getRequestResponseByOperation(
                     IdBuilder.buildOperationId(service, operation));
               messagesMap.put(operation.getName(), pairs);
            }
         }
         return new ResponseEntity<>(new ServiceView(service, messagesMap), HttpStatus.OK);
      }
      return new ResponseEntity<>(service, HttpStatus.OK);
   }

   @RequestMapping(value = "/services/generic", method = RequestMethod.POST)
   public ResponseEntity<Service> createGenericResourceService(@RequestBody GenericResourceServiceDTO serviceDTO) {
      log.debug("Creating a new Service '{}-{}' for generic resource '{}'", serviceDTO.getName(), serviceDTO.getVersion(), serviceDTO.getResource());

      try{
         Service service = serviceService.createGenericResourceService(serviceDTO.getName(), serviceDTO.getVersion(), serviceDTO.getResource());
         return new ResponseEntity<>(service, HttpStatus.CREATED);
      } catch (EntityAlreadyExistsException eaee) {
         log.error("Service '{}-{} already exists'", serviceDTO.getName(), serviceDTO.getVersion());
         return new ResponseEntity<>(HttpStatus.CONFLICT);
      }
   }

   @RequestMapping(value = "/services/{id}/metadata", method = RequestMethod.PUT)
   public ResponseEntity<?> updateMetadata(@PathVariable("id") String serviceId,
         @RequestBody Metadata metadata,
         UserInfo userInfo) {
      log.debug("Updating the metadata of service {}", serviceId);
      boolean result = serviceService.updateMetadata(serviceId, metadata, userInfo);
      if (result){
         return new ResponseEntity<>(HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
   }

   @RequestMapping(value = "/services/{id}/operation", method = RequestMethod.PUT)
   public ResponseEntity<?> overrideServiceOperation(
         @PathVariable("id") String serviceId,
         @RequestParam(value = "operationName") String operationName,
         @RequestBody OperationOverrideDTO operationOverride,
         UserInfo userInfo
      ) {
      log.debug("Updating operation {} of service {}", operationName, serviceId);
      log.debug("ParameterConstraints?: " + operationOverride.getParameterConstraints());
      boolean result = serviceService.updateOperation(serviceId, operationName, operationOverride.getDispatcher(),
            operationOverride.getDispatcherRules(), operationOverride.getDefaultDelay(), operationOverride.getParameterConstraints(), userInfo);
      if (result){
         return new ResponseEntity<>(HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
   }

   @RequestMapping(value = "/services/{id}", method = RequestMethod.DELETE)
   public ResponseEntity<String> deleteService(@PathVariable("id") String serviceId, UserInfo userInfo) {
      log.debug("Removing service with id {}", serviceId);
      serviceService.deleteService(serviceId);
      return new ResponseEntity<>(HttpStatus.OK);
   }
}
