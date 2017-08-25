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
package com.github.lbroudoux.microcks.web;

import com.github.lbroudoux.microcks.domain.Operation;
import com.github.lbroudoux.microcks.domain.Service;
import com.github.lbroudoux.microcks.repository.ServiceRepository;
import com.github.lbroudoux.microcks.service.MessageService;
import com.github.lbroudoux.microcks.service.RequestResponsePair;
import com.github.lbroudoux.microcks.service.ServiceService;
import com.github.lbroudoux.microcks.util.EntityAlreadyExistsException;
import com.github.lbroudoux.microcks.util.IdBuilder;
import com.github.lbroudoux.microcks.web.dto.GenericResourceServiceDTO;
import com.github.lbroudoux.microcks.web.dto.ServiceViewDTO;
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
      return serviceRepository.findAll(new PageRequest(page, size,
            new Sort(Sort.Direction.ASC, "name", "version"))).getContent();
   }

   @RequestMapping(value = "/services/search", method = RequestMethod.GET)
   public List<Service> searchServices(@RequestParam(value = "name") String name) {
      log.debug("Searching services corresponding to {}", name);
      return serviceRepository.findByNameLike(name);
   }

   @RequestMapping(value = "/services/count", method = RequestMethod.GET)
   public Map<String, Long> countServices() {
      log.debug("Counting services...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", serviceRepository.count());
      return counter;
   }

   @RequestMapping(value = "/services/{id}", method = RequestMethod.GET)
   public ResponseEntity<?> getService(
         @PathVariable("id") String serviceId,
         @RequestParam(value = "messages", required = false, defaultValue = "true") boolean messages
      ) {
      log.debug("Retrieving service with id {}", serviceId);
      Service service = serviceRepository.findOne(serviceId);

      if (messages) {
         Map<String, List<RequestResponsePair>> messagesMap = new HashMap<String, List<RequestResponsePair>>();
         for (Operation operation : service.getOperations()) {
            List<RequestResponsePair> pairs = messageService.getRequestResponseByOperation(
                  IdBuilder.buildOperationId(service, operation));
            messagesMap.put(operation.getName(), pairs);
         }
         return new ResponseEntity<>(new ServiceViewDTO(service, messagesMap), HttpStatus.OK);
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

   @RequestMapping(value = "/services/{id}/operationDelay", method = RequestMethod.PUT)
   public ResponseEntity<?> updateServiceOperationDelay(
         @PathVariable("id") String serviceId,
         @RequestParam(value = "operationName") String operationName,
         @RequestParam(value = "delay") Long delay
      ) {
      log.debug("Updating delay for operation {} of service {}", operationName, serviceId);
      boolean result = serviceService.updateOperationDelay(serviceId, operationName, delay);
      if (result){
         return new ResponseEntity<>(HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
   }
}
