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
package io.github.microcks.web;

import io.github.microcks.domain.Exchange;
import io.github.microcks.domain.Metadata;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.RequestResponsePair;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceType;
import io.github.microcks.domain.ServiceView;
import io.github.microcks.repository.CustomServiceRepository;
import io.github.microcks.repository.ServiceRepository;
import io.github.microcks.security.UserInfo;
import io.github.microcks.service.MessageService;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.EntityAlreadyExistsException;
import io.github.microcks.util.IdBuilder;
import io.github.microcks.util.SafeLogger;
import io.github.microcks.web.dto.GenericResourceServiceDTO;
import io.github.microcks.web.dto.OperationOverrideDTO;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
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
   public ServiceController(ServiceService serviceService, ServiceRepository serviceRepository,
         MessageService messageService) {
      this.serviceService = serviceService;
      this.serviceRepository = serviceRepository;
      this.messageService = messageService;
   }

   @GetMapping(value = "/services")
   public List<Service> listServices(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size) {
      log.debug("Getting service list for page {} and size {}", page, size);
      return serviceRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name", "version")))
            .getContent();
   }

   @GetMapping(value = "/services/search")
   public List<Service> searchServices(@RequestParam Map<String, String> queryMap) {
      SearchCriteria criteria = parseSearchCriteria(queryMap);
      Sort sort = parseSort(criteria.sort());

      if (criteria.page() != null || criteria.size() != null) {
         int page = criteria.page() != null ? criteria.page() : 0;
         int size = criteria.size() != null ? criteria.size() : 20;
         log.debug("Searching services with page {} and size {}", page, size);
         Pageable pageable = PageRequest.of(page, size, sort);
         return serviceRepository.search(criteria.name(), criteria.labels(), criteria.version(), criteria.type(),
               pageable);
      }

      log.debug("Searching services without pagination");
      return serviceRepository.search(criteria.name(), criteria.labels(), criteria.version(), criteria.type(), sort);
   }

   @GetMapping(value = "/services/search/count")
   public Map<String, Long> countSearchServices(@RequestParam Map<String, String> queryMap) {
      log.debug("Counting services matching search criteria...");
      SearchCriteria criteria = parseSearchCriteria(queryMap);
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter",
            serviceRepository.countSearch(criteria.name(), criteria.labels(), criteria.version(), criteria.type()));
      return counter;
   }

   @GetMapping(value = "/services/count")
   public Map<String, Long> countServices() {
      log.debug("Counting services...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", serviceRepository.count());
      return counter;
   }

   @GetMapping(value = "/services/map")
   public Map<String, Integer> getServicesMap() {
      log.debug("Counting services by type...");
      Map<String, Integer> map = new HashMap<>();
      List<CustomServiceRepository.ServiceCount> results = serviceRepository.countServicesByType();
      for (CustomServiceRepository.ServiceCount count : results) {
         map.put(count.getType(), count.getNumber());
      }
      return map;
   }

   @GetMapping(value = "/services/labels")
   public Map<String, String[]> getServicesLabels() {
      log.debug("Retrieving available services labels...");
      Map<String, String[]> labelValues = new HashMap<>();
      List<CustomServiceRepository.LabelValues> results = serviceRepository.listLabels();
      for (CustomServiceRepository.LabelValues values : results) {
         labelValues.put(values.getKey(), values.getValues());
      }
      return labelValues;
   }

   @GetMapping(value = "/services/{id:.+}", produces = "application/json")
   public ResponseEntity<Object> getService(@PathVariable("id") String serviceId,
         @RequestParam(value = "messages", required = false, defaultValue = "true") boolean messages) {
      log.debug("Retrieving service with id {}", serviceId);

      // Just retrieve the service and return it.
      Service service = serviceService.getServiceById(serviceId);
      if (service == null) {
         return new ResponseEntity<>(HttpStatus.NOT_FOUND);
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
         return new ResponseEntity<>(new ServiceView(service, messagesMap), HttpStatus.OK);
      }
      return new ResponseEntity<>(service, HttpStatus.OK);
   }

   @PostMapping(value = "/services/generic")
   public ResponseEntity<Service> createGenericResourceService(@RequestBody GenericResourceServiceDTO serviceDTO) {
      log.debug("Creating a new Service '{}-{}' for generic resource '{}'", serviceDTO.getName(),
            serviceDTO.getVersion(), serviceDTO.getResource());

      try {
         Service service = serviceService.createGenericResourceService(serviceDTO.getName(), serviceDTO.getVersion(),
               serviceDTO.getResource(), serviceDTO.getReferencePayload());
         return new ResponseEntity<>(service, HttpStatus.CREATED);
      } catch (EntityAlreadyExistsException eaee) {
         log.error("Service '{}-{} already exists'", serviceDTO.getName(), serviceDTO.getVersion());
         return new ResponseEntity<>(HttpStatus.CONFLICT);
      }
   }

   @PostMapping(value = "/services/generic/event")
   public ResponseEntity<Service> createGenericEventService(@RequestBody GenericResourceServiceDTO serviceDTO) {
      log.debug("Creating a new Service '{}-{}' for generic resource '{}'", serviceDTO.getName(),
            serviceDTO.getVersion(), serviceDTO.getResource());

      try {
         Service service = serviceService.createGenericEventService(serviceDTO.getName(), serviceDTO.getVersion(),
               serviceDTO.getResource(), serviceDTO.getReferencePayload());
         return new ResponseEntity<>(service, HttpStatus.CREATED);
      } catch (EntityAlreadyExistsException eaee) {
         log.error("Service '{}-{} already exists'", serviceDTO.getName(), serviceDTO.getVersion());
         return new ResponseEntity<>(HttpStatus.CONFLICT);
      }
   }

   @PutMapping(value = "/services/{id}/metadata")
   public ResponseEntity<Object> updateMetadata(@PathVariable("id") String serviceId, @RequestBody Metadata metadata,
         UserInfo userInfo) {
      log.debug("Updating the metadata of service {}", serviceId);
      boolean result = serviceService.updateMetadata(serviceId, metadata, userInfo);
      if (result) {
         return new ResponseEntity<>(HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @PutMapping(value = "/services/{id}/operation")
   public ResponseEntity<Object> overrideServiceOperation(@PathVariable("id") String serviceId,
         @RequestParam(value = "operationName") String operationName,
         @RequestBody OperationOverrideDTO operationOverride, UserInfo userInfo) {
      log.debug("Updating operation {} of service {}", operationName, serviceId);
      log.debug("ParameterConstraints?: {}", operationOverride.getParameterConstraints());
      boolean result = serviceService.updateOperation(serviceId, operationName, operationOverride.getDispatcher(),
            operationOverride.getDispatcherRules(), operationOverride.getDefaultDelay(),
            operationOverride.getDefaultDelayStrategy(), operationOverride.getParameterConstraints(), userInfo);
      if (result) {
         return new ResponseEntity<>(HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @DeleteMapping(value = "/services/{id}")
   public ResponseEntity<String> deleteService(@PathVariable("id") String serviceId, UserInfo userInfo) {
      log.debug("Removing service with id {}", serviceId);
      boolean result = serviceService.deleteService(serviceId, userInfo);
      if (result) {
         return new ResponseEntity<>(HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   private SearchCriteria parseSearchCriteria(Map<String, String> queryMap) {
      String name = null;
      String version = null;
      ServiceType serviceType = null;
      Integer page = null;
      Integer size = null;
      String sort = null;
      Map<String, String> labels = new HashMap<>();

      for (Map.Entry<String, String> entry : queryMap.entrySet()) {
         String key = entry.getKey();
         String value = entry.getValue();
         if ("name".equals(key)) {
            if (value != null && !value.trim().isEmpty()) {
               name = value.trim();
            }
         } else if ("version".equals(key)) {
            if (value != null && !value.trim().isEmpty()) {
               version = value.trim();
            }
         } else if ("type".equals(key)) {
            if (value != null && !value.trim().isEmpty()) {
               try {
                  serviceType = ServiceType.valueOf(value.trim().toUpperCase());
               } catch (IllegalArgumentException e) {
                  log.warn("Invalid ServiceType: {}", value);
               }
            }
         } else if ("page".equals(key)) {
            try {
               page = Integer.parseInt(value);
            } catch (NumberFormatException e) {
               log.warn("Invalid page number: {}", value);
            }
         } else if ("size".equals(key)) {
            try {
               size = Integer.parseInt(value);
            } catch (NumberFormatException e) {
               log.warn("Invalid page size: {}", value);
            }
         } else if ("sort".equals(key)) {
            sort = value;
         } else if (key.startsWith("labels.")) {
            String labelKey = key.substring(key.indexOf('.') + 1);
            if (!labelKey.trim().isEmpty() && value != null && !value.trim().isEmpty()) {
               labels.put(labelKey.trim(), value.trim());
            }
         }
      }
      return new SearchCriteria(name, labels, version, serviceType, page, size, sort);
   }

   private Sort parseSort(String sort) {
      if (sort == null || sort.trim().isEmpty()) {
         return Sort.by(Sort.Direction.ASC, "name", "version");
      }
      List<Sort.Order> orders = new ArrayList<>();
      String[] clauses = sort.split(";");
      for (String clause : clauses) {
         String[] parts = clause.split(",");
         String property = parts[0].trim();
         if (isValidSortProperty(property)) {
            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())) {
               direction = Sort.Direction.DESC;
            }
            orders.add(new Sort.Order(direction, property));
         }
      }
      if (orders.isEmpty()) {
         return Sort.by(Sort.Direction.ASC, "name", "version");
      }
      return Sort.by(orders);
   }

   private boolean isValidSortProperty(String property) {
      return "name".equals(property) || "version".equals(property) || "metadata.lastUpdate".equals(property)
            || "type".equals(property);
   }

   private record SearchCriteria(String name, Map<String, String> labels, String version, ServiceType type,
         Integer page, Integer size, String sort) {
   }
}
