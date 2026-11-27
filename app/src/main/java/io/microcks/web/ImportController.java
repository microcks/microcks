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

import io.microcks.service.ImportExportService;
import io.microcks.util.SafeLogger;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * A Controller for importing new definitions into microcks repository.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ImportController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(ImportController.class);

   private final ImportExportService importExportService;

   /**
    * Create new ImportController with required service.
    * @param importExportService The service for managing imports.
    */
   @Inject
   public ImportController(ImportExportService importExportService) {
      this.importExportService = importExportService;
   }

   @POST
   @Path("/import")
   @Consumes(MediaType.MULTIPART_FORM_DATA)
   public Response importRepository(@RestForm("file") FileUpload file) {
      log.debug("Importing new services and resources definitions");

      if (file == null || file.size() == 0) {
         return Response.status(Response.Status.BAD_REQUEST).build();
      }

      log.debug("Content type of {} is {}", file.fileName(), file.contentType());

      if (!MediaType.APPLICATION_JSON.equals(file.contentType())) {
         return Response.status(Response.Status.UNSUPPORTED_MEDIA_TYPE).build();
      }

      try {
         byte[] bytes = Files.readAllBytes(file.uploadedFile());
         String json = new String(bytes, StandardCharsets.UTF_8);
         importExportService.importRepository(json);

         return Response.status(Response.Status.CREATED).build();
      } catch (Exception e) {
         log.error("Exception while importing repository", e);
         return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
      }
   }
}
