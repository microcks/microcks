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

import io.microcks.domain.ArazzoDocumentSummary;
import io.microcks.repository.ArazzoDocumentRepository;
import io.microcks.service.ArazzoDocumentService;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * A Rest controller for the Arazzo documents imported into Microcks.
 * @author SebastienDegodez
 */
@Path("/api/arazzo")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ArazzoDocumentController {

   private final ArazzoDocumentService arazzoDocumentService;
   private final ArazzoDocumentRepository arazzoDocumentRepository;

   /**
    * Build a new ArazzoDocumentController with its dependencies.
    * @param arazzoDocumentService    to summarise the imported Arazzo documents
    * @param arazzoDocumentRepository to read an imported Arazzo document as it was uploaded
    */
   @Inject
   public ArazzoDocumentController(ArazzoDocumentService arazzoDocumentService,
         ArazzoDocumentRepository arazzoDocumentRepository) {
      this.arazzoDocumentService = arazzoDocumentService;
      this.arazzoDocumentRepository = arazzoDocumentRepository;
   }

   /**
    * List the imported Arazzo documents, with the APIs their source descriptions are linked to and their workflows.
    * @param api The name of an API to list only the documents using it; every document when absent
    * @return The summary of each listed document, by title then version
    */
   @GET
   @Path("/documents")
   public List<ArazzoDocumentSummary> listDocuments(@QueryParam("api") String api) {
      return arazzoDocumentService.getDocumentSummaries(api);
   }

   /**
    * Read an imported Arazzo document as it was uploaded, for the Source tab of the Workflow page.
    * @param documentId The id of the Arazzo document
    * @return The content of the document, as YAML or JSON; 404 when the document does not exist
    */
   @GET
   @Path("/documents/{documentId}/source")
   @Produces({ "application/yaml", MediaType.TEXT_PLAIN })
   public Response getDocumentSource(@PathParam("documentId") String documentId) {
      return arazzoDocumentRepository.findByIdOptional(documentId)
            .map(document -> Response.ok(document.getContent(), "application/yaml").build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
   }
}
