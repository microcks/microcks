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
package io.microcks.service;

import io.microcks.service.arazzo.ArazzoDocumentService;
import io.microcks.service.arazzo.ArazzoImportService;
import io.microcks.domain.ImportJob;
import io.microcks.domain.Secret;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceRef;
import io.microcks.repository.ImportJobRepository;
import io.microcks.repository.SecretRepository;
import io.microcks.artifact.MockRepositoryImportException;
import io.microcks.util.HTTPDownloader;
import io.microcks.util.ReferenceResolver;
import io.microcks.util.RelativeReferenceURLBuilder;
import io.microcks.util.RelativeReferenceURLBuilderFactory;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Bean defining service operations around ImportJob domain objects.
 * @author laurent
 */
@ApplicationScoped
public class JobService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(JobService.class);

   private final ImportJobRepository jobRepository;
   private final SecretRepository secretRepository;
   private final ServiceService serviceService;
   private final ArazzoDocumentService arazzoDocumentService;
   private final ArazzoImportService arazzoImportService;

   /**
    * Create a new JobService with required dependencies.
    * @param jobRepository         The job repository to use.
    * @param secretRepository      The secret repository to use.
    * @param serviceService        The service service to use.
    * @param arazzoDocumentService The service that tells Arazzo documents, ArazzoMetadata and ArazzoExamples apart
    *                              from APIs.
    * @param arazzoImportService   The service that imports Arazzo documents, ArazzoMetadata and ArazzoExamples from
    *                              a repository.
    */
   @Inject
   public JobService(ImportJobRepository jobRepository, SecretRepository secretRepository,
         ServiceService serviceService, ArazzoDocumentService arazzoDocumentService,
         ArazzoImportService arazzoImportService) {
      this.jobRepository = jobRepository;
      this.secretRepository = secretRepository;
      this.serviceService = serviceService;
      this.arazzoDocumentService = arazzoDocumentService;
      this.arazzoImportService = arazzoImportService;
   }

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
         jobSecret = secretRepository.findById(job.getSecretRef().getSecretId());
      }

      // Reinitialize service and Arazzo document references and import errors before new import.
      job.setServiceRefs(null);
      job.setArazzoDocumentRef(null);
      job.setLastImportError(null);
      try {
         importRepository(job, jobSecret);
      } catch (MockRepositoryImportException mrie) {
         log.warn("MockRepositoryImportException while importing job '{}' : {}", job.getName(), mrie.getMessage());
         job.setLastImportError(mrie.getMessage());
      }
      job.setLastImportDate(new Date());
      jobRepository.persistOrUpdate(job);
      log.info("Import of job '{}' done", job.getName());
   }

   /**
    * Import the artifact at the repository url of a job: an Arazzo document, an ArazzoMetadata or an ArazzoExamples
    * has its own import, any other artifact is imported as services.
    */
   private void importRepository(ImportJob job, Secret jobSecret) throws MockRepositoryImportException {
      String repositoryUrl = job.getRepositoryUrl();
      File localFile;
      Map<String, List<String>> fileProperties = null;
      if (repositoryUrl.startsWith("http")) {
         try {
            HTTPDownloader.FileAndHeaders fileAndHeaders = HTTPDownloader
                  .handleHTTPDownloadToFileAndHeaders(repositoryUrl, jobSecret, job.isRepositoryDisableSSLValidation());
            localFile = fileAndHeaders.getLocalFile();
            fileProperties = fileAndHeaders.getResponseHeaders();
         } catch (IOException ioe) {
            throw new MockRepositoryImportException(repositoryUrl + " cannot be downloaded", ioe);
         }
      } else {
         // Simply build localFile from repository url.
         localFile = new File(repositoryUrl);
      }

      RelativeReferenceURLBuilder referenceURLBuilder = RelativeReferenceURLBuilderFactory
            .getRelativeReferenceURLBuilder(fileProperties);
      String artifactName = referenceURLBuilder.getFileName(repositoryUrl, fileProperties);
      // Initialize a reference resolver to the folder of this repositoryUrl.
      ReferenceResolver referenceResolver = new ReferenceResolver(repositoryUrl, jobSecret,
            job.isRepositoryDisableSSLValidation(), referenceURLBuilder);

      String content = contentOf(localFile);
      if (arazzoDocumentService.isArazzoDocument(content)) {
         job.setArazzoDocumentRef(arazzoImportService.importArazzoDocument(content, artifactName, referenceResolver));
      } else if (arazzoDocumentService.isArazzoMetadata(content)) {
         job.setArazzoDocumentRef(arazzoImportService.importArazzoMetadata(content));
      } else if (arazzoDocumentService.isArazzoExamples(content)) {
         job.setArazzoDocumentRef(arazzoImportService.importArazzoExamples(content));
      } else {
         List<Service> services = serviceService.importServiceDefinition(localFile, referenceResolver,
               new ArtifactInfo(artifactName, job.isMainArtifact()));
         for (Service service : services) {
            job.addServiceRef(new ServiceRef(service.getId(), service.getName(), service.getVersion()));
         }
      }
   }

   /** A file that cannot be read has no content: the importer of services tells why. */
   private static String contentOf(File localFile) {
      try {
         return Files.readString(localFile.toPath(), StandardCharsets.UTF_8);
      } catch (IOException ioe) {
         return "";
      }
   }
}
