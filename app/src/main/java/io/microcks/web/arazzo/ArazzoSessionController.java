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

import io.microcks.domain.arazzo.ArazzoSession;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.service.arazzo.ArazzoRunRefusedException;
import io.microcks.service.arazzo.ArazzoSessionService;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.Map;

/**
 * Endpoints of the verification sessions of Arazzo workflows: a test opens a session on a workflow, the application it
 * tests calls the mocks naming the session, then the test asks for the verification of the session.
 * @author SebastienDegodez
 */
@Path("/api/arazzo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ArazzoSessionController {

   private final ArazzoSessionService arazzoSessionService;

   /**
    * Build a new ArazzoSessionController with its dependencies.
    * @param arazzoSessionService to open and verify sessions
    */
   @Inject
   public ArazzoSessionController(ArazzoSessionService arazzoSessionService) {
      this.arazzoSessionService = arazzoSessionService;
   }

   /**
    * The workflow a session is opened on.
    * @param documentId The id of the Arazzo document, or its title and version as {@code <title>:<version>}
    * @param workflowId The id of the workflow in that document
    * @param inputs     The inputs of the workflow whose values the calls must carry, by name; null to check none
    * @param failures   The steps set to fail in the session, by step id, with the name of the existing example of their
    *                   operation the mocks answer them with; null for none
    */
   public record ArazzoSessionRequest(String documentId, String workflowId, Map<String, Object> inputs,
         Map<String, String> failures) {

      /** A session with inputs, in which no step is set to fail. */
      public ArazzoSessionRequest(String documentId, String workflowId, Map<String, Object> inputs) {
         this(documentId, workflowId, inputs, null);
      }
   }

   @POST
   @Path("/sessions")
   public Response openSession(ArazzoSessionRequest request) {
      try {
         return arazzoSessionService.openSession(request.documentId(), request.workflowId(), request.inputs(),
                     request.failures() == null ? Map.of() : request.failures())
               .map(session -> Response.created(URI.create("/api/arazzo/sessions/" + session.getId())).entity(session)
                     .build())
               .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
      } catch (ArazzoRunRefusedException arre) {
         // The reason is plain text, whatever the client accepts, as the Microcks API describes it.
         return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(arre.getMessage())
               .build();
      }
   }

   @DELETE
   @Path("/sessions/{sessionId}")
   public Response closeSession(@PathParam("sessionId") String sessionId) {
      return arazzoSessionService.closeSession(sessionId) ? Response.noContent().build()
            : Response.status(Response.Status.NOT_FOUND).build();
   }

   @POST
   @Path("/sessions/{sessionId}/verification")
   public Response verify(@PathParam("sessionId") String sessionId) {
      return arazzoSessionService.verify(sessionId).map(result -> Response.ok(result).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
   }
}
