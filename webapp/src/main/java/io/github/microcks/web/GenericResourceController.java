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
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A controller that expose APIs for browsing Generic resources.
 * @author laurent
 */
@org.springframework.web.bind.annotation.RestController
@RequestMapping("/api")
public class GenericResourceController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(GenericResourceController.class);

   public static final String ID_FIELD = "id";

   @Autowired
   GenericResourceRepository genericResourceRepository;

   @RequestMapping(value = "/genericresources/service/{serviceId}", method = RequestMethod.GET)
   public List<GenericResource> listResources(@PathVariable("serviceId") String serviceId,
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "10") int size) {
      log.debug("List resources for service '{}'", serviceId);

      List<GenericResource> genericResources = genericResourceRepository.findByServiceId(serviceId,
            PageRequest.of(page, size));
      // Transform and collect resources.
      List<GenericResource> resources = genericResources.stream()
            .map(genericResource -> addIdToPayload(genericResource)).collect(Collectors.toList());

      return resources;
   }

   @RequestMapping(value = "/genericresources/service/{serviceId}/count", method = RequestMethod.GET)
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
