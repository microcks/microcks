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

import io.microcks.domain.ImportJob;
import io.microcks.domain.Metadata;
import io.microcks.repository.ImportJobRepository;
import io.microcks.security.AuthorizationChecker;
import io.microcks.security.UserInfo;
import io.microcks.service.JobService;
import io.microcks.util.SafeLogger;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Rest controller for API defined on importers.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class JobController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(JobController.class);

   private final ImportJobRepository jobRepository;
   private final JobService jobService;
   private final AuthorizationChecker authorizationChecker;

   /**
    * Build a new JobController with its dependencies.
    * @param jobRepository        to have access to ImportJob definition
    * @param jobService           to have access to ImportJob service (activate, start, stop)
    * @param authorizationChecker to check authorization for operations
    */
   @Inject
   public JobController(ImportJobRepository jobRepository, JobService jobService,
         AuthorizationChecker authorizationChecker) {
      this.jobRepository = jobRepository;
      this.jobService = jobService;
      this.authorizationChecker = authorizationChecker;
   }

   @GET
   @Path("/jobs")
   public List<ImportJob> listJobs(@QueryParam("page") @DefaultValue("0") int page,
         @QueryParam("size") @DefaultValue("20") int size, @QueryParam("name") String name) {
      log.debug("Getting job list for page {} and size {}", page, size);
      if (name != null) {
         return jobRepository.findByNameLike(name);
      }
      return jobRepository.findAll(Sort.ascending("name")).page(Page.of(page, size)).list();
   }

   @GET
   @Path("/jobs/search")
   public List<ImportJob> searchJobs(@Context UriInfo uriInfo) {
      // Parse params from queryMap.
      String name = null;
      Map<String, String> labels = new HashMap<>();
      for (Map.Entry<String, List<String>> paramEntry : uriInfo.getQueryParameters().entrySet()) {
         String paramValue = paramEntry.getValue().isEmpty() ? null : paramEntry.getValue().get(0);
         String paramKey = paramEntry.getKey();
         if ("name".equals(paramKey)) {
            name = paramValue;
         }
         if (paramKey.startsWith("labels.")) {
            labels.put(paramKey.substring(paramKey.indexOf('.') + 1), paramValue);
         }
      }

      if (labels.isEmpty()) {
         log.debug("Searching jobs corresponding to name {}", name);
         return jobRepository.findByNameLike(name);
      }
      if (name == null || name.trim().isEmpty()) {
         log.debug("Searching jobs corresponding to labels {}", labels);
         return jobRepository.findByLabels(labels);
      }
      log.debug("Searching jobs corresponding to name {} and labels {}", name, labels);
      return jobRepository.findByLabelsAndNameLike(labels, name);
   }

   @GET
   @Path("/jobs/count")
   public Map<String, Long> countJobs() {
      log.debug("Counting jobs...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", jobRepository.count());
      return counter;
   }

   @POST
   @Path("/jobs")
   public Response createJob(ImportJob job) {
      log.debug("Creating new job: {}", job);
      // Store labels somewhere before reinitializing metadata to ensure createdOn is correct.
      Map<String, String> labels = null;
      if (job.getMetadata() != null && job.getMetadata().getLabels() != null) {
         labels = job.getMetadata().getLabels();
      }
      job.setMetadata(new Metadata());
      job.setCreatedDate(new Date());
      // Restore labels.
      if (labels != null) {
         job.getMetadata().setLabels(labels);
      }
      return Response.status(Response.Status.CREATED).entity(save(job)).build();
   }

   @GET
   @Path("/jobs/{id}")
   public Response getJob(@PathParam("id") String jobId) {
      log.debug("Retrieving job with id {}", jobId);
      return Response.status(Response.Status.OK).entity(jobRepository.findById(jobId)).build();
   }

   @POST
   @Path("/jobs/{id}")
   public Response saveJob(ImportJob job) {
      log.debug("Saving existing job: {}", job);
      if (authorizationChecker.hasRole(null, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(null, AuthorizationChecker.ROLE_MANAGER, job)) {
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         return Response.status(Response.Status.OK).entity(save(job)).build();
      }
      return Response.status(Response.Status.FORBIDDEN).build();
   }

   @PUT
   @Path("/jobs/{id}/activate")
   public Response activateJob(@PathParam("id") String jobId) {
      log.debug("Activating job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId);

      if (job == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }

      if (authorizationChecker.hasRole(null, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(null, AuthorizationChecker.ROLE_MANAGER, job)) {
         job.setActive(true);
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         return Response.status(Response.Status.OK).entity(save(job)).build();
      }
      return Response.status(Response.Status.FORBIDDEN).build();
   }

   @PUT
   @Path("/jobs/{id}/start")
   public Response startJob(@PathParam("id") String jobId) {
      log.debug("Starting job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId);

      if (job == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }

      if (authorizationChecker.hasRole(null, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(null, AuthorizationChecker.ROLE_MANAGER, job)) {
         job.setActive(true);
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         jobService.doImportJob(job);
         return Response.status(Response.Status.OK).entity(job).build();
      }
      return Response.status(Response.Status.FORBIDDEN).build();
   }

   @PUT
   @Path("/jobs/{id}/stop")
   public Response stopJob(@PathParam("id") String jobId) {
      log.debug("Stopping job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId);

      if (job == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }

      if (authorizationChecker.hasRole(null, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(null, AuthorizationChecker.ROLE_MANAGER, job)) {
         job.setActive(false);
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         return Response.status(Response.Status.OK).entity(save(job)).build();
      }
      return Response.status(Response.Status.FORBIDDEN).build();
   }

   @DELETE
   @Path("/jobs/{id}")
   public Response deleteJob(@PathParam("id") String jobId) {
      log.debug("Removing job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId);

      if (job == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }

      if (authorizationChecker.hasRole(null, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(null, AuthorizationChecker.ROLE_MANAGER, job)) {
         jobRepository.deleteById(jobId);
         return Response.status(Response.Status.OK).build();
      }
      return Response.status(Response.Status.FORBIDDEN).build();
   }

   private ImportJob save(ImportJob job) {
      jobRepository.persistOrUpdate(job);
      return job;
   }

   private void initMetadataIfMissing(ImportJob job) {
      if (job.getMetadata() == null) {
         job.setMetadata(new Metadata());
      }
   }
}
