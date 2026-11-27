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
package io.microcks.minion.arazzo.client;

import io.microcks.minion.arazzo.client.dto.RunOutcomeDTO;
import io.microcks.minion.arazzo.client.dto.StepReportDTO;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.MediaType;

/**
 * REST client of the Microcks APIs the minion reports runs to.
 * @author SebastienDegodez
 */
@Path("/api/arazzo/runs")
@RegisterRestClient(configKey = "microcks-api")
public interface MicrocksAPIConnector {

   /**
    * Report a step of a run as it ends.
    * @param runId  The id of the run
    * @param report The step
    */
   @POST
   @Path("/{runId}/steps")
   @Consumes(MediaType.APPLICATION_JSON)
   void reportStep(@PathParam("runId") String runId, StepReportDTO report);

   /**
    * Report the end of a run.
    * @param runId   The id of the run
    * @param outcome How it ended
    */
   @POST
   @Path("/{runId}/outcome")
   @Consumes(MediaType.APPLICATION_JSON)
   void reportOutcome(@PathParam("runId") String runId, RunOutcomeDTO outcome);
}
