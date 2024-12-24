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

import io.github.microcks.domain.ImportJob;
import io.github.microcks.domain.Metadata;
import io.github.microcks.repository.ImportJobRepository;
import io.github.microcks.security.AuthorizationChecker;
import io.github.microcks.security.UserInfo;
import io.github.microcks.service.JobService;
import io.github.microcks.util.SafeLogger;

import org.springframework.data.domain.PageRequest;
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

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Rest controller for API defined on importers.
 * @author laurent
 */
@RestController
@RequestMapping("/api")
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
   public JobController(ImportJobRepository jobRepository, JobService jobService,
         AuthorizationChecker authorizationChecker) {
      this.jobRepository = jobRepository;
      this.jobService = jobService;
      this.authorizationChecker = authorizationChecker;
   }

   @GetMapping(value = "/jobs")
   public List<ImportJob> listJobs(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size,
         @RequestParam(value = "name", required = false) String name) {
      log.debug("Getting job list for page {} and size {}", page, size);
      if (name != null) {
         return jobRepository.findByNameLike(name);
      }
      return jobRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"))).getContent();
   }

   @GetMapping(value = "/jobs/search")
   public List<ImportJob> searchJobs(@RequestParam Map<String, String> queryMap) {
      // Parse params from queryMap.
      String name = null;
      Map<String, String> labels = new HashMap<>();
      for (Map.Entry<String, String> paramEntry : queryMap.entrySet()) {
         String paramKey = paramEntry.getKey();
         if ("name".equals(paramKey)) {
            name = paramEntry.getValue();
         }
         if (paramKey.startsWith("labels.")) {
            labels.put(paramKey.substring(paramKey.indexOf('.') + 1), paramEntry.getValue());
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

   @GetMapping(value = "/jobs/count")
   public Map<String, Long> countJobs() {
      log.debug("Counting jobs...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", jobRepository.count());
      return counter;
   }

   @PostMapping(value = "/jobs")
   public ResponseEntity<ImportJob> createJob(@RequestBody ImportJob job) {
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
      return new ResponseEntity<>(jobRepository.save(job), HttpStatus.CREATED);
   }

   @GetMapping(value = "/jobs/{id}")
   public ResponseEntity<ImportJob> getJob(@PathVariable("id") String jobId) {
      log.debug("Retrieving job with id {}", jobId);
      return new ResponseEntity<>(jobRepository.findById(jobId).orElse(null), HttpStatus.OK);
   }

   @PostMapping(value = "/jobs/{id}")
   public ResponseEntity<ImportJob> saveJob(@RequestBody ImportJob job, UserInfo userInfo) {
      log.debug("Saving existing job: {}", job);
      if (authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(userInfo, AuthorizationChecker.ROLE_MANAGER, job)) {
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         return new ResponseEntity<>(jobRepository.save(job), HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @PutMapping(value = "/jobs/{id}/activate")
   public ResponseEntity<ImportJob> activateJob(@PathVariable("id") String jobId, UserInfo userInfo) {
      log.debug("Activating job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId).orElse(null);
      if (job != null && (authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(userInfo, AuthorizationChecker.ROLE_MANAGER, job))) {
         job.setActive(true);
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         return new ResponseEntity<>(jobRepository.save(job), HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @PutMapping(value = "/jobs/{id}/start")
   public ResponseEntity<ImportJob> startJob(@PathVariable("id") String jobId, UserInfo userInfo) {
      log.debug("Starting job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId).orElse(null);
      if (authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(userInfo, AuthorizationChecker.ROLE_MANAGER, job)) {
         job.setActive(true);
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         jobService.doImportJob(job);
         return new ResponseEntity<>(job, HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @PutMapping(value = "/jobs/{id}/stop")
   public ResponseEntity<ImportJob> stopJob(@PathVariable("id") String jobId, UserInfo userInfo) {
      log.debug("Stopping job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId).orElse(null);
      if (authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(userInfo, AuthorizationChecker.ROLE_MANAGER, job)) {
         job.setActive(false);
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         return new ResponseEntity<>(jobRepository.save(job), HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @DeleteMapping(value = "/jobs/{id}")
   public ResponseEntity<String> deleteJob(@PathVariable("id") String jobId, UserInfo userInfo) {
      log.debug("Removing job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId).orElse(null);
      if (authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(userInfo, AuthorizationChecker.ROLE_MANAGER, job)) {
         jobRepository.deleteById(jobId);
         return new ResponseEntity<>(HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   private void initMetadataIfMissing(ImportJob job) {
      if (job.getMetadata() == null) {
         job.setMetadata(new Metadata());
      }
   }
}
