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

import io.microcks.service.arazzo.view.ArazzoApiUsage;
import io.microcks.service.arazzo.ArazzoWorkflowService;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * A Rest controller for the workflows of the Arazzo documents imported into Microcks.
 * @author SebastienDegodez
 */
@Path("/api/arazzo")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ArazzoWorkflowController {

   private final ArazzoWorkflowService arazzoWorkflowService;

   /**
    * Build a new ArazzoWorkflowController with its dependencies.
    * @param arazzoWorkflowService to read the workflows of Arazzo documents
    */
   @Inject
   public ArazzoWorkflowController(ArazzoWorkflowService arazzoWorkflowService) {
      this.arazzoWorkflowService = arazzoWorkflowService;
   }

   /**
    * Read a workflow of an Arazzo document: its steps, the operations they call and how data flows between them.
    * @param documentId The id of the Arazzo document
    * @param workflowId The id of the workflow in this document
    * @return The detail of the workflow; 404 when the document or the workflow does not exist
    */
   @GET
   @Path("/documents/{documentId}/workflows/{workflowId}")
   public Response getWorkflowDetail(@PathParam("documentId") String documentId,
         @PathParam("workflowId") String workflowId) {
      return arazzoWorkflowService.getWorkflowDetail(documentId, workflowId).map(detail -> Response.ok(detail).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
   }

   /**
    * Tell which workflows use an API, and how many workflows call each of its operations, for the page of this API.
    * @param serviceId The id of the API
    * @return The workflows using the API, by document, with the number of workflows calling each of its operations
    */
   @GET
   @Path("/services/{serviceId}/workflows")
   public ArazzoApiUsage getApiUsage(@PathParam("serviceId") String serviceId) {
      return arazzoWorkflowService.getApiUsage(serviceId);
   }
}
