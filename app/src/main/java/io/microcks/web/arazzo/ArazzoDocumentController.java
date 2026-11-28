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
package io.microcks.web.arazzo;

import io.microcks.domain.arazzo.ArazzoDocumentSummary;
import io.microcks.domain.arazzo.ArazzoImportAnalysis;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.service.arazzo.ArazzoDocumentService;
import io.microcks.service.arazzo.ArazzoImportService;
import io.microcks.artifact.MockRepositoryImportException;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

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
   private final ArazzoImportService arazzoImportService;

   /**
    * Build a new ArazzoDocumentController with its dependencies.
    * @param arazzoDocumentService    to summarise the imported Arazzo documents
    * @param arazzoDocumentRepository to read an imported Arazzo document as it was uploaded
    * @param arazzoImportService      to analyse and import the Arazzo documents dropped in the import dialog
    */
   @Inject
   public ArazzoDocumentController(ArazzoDocumentService arazzoDocumentService,
         ArazzoDocumentRepository arazzoDocumentRepository, ArazzoImportService arazzoImportService) {
      this.arazzoDocumentService = arazzoDocumentService;
      this.arazzoDocumentRepository = arazzoDocumentRepository;
      this.arazzoImportService = arazzoImportService;
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

   /**
    * Analyse an Arazzo document before importing it. Nothing is stored.
    * @param request The document, with the links chosen for its source descriptions, if any
    * @return Whether the document can be imported, the API each source is linked to, and whether each workflow is ready
    *         to run
    */
   @POST
   @Path("/documents/analysis")
   @Consumes(MediaType.APPLICATION_JSON)
   public ArazzoImportAnalysis analyseDocument(ArazzoImportRequest request) {
      return arazzoImportService.analyseArazzoDocument(request.content(), request.links());
   }

   /**
    * Import an Arazzo document with the links chosen for its source descriptions, all or nothing.
    * @param request The document, with the links chosen for its source descriptions
    * @return The summary of the imported document, or why it is refused
    */
   @POST
   @Path("/documents")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response importDocument(ArazzoImportRequest request) {
      try {
         return Response.status(Response.Status.CREATED).entity(arazzoImportService.importArazzoDocument(
               request.content(), request.fileName(), request.links() == null ? Map.of() : request.links())).build();
      } catch (MockRepositoryImportException mrie) {
         return Response.status(Response.Status.BAD_REQUEST).entity(mrie.getMessage()).build();
      }
   }

   /**
    * Analyse the links of the source descriptions of an imported Arazzo document, to link them again. Nothing is
    * stored.
    * @param documentId The identifier of the imported document
    * @param request    The links chosen for its source descriptions; none for the links of its ArazzoMetadata
    * @return The API each source is linked to, and whether each workflow is ready to run; 404 for an unknown document
    * @throws MockRepositoryImportException if the stored document cannot be read anymore
    */
   @POST
   @Path("/documents/{id}/analysis")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response analyseImportedDocument(@PathParam("id") String documentId, ArazzoLinksRequest request)
         throws MockRepositoryImportException {
      ArazzoImportAnalysis analysis = arazzoImportService.analyseImportedDocument(documentId, request.links());
      return analysis == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(analysis).build();
   }

   /**
    * Link the source descriptions of an imported Arazzo document again, replacing the sources of its ArazzoMetadata.
    * @param documentId The identifier of the imported document
    * @param request    The links chosen for its source descriptions
    * @return The summary of the document, why the links are refused, or 404 for an unknown document
    */
   @PUT
   @Path("/documents/{id}/sources")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response relinkSources(@PathParam("id") String documentId, ArazzoLinksRequest request) {
      try {
         ArazzoDocumentSummary summary = arazzoImportService.relinkSources(documentId,
               request.links() == null ? Map.of() : request.links());
         return summary == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(summary).build();
      } catch (MockRepositoryImportException mrie) {
         return Response.status(Response.Status.BAD_REQUEST).entity(mrie.getMessage()).build();
      }
   }
}
