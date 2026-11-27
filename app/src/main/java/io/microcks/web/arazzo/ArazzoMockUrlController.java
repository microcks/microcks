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

import io.microcks.service.arazzo.ArazzoMockUrlAnswer;
import io.microcks.service.arazzo.ArazzoMockUrlService;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Mock URL of each workflow of the imported Arazzo documents: an application posts the inputs of the workflow,
 * Microcks plays it on its mocks and answers with its outputs. Like the other mock endpoints, it is served outside of
 * {@code /api}, with no authentication.
 * @author SebastienDegodez
 */
@Path("/arazzo")
@RunOnVirtualThread
public class ArazzoMockUrlController {

   /** The header giving the id of the run that played the workflow, to read it in the Microcks API. */
   public static final String RUN_ID_HEADER = "X-Microcks-Arazzo-Run-Id";

   private final ArazzoMockUrlService arazzoMockUrlService;

   /**
    * Build a new ArazzoMockUrlController with its required dependencies.
    * @param arazzoMockUrlService The service playing the workflows on the mocks
    */
   @Inject
   public ArazzoMockUrlController(ArazzoMockUrlService arazzoMockUrlService) {
      this.arazzoMockUrlService = arazzoMockUrlService;
   }

   /**
    * Play a workflow on the mocks with the posted inputs.
    * @param documentTitle   The title of the Arazzo document, a + standing for a space as in the API mock URLs
    * @param documentVersion The version of the Arazzo document
    * @param workflowId      The id of the workflow
    * @param inputs          The inputs of the workflow, as a JSON object; none when the workflow needs none
    * @return 200 with exactly the outputs of the workflow, else the failure status with the run, the failed step and
    *         the reason
    */
   @POST
   @Path("/{documentTitle}/{documentVersion}/{workflowId}")
   @Consumes(MediaType.WILDCARD)
   @Produces(MediaType.APPLICATION_JSON)
   public Response play(@PathParam("documentTitle") String documentTitle,
         @PathParam("documentVersion") String documentVersion, @PathParam("workflowId") String workflowId,
         String inputs) {
      ArazzoMockUrlAnswer answer = arazzoMockUrlService.play(documentTitle.replace('+', ' '), documentVersion,
            workflowId, inputs);
      Response.ResponseBuilder response = Response.status(answer.status());
      if (answer.runId() != null) {
         response.header(RUN_ID_HEADER, answer.runId());
      }
      return response.entity(answer.status() == 200 ? answer.outputs() : failureOf(answer)).build();
   }

   /** A failure gives the run, the failed step and the reason, those it has. */
   private static Map<String, Object> failureOf(ArazzoMockUrlAnswer answer) {
      Map<String, Object> failure = new LinkedHashMap<>();
      if (answer.runId() != null) {
         failure.put("runId", answer.runId());
      }
      if (answer.failedStep() != null) {
         failure.put("failedStep", answer.failedStep());
      }
      failure.put("reason", answer.reason());
      return failure;
   }
}
