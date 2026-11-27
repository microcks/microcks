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
package io.microcks.minion.async.client;

import io.microcks.domain.Resource;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceView;
import io.microcks.domain.TestCaseResult;

import io.microcks.minion.async.client.dto.TestCaseReturnDTO;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

import java.util.List;

@Path("/api")
@RegisterRestClient
@RegisterClientHeaders(MicrocksAuthHeadersFactory.class)
/**
 * REST Client interface for calling various Microcks APIs.
 * @author laurent
 */
public interface MicrocksAPIConnector {

   /**
    * Retrieve a list of services from Microcks APIs.
    * @param page The page of service list to request
    * @param size The size of the page to fetch
    * @return A list of Service
    */
   @GET
   @Path("/services")
   @Produces("application/json")
   List<Service> listServices(@QueryParam("page") int page, @QueryParam("size") int size);

   /**
    * Retrieve the complete ServiceView for a Service that may contain messages definitions.
    * @param serviceId The unique identifier of Service to get the view for
    * @param messages  Whether to include full descriptions of operations messages
    * @return The complete ServiceView for Service
    */
   @GET
   @Path("/services/{id}")
   @Produces("application/json")
   ServiceView getService(@PathParam("id") String serviceId, @QueryParam("messages") boolean messages);

   /**
    * Retrieve the list of contract resources for a Service.
    * @param serviceId The unique identifier of Service to get resources for
    * @return A list of Resources associated to Service
    */
   @GET
   @Path("/resources/service/{id}")
   @Produces("application/json")
   List<Resource> getResources(@PathParam("id") String serviceId);

   /**
    * Report a TestCaseResult associated to a TestResult.
    * @param testResultId   The unique identifier of TestResult we want to report a result for
    * @param testCaseReturn A Test Case return data object for this TestResult
    * @return The created TestCaseResult following reporting
    */
   @POST
   @Path("/tests/{id}/testCaseResult")
   @Produces("application/json")
   TestCaseResult reportTestCaseResult(@PathParam("id") String testResultId, TestCaseReturnDTO testCaseReturn);
}
