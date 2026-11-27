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

import io.github.microcks.domain.GenericResource;
import io.github.microcks.repository.GenericResourceRepository;
import io.github.microcks.util.SafeLogger;

import org.bson.Document;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A controller that expose APIs for browsing Generic resources.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class GenericResourceController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(GenericResourceController.class);

   public static final String ID_FIELD = "id";

   private final GenericResourceRepository genericResourceRepository;

   /**
    * Build a new GenericResourceController with its dependencies.
    * @param genericResourceRepository to have access to GenericResource definition
    */
   public GenericResourceController(GenericResourceRepository genericResourceRepository) {
      this.genericResourceRepository = genericResourceRepository;
   }

   @GetMapping(value = "/genericresources/service/{serviceId}")
   public List<GenericResource> listResources(@PathVariable("serviceId") String serviceId,
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
      log.debug("List resources for service '{}'", serviceId);

      List<GenericResource> genericResources = genericResourceRepository.findByServiceId(serviceId,
            PageRequest.of(page, size));
      // Transform and collect resources.
      return genericResources.stream().map(this::addIdToPayload).toList();
   }

   @GetMapping(value = "/genericresources/service/{serviceId}/count")
   public Map<String, Long> countResources(@PathVariable("serviceId") String serviceId) {
      log.debug("Counting resources for service '{}'", serviceId);

      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", genericResourceRepository.countByServiceId(serviceId));
      return counter;
   }

   private GenericResource addIdToPayload(GenericResource genericResource) {
      Document document = genericResource.getPayload();
      document.append(ID_FIELD, genericResource.getId());
      return genericResource;
   }
}
