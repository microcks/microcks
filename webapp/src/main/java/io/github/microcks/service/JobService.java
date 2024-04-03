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
package io.github.microcks.service;

import io.github.microcks.domain.ImportJob;
import io.github.microcks.domain.Secret;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.ServiceRef;
import io.github.microcks.repository.ImportJobRepository;
import io.github.microcks.repository.SecretRepository;
import io.github.microcks.util.MockRepositoryImportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Date;
import java.util.List;

/**
 * Bean defining service operations around ImportJob domain objects.
 * @author laurent
 */
@org.springframework.stereotype.Service
public class JobService {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(JobService.class);

   @Autowired
   private ImportJobRepository jobRepository;

   @Autowired
   private SecretRepository secretRepository;

   @Autowired
   private ServiceService serviceService;

   /**
    * Realize the import of a repository defined into an import job.
    * @param job The job containing information onto the repository.
    */
   public void doImportJob(ImportJob job) {
      log.info("Starting import for job '{}'", job.getName());

      // Retrieve associated secret if any.
      Secret jobSecret = null;
      if (job.getSecretRef() != null) {
         log.debug("Retrieving secret {} for job {}", job.getSecretRef().getName(), job.getName());
         jobSecret = secretRepository.findById(job.getSecretRef().getSecretId()).orElse(null);
      }

      // Reinitialize service references and import errors before new import.
      job.setServiceRefs(null);
      job.setLastImportError(null);
      List<Service> services = null;
      try {
         services = serviceService.importServiceDefinition(job.getRepositoryUrl(), jobSecret,
               job.isRepositoryDisableSSLValidation(), job.isMainArtifact());
      } catch (MockRepositoryImportException mrie) {
         log.warn("MockRepositoryImportException while importing job '{}' : {}", job.getName(), mrie.getMessage());
         job.setLastImportError(mrie.getMessage());
      }

      // Add service references if any.
      if (services != null) {
         for (Service service : services) {
            job.addServiceRef(new ServiceRef(service.getId(), service.getName(), service.getVersion()));
         }
      }
      job.setLastImportDate(new Date());
      jobRepository.save(job);
      log.info("Import of job '{}' done", job.getName());
   }

}
