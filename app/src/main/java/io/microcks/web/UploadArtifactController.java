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

import io.microcks.domain.Secret;
import io.microcks.domain.Service;
import io.microcks.repository.SecretRepository;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;
import io.microcks.util.HTTPDownloader;
import io.microcks.artifact.MockRepositoryImportException;
import io.microcks.util.ReferenceResolver;
import io.microcks.util.RelativeReferenceURLBuilderFactory;
import io.microcks.util.SafeLogger;
import io.microcks.util.SimpleReferenceURLBuilder;


import io.smallrye.common.annotation.RunOnVirtualThread;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * This is a controller for managing import of uploaded or downloaded artifact contract files.
 * @author laurent
 */
@Path("/api")
@RunOnVirtualThread
public class UploadArtifactController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(UploadArtifactController.class);

   private final ServiceService serviceService;
   private final SecretRepository secretRepository;

   @ConfigProperty(name = "default-artifacts-repository.url")
   Optional<String> defaultArtifactsRepositoryUrl;

   /**
    * Build a new UploadArtifactController with its dependencies.
    * @param serviceService   to perform business logic on Services
    * @param secretRepository to retrieve requested Secrets
    */
   @Inject
   public UploadArtifactController(ServiceService serviceService, SecretRepository secretRepository) {
      this.serviceService = serviceService;
      this.secretRepository = secretRepository;
   }

   @POST
   @Path("/artifact/download")
   @Produces(MediaType.APPLICATION_JSON)
   public Response downloadArtifact(@QueryParam("url") String url,
         @QueryParam("mainArtifact") @DefaultValue("true") boolean mainArtifact,
         @QueryParam("secretName") String secretName) {
      if (url != null && !url.isEmpty()) {
         List<Service> services = null;

         Secret secret = null;
         if (secretName != null) {
            secret = secretRepository.findByName(secretName).stream().findFirst().orElse(null);
            log.debug("Secret {} was requested. Have we found it? {}", secretName, (secret != null));
         }

         File localFile = null;
         try {
            // Download remote to local file before import.
            HTTPDownloader.FileAndHeaders fileAndHeaders = HTTPDownloader.handleHTTPDownloadToFileAndHeaders(url,
                  secret, true);
            localFile = fileAndHeaders.getLocalFile();

            // Now try importing services.
            services = serviceService.importServiceDefinition(localFile,
                  new ReferenceResolver(url, secret, true,
                        RelativeReferenceURLBuilderFactory
                              .getRelativeReferenceURLBuilder(fileAndHeaders.getResponseHeaders())),
                  new ArtifactInfo(url, mainArtifact));
         } catch (IOException ioe) {
            log.error("Exception while retrieving remote item {}", url, ioe);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                  .entity("Exception while retrieving remote item").build();
         } catch (MockRepositoryImportException mrie) {
            log.error("Exception while reading remote item {}", url, mrie);
            return Response.status(Response.Status.BAD_REQUEST).entity(mrie.getMessage()).build();
         } finally {
            // Cleanup and remove local file.
            if (localFile != null && !localFile.delete()) {
               log.warn("Failed to delete temporary artifact file {}", localFile.getAbsolutePath());
            }
         }

         if (services != null && !services.isEmpty()) {
            return Response.status(Response.Status.CREATED)
                  .entity("{\"name\": \"" + services.get(0).getName() + ":" + services.get(0).getVersion() + "\"}")
                  .build();
         }
      }
      return Response.status(Response.Status.NO_CONTENT).build();
   }

   @POST
   @Path("/artifact/upload")
   @Consumes(MediaType.MULTIPART_FORM_DATA)
   @Produces(MediaType.TEXT_PLAIN)
   public Response uploadArtifact(@RestForm("file") FileUpload file,
         @QueryParam("mainArtifact") Boolean mainArtifactParam, @RestForm("mainArtifact") Boolean mainArtifactForm) {
      // Spring accepted the flag as query or form parameter: keep both, defaulting to true.
      boolean mainArtifact = mainArtifactParam != null ? mainArtifactParam
            : (mainArtifactForm == null || mainArtifactForm);
      if (file != null && file.size() > 0) {
         log.debug("Content type of {} is {}", file.fileName(), file.contentType());

         List<Service> services = null;
         String localFile = null;
         try {
            // Save upload to local file before import.
            localFile = System.getProperty("java.io.tmpdir") + "/microcks-" + UUID.randomUUID() + ".artifact";

            Files.copy(file.uploadedFile(), Paths.get(localFile), StandardCopyOption.REPLACE_EXISTING);

            // Now try importing services.
            ReferenceResolver referenceResolver = getDefaultReferenceResolver(file.fileName());
            services = serviceService.importServiceDefinition(new File(localFile), referenceResolver,
                  new ArtifactInfo(file.fileName(), mainArtifact));
         } catch (IOException ioe) {
            log.error("Exception while writing uploaded item {}", file.fileName(), ioe);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                  .entity("Exception while writing uploaded item").build();
         } catch (MockRepositoryImportException mrie) {
            log.error("Exception while reading uploaded item {}", file.fileName(), mrie);
            return Response.status(Response.Status.BAD_REQUEST).entity(mrie.getMessage()).build();
         } finally {
            // Cleanup and remove local file.
            if (localFile != null) {
               File tempFile = Paths.get(localFile).toFile();
               if (!tempFile.delete()) {
                  log.warn("Failed to delete temporary artifact file {}", tempFile.getAbsolutePath());
               }
            }
         }

         if (services != null && !services.isEmpty()) {
            return Response.status(Response.Status.CREATED)
                  .entity(services.get(0).getName() + ":" + services.get(0).getVersion()).build();
         }
      }
      return Response.status(Response.Status.NO_CONTENT).build();
   }

   private ReferenceResolver getDefaultReferenceResolver(String fileName) {
      log.debug("defaultArtifactsRepositoryUrl is {}", defaultArtifactsRepositoryUrl.orElse(null));
      if (defaultArtifactsRepositoryUrl.isPresent()) {
         String repositoryUrl = defaultArtifactsRepositoryUrl.get();
         String baseRepositoryUrl = repositoryUrl.endsWith("/") ? repositoryUrl + fileName
               : repositoryUrl + "/" + fileName;
         ReferenceResolver resolver = new ReferenceResolver(baseRepositoryUrl, null, true,
               new SimpleReferenceURLBuilder());
         if (!repositoryUrl.startsWith("http")) {
            resolver.setCleanResolvedFiles(false);
         }
         return resolver;
      }
      return null;
   }
}
