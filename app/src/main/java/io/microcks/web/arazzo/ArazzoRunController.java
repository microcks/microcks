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

import io.microcks.domain.arazzo.ArazzoExampleLaunch;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.service.arazzo.ArazzoRunImplementations;
import io.microcks.service.arazzo.ArazzoRunRefusedException;
import io.microcks.service.arazzo.ArazzoRunService;
import io.microcks.web.arazzo.dto.ArazzoRunLaunchDTO;
import io.microcks.web.arazzo.dto.ArazzoRunOutcomeDTO;
import io.microcks.web.arazzo.dto.ArazzoStepReportDTO;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoints running the workflows of Arazzo documents: launching a run, reading it, and receiving the reports of the
 * Arazzo minion executing it.
 * @author SebastienDegodez
 */
@Path("/api/arazzo")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ArazzoRunController {

   private final ArazzoRunService arazzoRunService;

   /**
    * Build a new ArazzoRunController with its dependencies.
    * @param arazzoRunService to run workflows and record their runs
    */
   @Inject
   public ArazzoRunController(ArazzoRunService arazzoRunService) {
      this.arazzoRunService = arazzoRunService;
   }

   @POST
   @Path("/documents/{documentId}/workflows/{workflowId}/runs")
   public Response launchRun(@PathParam("documentId") String documentId, @PathParam("workflowId") String workflowId,
         ArazzoRunLaunchDTO launch) {
      try {
         if (launch.target() != null && !launch.mixed()) {
            throw new ArazzoRunRefusedException("Unknown run target kind '" + launch.target().kind()
                  + "': a run target is mixed, or left out to run on the mocks or on the implementations");
         }
         boolean validateSchema = Boolean.TRUE.equals(launch.validateSchema());
         Optional<WorkflowResult> run = launch.example() == null
               ? arazzoRunService.launchRun(documentId, workflowId, launch.inputs(), launch.timeout(),
                     implementationsOf(launch), validateSchema)
               : arazzoRunService.launchExampleRun(documentId, workflowId, launch.example(), launch.timeout(),
                     implementationsOf(launch), validateSchema);
         return run.map(launched -> Response.created(URI.create("/api/arazzo/runs/" + launched.getId()))
               .entity(launched).build()).orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
      } catch (ArazzoRunRefusedException arre) {
         return refused(arre);
      }
   }

   /**
    * Play every recorded example of a workflow, one run per example, as a CI does in one call.
    * @param documentId The id of the Arazzo document
    * @param workflowId The id of the workflow in that document
    * @param launch     What the runs target and how; its inputs and example are not used
    * @return How the run of each example was launched, in the order of the examples; the reason in plain text when the
    *         workflow is unknown
    */
   @POST
   @Path("/documents/{documentId}/workflows/{workflowId}/examples/runs")
   public Response launchExampleRuns(@PathParam("documentId") String documentId,
         @PathParam("workflowId") String workflowId, ArazzoRunLaunchDTO launch) {
      try {
         Optional<List<ArazzoExampleLaunch>> launches = arazzoRunService.launchExampleRuns(documentId, workflowId,
               launch.timeout(), implementationsOf(launch), Boolean.TRUE.equals(launch.validateSchema()));
         return launches.map(done -> Response.ok(done).build())
               .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
      } catch (ArazzoRunRefusedException arre) {
         return refused(arre);
      }
   }

   /** The reason is plain text, whatever the client accepts, as the Microcks API describes it. */
   private static Response refused(ArazzoRunRefusedException arre) {
      return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(arre.getMessage())
            .build();
   }

   @GET
   @Path("/runs/{runId}")
   public Response getRun(@PathParam("runId") String runId) {
      return arazzoRunService.getRun(runId).map(run -> Response.ok(run).build())
            .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
   }

   @POST
   @Path("/runs/{runId}/steps")
   public Response reportStep(@PathParam("runId") String runId, ArazzoStepReportDTO report) {
      return noContentOrNotFound(
            arazzoRunService.recordStep(runId, report.toStepResult(), report.toRequest(), report.toResponse()));
   }

   @POST
   @Path("/runs/{runId}/outcome")
   public Response reportOutcome(@PathParam("runId") String runId, ArazzoRunOutcomeDTO outcome) {
      return noContentOrNotFound(arazzoRunService.recordOutcome(runId, outcome.runStatus(), outcome.outputs(),
            outcome.elapsedTime(), outcome.message()));
   }

   private static Response noContentOrNotFound(Optional<WorkflowResult> run) {
      return Response.status(run.isPresent() ? Response.Status.NO_CONTENT : Response.Status.NOT_FOUND).build();
   }

   /** The mocks unless implementations are given, or a mixed target saying where each source goes. */
   private static ArazzoRunImplementations implementationsOf(ArazzoRunLaunchDTO launch) {
      if (launch.mixed()) {
         Map<String, String> perSource = launch.target().perSource() == null ? Map.of() : launch.target().perSource();
         return new ArazzoRunImplementations(launch.implementations(), launch.secretName(), perSource);
      }
      return launch.implementations() == null ? null
            : new ArazzoRunImplementations(launch.implementations(), launch.secretName());
   }
}
