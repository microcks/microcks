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

import com.github.lbroudoux.microcks.domain.ImportJob;
import com.github.lbroudoux.microcks.repository.ImportJobRepository;
import com.github.lbroudoux.microcks.service.JobService;
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


   @RequestMapping(value = "/jobs", method = RequestMethod.GET)
   public List<ImportJob> listJobs(
         @RequestParam(value = "page", required = false, defaultValue = "0") int page,
         @RequestParam(value = "size", required = false, defaultValue = "20") int size,
         @RequestParam(value = "name", required = false) String name
      ) {
      log.debug("Getting job list for page {} and size {}", page, size);
      if (name != null) {
         return jobRepository.findByNameLike(name);
      }
      return jobRepository.findAll(new PageRequest(page, size, new Sort(Sort.Direction.ASC, "name")))
            .getContent();
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
      job.setCreatedDate(new Date());
      return new ResponseEntity<>(jobRepository.save(job), HttpStatus.CREATED);
   }

   @RequestMapping(value = "/jobs/{id}", method = RequestMethod.GET)
   public ResponseEntity<ImportJob> getJob(@PathVariable("id") String jobId) {
      log.debug("Retrieving job with id {}", jobId);
      return new ResponseEntity<>(jobRepository.findOne(jobId), HttpStatus.OK);
   }

   @RequestMapping(value = "/jobs/{id}", method = RequestMethod.POST)
   public ResponseEntity<ImportJob> saveJob(@RequestBody ImportJob job) {
      log.debug("Saving existing job: {}", job);
      return new ResponseEntity<>(jobRepository.save(job), HttpStatus.OK);
   }

   @RequestMapping(value = "/jobs/{id}/activate", method = RequestMethod.PUT)
   public ResponseEntity<ImportJob> activateJob(@PathVariable("id") String jobId) {
      log.debug("Activating job with id {}", jobId);
      ImportJob job = jobRepository.findOne(jobId);
      job.setActive(true);
      return new ResponseEntity<>(jobRepository.save(job), HttpStatus.OK);
   }

   @RequestMapping(value = "/jobs/{id}/start", method = RequestMethod.PUT)
   public ResponseEntity<ImportJob> startJob(@PathVariable("id") String jobId) {
      log.debug("Starting job with id {}", jobId);
      ImportJob job = jobRepository.findOne(jobId);
      job.setActive(true);
      jobService.doImportJob(job);
      return new ResponseEntity<>(job, HttpStatus.OK);
   }

   @RequestMapping(value = "/jobs/{id}/stop", method = RequestMethod.PUT)
   public ResponseEntity<ImportJob> stopJob(@PathVariable("id") String jobId) {
      log.debug("Stopping job with id {}", jobId);
      ImportJob job = jobRepository.findOne(jobId);
      job.setActive(false);
      return new ResponseEntity<>(jobRepository.save(job), HttpStatus.OK);
   }

   @RequestMapping(value = "/jobs/{id}", method = RequestMethod.DELETE)
   public ResponseEntity<String> deleteJob(@PathVariable("id") String jobId) {
      log.debug("Removing job with id {}", jobId);
      jobRepository.delete(jobId);
      return new ResponseEntity<>(HttpStatus.OK);
   }
}
