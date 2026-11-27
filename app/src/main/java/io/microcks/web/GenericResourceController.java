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
package io.microcks.web;

import io.microcks.domain.GenericResource;
import io.microcks.repository.GenericResourceRepository;
import io.microcks.util.SafeLogger;

import io.smallrye.common.annotation.RunOnVirtualThread;
import org.bson.Document;

import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A controller that expose APIs for browsing Generic resources.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class GenericResourceController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(GenericResourceController.class);

   public static final String ID_FIELD = "id";

   private final GenericResourceRepository genericResourceRepository;

   /**
    * Build a new GenericResourceController with its dependencies.
    * @param genericResourceRepository to have access to GenericResource definition
    */
   @Inject
   public GenericResourceController(GenericResourceRepository genericResourceRepository) {
      this.genericResourceRepository = genericResourceRepository;
   }

   @GET
   @Path("/genericresources/service/{serviceId}")
   public List<GenericResource> listResources(@PathParam("serviceId") String serviceId,
         @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("10") int size) {
      log.debug("List resources for service '{}'", serviceId);

      List<GenericResource> genericResources = genericResourceRepository.findByServiceId(serviceId, Page.of(page, size),
            Sort.empty());
      // Transform and collect resources.
      return genericResources.stream().map(this::addIdToPayload).toList();
   }

   @GET
   @Path("/genericresources/service/{serviceId}/count")
   public Map<String, Long> countResources(@PathParam("serviceId") String serviceId) {
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
