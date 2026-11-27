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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * A Controller for getting an export of microcks repository.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ExportController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(ExportController.class);

   private final ImportExportService importExportService;

   /**
    * Build a new ExportController with its dependencies.
    * @param importExportService to have access to export service
    */
   @Inject
   public ExportController(ImportExportService importExportService) {
      this.importExportService = importExportService;
   }

   @GET
   @Path("/export")
   public Response exportRepository(@QueryParam("serviceIds") List<String> serviceIds) {
      log.debug("Extracting export for serviceIds {}", serviceIds);
      String json = importExportService.exportRepository(serviceIds, "json");

      byte[] body = json.getBytes();
      return Response.ok(body).type(MediaType.APPLICATION_JSON)
            .header("Content-Disposition", "attachment; filename=microcks-repository.json")
            .header("Content-Length", body.length).build();
   }
}
