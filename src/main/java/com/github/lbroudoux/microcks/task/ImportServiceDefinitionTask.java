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
package com.github.lbroudoux.microcks.task;

import com.github.lbroudoux.microcks.domain.ImportJob;
import com.github.lbroudoux.microcks.domain.Service;
import com.github.lbroudoux.microcks.domain.ServiceRef;
import com.github.lbroudoux.microcks.repository.ImportJobRepository;
import com.github.lbroudoux.microcks.service.ServiceService;
import com.github.lbroudoux.microcks.util.MockRepositoryImportException;
import com.github.lbroudoux.microcks.util.UsernamePasswordAuthenticator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.Authenticator;
import java.net.URL;
import java.net.URLConnection;
import java.util.Date;
import java.util.List;

/**
 * Scheduled task responsible for periodically update Service definitions
 * if mock repository have changed since previous scan.
 * @author laurent
 */
@Component
public class ImportServiceDefinitionTask {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(ImportServiceDefinitionTask.class);

   private static final int CHUNK_SIZE = 20;

   @Autowired
   private ServiceService serviceService;

   @Autowired
   private ImportJobRepository jobRepository;

   @Value("${network.username}")
   private final String username = null;

   @Value("${network.password}")
   private final String password = null;


   @Scheduled(cron="0 0 0/2 * * *")
   public void importServiceDefinition(){
      // Prepare some flags.
      int updated = 0;
      long startTime = System.currentTimeMillis();

      log.info("Starting scan of Service definitions update scheduled task...");

      long numJobs = jobRepository.count();
      log.debug("Found {} jobs to check. Splitting in {} chunks.", numJobs, (numJobs/CHUNK_SIZE + 1));

      for (int i=0; i<numJobs/CHUNK_SIZE + 1; i++) {
         List<ImportJob> jobs = jobRepository.findAll(new PageRequest(i, CHUNK_SIZE)).getContent();
         log.debug("Found {} jobs into chunk {}", jobs.size(), i);

         for (ImportJob job : jobs){
            log.debug("Dealing with job " + job.getName());
            if (job.isActive()){
               // Get older and fresh Etag if any.
               String etag = job.getEtag();
               String freshEtag = getRepositoryUrlEtag(job.getRepositoryUrl());

               // Test if we must update this service definition.
               if (freshEtag == null || (freshEtag != null && !freshEtag.equals(etag)) ){
                  log.debug("No Etag or fresher one found, updating service definition for " + job.getName());
                  List<Service> services = null;
                  try{
                     services = serviceService.importServiceDefinition(job.getRepositoryUrl());
                     updateJobServiceRefs(job, services);
                     // Reset last error if any.
                     job.setLastImportError(null);
                  } catch (MockRepositoryImportException mrie){
                     job.setLastImportError(mrie.getMessage());
                  }

                  // Update job with last informations.
                  job.setEtag(freshEtag);
                  job.setLastImportDate(new Date());
                  jobRepository.save(job);

                  updated++;
               }
            }
         }
      }
      long duration = System.currentTimeMillis() - startTime;
      log.info("Task end in " + duration + " ms, updating " + updated + " job services definitions");
   }

   /**
    * Extract URL Etag in order to avoid a reimport on unchanged resource.
    * @param repositoryUrl The URL to test as
    * @return The etag or null if none.
    */
   private String getRepositoryUrlEtag(String repositoryUrl){
      Authenticator.setDefault(new UsernamePasswordAuthenticator(username, password));
      try{
         URLConnection connection = new URL(repositoryUrl).openConnection();
         // Try simple syntax.
         String etag = connection.getHeaderField("Etag");
         if (etag != null){
            log.debug("Found an Etag for " + repositoryUrl + ": " + etag);
            return etag;
         }
         // Try other syntax.
         etag = connection.getHeaderField("ETag");
         if (etag != null){
            log.debug("Found an ETag for " + repositoryUrl + ": " + etag);
            return etag;
         }
      } catch (Exception e){
         log.error("Caught an exception while retrieving Etag for " + repositoryUrl, e);
      }
      log.debug("No Etag found for " + repositoryUrl + " !");
      return null;
   }

   /** Update job service references from newly acquired services. */
   private void updateJobServiceRefs(ImportJob job, List<Service> services){
      if (job.getServiceRefs() != null){
         job.getServiceRefs().clear();
      }
      for (Service service : services){
         ServiceRef reference = new ServiceRef(service.getId(), service.getName(), service.getVersion());
         job.addServiceRef(reference);
      }
   }
}
