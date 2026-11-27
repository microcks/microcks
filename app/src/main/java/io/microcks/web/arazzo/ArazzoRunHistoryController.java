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

import io.microcks.domain.WorkflowRunCounts;
import io.microcks.domain.WorkflowRunStatus;
import io.microcks.domain.WorkflowRunSummary;
import io.microcks.domain.WorkflowRunTarget;
import io.microcks.repository.WorkflowRunFilter;
import io.microcks.service.ArazzoRunHistoryService;


import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Endpoints reading the runs history of the workflows of an Arazzo document, newest first, filtered by workflow, target
 * and result.
 * @author SebastienDegodez
 */
@Path("/api/arazzo")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ArazzoRunHistoryController {

   private final ArazzoRunHistoryService arazzoRunHistoryService;

   /**
    * Build a new ArazzoRunHistoryController with its dependencies.
    * @param arazzoRunHistoryService to read the runs history
    */
   @Inject
   public ArazzoRunHistoryController(ArazzoRunHistoryService arazzoRunHistoryService) {
      this.arazzoRunHistoryService = arazzoRunHistoryService;
   }

   @GET
   @Path("/documents/{documentId}/runs")
   public List<WorkflowRunSummary> listRuns(@PathParam("documentId") String documentId,
         @QueryParam("workflow") String workflowId,
         @QueryParam("target") String target,
         @QueryParam("result") String result,
         @QueryParam("page") @DefaultValue("0") int page,
         @QueryParam("size") @DefaultValue("20") int size) {
      return arazzoRunHistoryService.getRuns(documentId, filter(workflowId, target, result), page, size);
   }

   @GET
   @Path("/documents/{documentId}/runs/count")
   public WorkflowRunCounts countRuns(@PathParam("documentId") String documentId,
         @QueryParam("workflow") String workflowId,
         @QueryParam("target") String target,
         @QueryParam("result") String result) {
      return arazzoRunHistoryService.countRuns(documentId, filter(workflowId, target, result));
   }

   /** Spring answered 400 to an unknown enum value in a query parameter; JAX-RS would answer 404, so parse it here. */
   private static WorkflowRunFilter filter(String workflowId, String target, String result) {
      return new WorkflowRunFilter(workflowId, valueOf(WorkflowRunTarget.class, "target", target),
            valueOf(WorkflowRunStatus.class, "result", result));
   }

   private static <E extends Enum<E>> E valueOf(Class<E> type, String name, String value) {
      if (value == null || value.isEmpty()) {
         return null;
      }
      try {
         return Enum.valueOf(type, value);
      } catch (IllegalArgumentException iae) {
         throw new BadRequestException("Unknown " + name + ": " + value);
      }
   }
}
