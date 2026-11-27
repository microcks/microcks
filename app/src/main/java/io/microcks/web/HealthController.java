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

import io.microcks.repository.ImportJobRepository;

import io.smallrye.common.annotation.RunOnVirtualThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

/**
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class HealthController {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(HealthController.class);

   private final ImportJobRepository jobRepository;

   /**
    * Build a new HealthController with its dependencies.
    * @param jobRepository to have access to ImportJobs
    */
   @Inject
   public HealthController(ImportJobRepository jobRepository) {
      this.jobRepository = jobRepository;
   }

   @GET
   @Path("/health")
   public Response health() {
      log.trace("Health check endpoint invoked");

      try {
         // Using a single selection query to ensure connection to MongoDB is ok.
         jobRepository.findAll(Sort.ascending("name")).page(Page.of(0, 10)).list();
      } catch (Exception e) {
         log.error("Health check caught an exception: {}", e.getMessage(), e);
         return Response.status(Response.Status.SERVICE_UNAVAILABLE).build();
      }
      log.trace("Health check is OK");
      return Response.status(Response.Status.OK).build();
   }
}
