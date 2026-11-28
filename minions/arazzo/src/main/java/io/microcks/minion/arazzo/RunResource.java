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
package io.microcks.minion.arazzo;

import io.microcks.minion.arazzo.client.dto.RunRequestDTO;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Endpoint Microcks posts workflow runs to. A run is accepted at once, then reported as it goes.
 * @author SebastienDegodez
 */
@Path("/api/runs")
public class RunResource {

   /** Get a JBoss logging logger. */
   private final Logger logger = Logger.getLogger(getClass());

   @Inject
   ArazzoRunManager arazzoRunManager;

   @ConfigProperty(name = "microcks.url")
   String microcksUrl;

   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   public Response launchRun(RunRequestDTO request) {
      logger.infof("Accepting run %s of workflow %s", request.runId(), request.workflowId());
      arazzoRunManager.launch(request.toSpecification(microcksUrl));
      return Response.accepted().build();
   }
}
