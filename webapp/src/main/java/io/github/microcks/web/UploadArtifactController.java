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

import io.github.microcks.domain.Secret;
import io.github.microcks.domain.Service;
import io.github.microcks.repository.SecretRepository;
import io.github.microcks.service.ArtifactInfo;
import io.github.microcks.service.ServiceService;
import io.github.microcks.util.HTTPDownloader;
import io.github.microcks.util.MockRepositoryImportException;
import io.github.microcks.util.ReferenceResolver;
import io.github.microcks.util.RelativeReferenceURLBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * This is a controller for managing import of uploaded or downloaded artifact contract files.
 * @author laurent
 */
@RestController
@RequestMapping("/api")
public class UploadArtifactController {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(UploadArtifactController.class);

   private ServiceService serviceService;

   private SecretRepository secretRepository;

   /**
    * Build a new UploadArtifactController with its dependencies.
    * @param serviceService   to perform business logic on Services
    * @param secretRepository to retrieve requested Secrets
    */
   public UploadArtifactController(ServiceService serviceService, SecretRepository secretRepository) {
      this.serviceService = serviceService;
      this.secretRepository = secretRepository;
   }

   @PostMapping(value = "/artifact/download")
   public ResponseEntity<String> importArtifact(@RequestParam(value = "url", required = true) String url,
         @RequestParam(value = "mainArtifact", defaultValue = "true") boolean mainArtifact,
         @RequestParam(value = "secretName", required = false) String secretName) {
      if (!url.isEmpty()) {
         List<Service> services = null;

         Secret secret = null;
         if (secretName != null) {
            secret = secretRepository.findByName(secretName).stream().findFirst().orElse(null);
            log.debug("Secret {} was requested. Have we found it? {}", secretName, (secret != null));
         }

         try {
            // Download remote to local file before import.
            HTTPDownloader.FileAndHeaders fileAndHeaders = HTTPDownloader.handleHTTPDownloadToFileAndHeaders(url,
                  secret, true);
            File localFile = fileAndHeaders.getLocalFile();

            // Now try importing services.
            services = serviceService.importServiceDefinition(localFile,
                  new ReferenceResolver(url, secret, true,
                        RelativeReferenceURLBuilderFactory
                              .getRelativeReferenceURLBuilder(fileAndHeaders.getResponseHeaders())),
                  new ArtifactInfo(url, mainArtifact));
         } catch (IOException ioe) {
            log.error("Exception while retrieving remote item " + url, ioe);
            return new ResponseEntity<>("Exception while retrieving remote item", HttpStatus.INTERNAL_SERVER_ERROR);
         } catch (MockRepositoryImportException mrie) {
            return new ResponseEntity<>(mrie.getMessage(), HttpStatus.BAD_REQUEST);
         }
         if (services != null && !services.isEmpty()) {
            return new ResponseEntity<>(
                  "{\"name\": \"" + services.get(0).getName() + ":" + services.get(0).getVersion() + "\"}",
                  HttpStatus.CREATED);
         }
      }
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
   }

   @PostMapping(value = "/artifact/upload")
   public ResponseEntity<String> importArtifact(@RequestParam(value = "file") MultipartFile file,
         @RequestParam(value = "mainArtifact", defaultValue = "true") boolean mainArtifact) {
      if (!file.isEmpty()) {
         log.debug("Content type of {} is {}", file.getOriginalFilename(), file.getContentType());

         List<Service> services = null;

         try {
            // Save upload to local file before import.
            String localFile = System.getProperty("java.io.tmpdir") + "/microcks-" + System.currentTimeMillis()
                  + ".artifact";

            try (ReadableByteChannel rbc = Channels.newChannel(file.getInputStream());
                  FileOutputStream fos = new FileOutputStream(localFile)) {
               fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }

            // Now try importing services.
            services = serviceService.importServiceDefinition(new File(localFile), null,
                  new ArtifactInfo(file.getOriginalFilename(), mainArtifact));

            // Cleanup and remove local file.
            Files.delete(Paths.get(localFile));
         } catch (IOException ioe) {
            log.error("Exception while writing uploaded item {}", file.getOriginalFilename(), ioe);
            return new ResponseEntity<>("Exception while writing uploaded item", HttpStatus.INTERNAL_SERVER_ERROR);
         } catch (MockRepositoryImportException mrie) {
            return new ResponseEntity<>(mrie.getMessage(), HttpStatus.BAD_REQUEST);
         }
         if (services != null && !services.isEmpty()) {
            return new ResponseEntity<>(services.get(0).getName() + ":" + services.get(0).getVersion(),
                  HttpStatus.CREATED);
         }
      }
      return new ResponseEntity<>(HttpStatus.NO_CONTENT);
   }
}
