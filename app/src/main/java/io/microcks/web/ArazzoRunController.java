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

import io.microcks.domain.WorkflowResult;
import io.microcks.service.ArazzoRunImplementations;
import io.microcks.service.ArazzoRunRefusedException;
import io.microcks.service.ArazzoRunService;
import io.microcks.web.dto.ArazzoRunLaunchDTO;
import io.microcks.web.dto.ArazzoRunOutcomeDTO;
import io.microcks.web.dto.ArazzoStepReportDTO;


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
         Optional<WorkflowResult> run = arazzoRunService.launchRun(documentId, workflowId, launch.inputs(),
               launch.timeout(), implementationsOf(launch));
         return run.map(launched -> Response.created(URI.create("/api/arazzo/runs/" + launched.getId()))
               .entity(launched).build()).orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
      } catch (ArazzoRunRefusedException arre) {
         return Response.status(Response.Status.BAD_REQUEST).type(MediaType.TEXT_PLAIN).entity(arre.getMessage())
               .build();
      }
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
