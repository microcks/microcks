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
package io.github.microcks.task;

import io.github.microcks.domain.ImportJob;
import io.github.microcks.domain.Secret;
import io.github.microcks.repository.ImportJobRepository;
import io.github.microcks.repository.SecretRepository;
import io.github.microcks.service.JobService;
import io.github.microcks.util.HTTPDownloader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Scheduled task responsible for periodically update Service definitions if mock repository have changed since previous
 * scan.
 * @author laurent
 */
@Component
public class ImportServiceDefinitionTask {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ImportServiceDefinitionTask.class);

   private static final int CHUNK_SIZE = 20;

   private final ImportJobRepository jobRepository;
   private final SecretRepository secretRepository;
   private final JobService jobService;

   /**
    * Build a new ImportServiceDefinitionTask with required dependencies.
    * @param jobRepository    The job repository to use.
    * @param secretRepository The secret repository to use.
    * @param jobService       The job service to use.
    */
   public ImportServiceDefinitionTask(ImportJobRepository jobRepository, SecretRepository secretRepository,
         JobService jobService) {
      this.jobRepository = jobRepository;
      this.secretRepository = secretRepository;
      this.jobService = jobService;
   }

   @Scheduled(cron = "${services.update.interval}")
   public void importServiceDefinition() {
      // Prepare some flags.
      int updated = 0;
      long startTime = System.currentTimeMillis();

      log.info("Starting scan of Service definitions update scheduled task...");

      long numJobs = jobRepository.count();
      log.debug("Found {} jobs to check. Splitting in {} chunks.", numJobs, (numJobs / CHUNK_SIZE + 1));

      for (int i = 0; i < numJobs / CHUNK_SIZE + 1; i++) {
         List<ImportJob> jobs = jobRepository.findAll(PageRequest.of(i, CHUNK_SIZE)).getContent();
         log.debug("Found {} jobs into chunk {}", jobs.size(), i);

         for (ImportJob job : jobs) {
            log.debug("Dealing with job {}", job.getName());
            if (job.isActive()) {

               // Retrieve associated secret if any.
               Secret jobSecret = null;
               if (job.getSecretRef() != null) {
                  log.debug("Retrieving secret {} for job {}", job.getSecretRef().getName(), job.getName());
                  jobSecret = secretRepository.findById(job.getSecretRef().getSecretId()).orElse(null);
               }

               // Get older and fresh Etag if any.
               String etag = job.getEtag();
               String freshEtag = null;
               try {
                  freshEtag = HTTPDownloader.getURLEtag(job.getRepositoryUrl(), jobSecret,
                        job.isRepositoryDisableSSLValidation());
               } catch (IOException ioe) {
                  log.error("Got an IOException while checking ETag for {}, pursuing...", job.getRepositoryUrl());
               }

               // Test if we must update this service definition.
               if (freshEtag == null || (freshEtag != null && !freshEtag.equals(etag))) {
                  log.debug("No Etag or fresher one found, updating service definition for {}", job.getName());

                  job.setEtag(freshEtag);
                  jobService.doImportJob(job);

                  updated++;
               }
            }
         }
      }
      long duration = System.currentTimeMillis() - startTime;
      log.info("Task end in {} ms, updating {} job services definitions", duration, updated);
   }
}
