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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
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

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(JobController.class);

   @Autowired
   private ImportJobRepository jobRepository;

   @Autowired
   private JobService jobService;

   @Autowired
   private AuthorizationChecker authorizationChecker;


   @RequestMapping(value = "/jobs", method = RequestMethod.GET)
   public List<ImportJob> listJobs(@RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size,
         @RequestParam(value = "name", required = false) String name) {
      log.debug("Getting job list for page {} and size {}", page, size);
      if (name != null) {
         return jobRepository.findByNameLike(name);
      }
      return jobRepository.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"))).getContent();
   }

   @RequestMapping(value = "/jobs/search", method = RequestMethod.GET)
   public List<ImportJob> searchJobs(@RequestParam Map<String, String> queryMap) {
      // Parse params from queryMap.
      String name = null;
      Map<String, String> labels = new HashMap<>();
      for (String paramKey : queryMap.keySet()) {
         if ("name".equals(paramKey)) {
            name = queryMap.get("name");
         }
         if (paramKey.startsWith("labels.")) {
            labels.put(paramKey.substring(paramKey.indexOf('.') + 1), queryMap.get(paramKey));
         }
      }

      if (labels == null || labels.isEmpty()) {
         log.debug("Searching jobs corresponding to name {}", name);
         return jobRepository.findByNameLike(name);
      }
      if (name == null || name.trim().length() == 0) {
         log.debug("Searching jobs corresponding to labels {}", labels);
         return jobRepository.findByLabels(labels);
      }
      log.debug("Searching jobs corresponding to name {} and labels {}", name, labels);
      return jobRepository.findByLabelsAndNameLike(labels, name);
   }

   @RequestMapping(value = "/jobs/count", method = RequestMethod.GET)
   public Map<String, Long> countJobs() {
      log.debug("Counting jobs...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", jobRepository.count());
      return counter;
   }

   @RequestMapping(value = "/jobs", method = RequestMethod.POST)
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

   @RequestMapping(value = "/jobs/{id}", method = RequestMethod.GET)
   public ResponseEntity<ImportJob> getJob(@PathVariable("id") String jobId) {
      log.debug("Retrieving job with id {}", jobId);
      return new ResponseEntity<>(jobRepository.findById(jobId).orElse(null), HttpStatus.OK);
   }

   @RequestMapping(value = "/jobs/{id}", method = RequestMethod.POST)
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

   @RequestMapping(value = "/jobs/{id}/activate", method = RequestMethod.PUT)
   public ResponseEntity<ImportJob> activateJob(@PathVariable("id") String jobId, UserInfo userInfo) {
      log.debug("Activating job with id {}", jobId);
      ImportJob job = jobRepository.findById(jobId).orElse(null);
      if (authorizationChecker.hasRole(userInfo, AuthorizationChecker.ROLE_ADMIN)
            || authorizationChecker.hasRoleForImportJob(userInfo, AuthorizationChecker.ROLE_MANAGER, job)) {
         job.setActive(true);
         initMetadataIfMissing(job);
         job.getMetadata().objectUpdated();
         return new ResponseEntity<>(jobRepository.save(job), HttpStatus.OK);
      }
      return new ResponseEntity<>(HttpStatus.FORBIDDEN);
   }

   @RequestMapping(value = "/jobs/{id}/start", method = RequestMethod.PUT)
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

   @RequestMapping(value = "/jobs/{id}/stop", method = RequestMethod.PUT)
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

   @RequestMapping(value = "/jobs/{id}", method = RequestMethod.DELETE)
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
