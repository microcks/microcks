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

import io.microcks.security.ServiceAccount;
import io.microcks.security.ServiceAccountRepository;
import io.microcks.security.ServiceAccountService;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

/**
 * Admin API managing service accounts. Access is restricted to admins by the authentication filter.
 * @author laurent
 */
@Path("/api/service-accounts")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class ServiceAccountController {

   private static final List<String> ALLOWED_ROLES = List.of("user", "manager", "admin");

   @Inject
   ServiceAccountRepository repository;

   @Inject
   ServiceAccountService service;

   /** @return The service accounts, without any secret */
   @GET
   public List<Map<String, Object>> list() {
      return repository.listAll().stream().map(a -> Map.<String, Object>of("id", a.id, "clientId", a.clientId,
            "description", a.description == null ? "" : a.description, "roles", a.roles)).toList();
   }

   /**
    * Create a service account.
    * @param request The client id, description and roles
    * @return The created account with its secret, shown only once
    */
   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   public Response create(CreateRequest request) {
      if (request == null || request.clientId() == null || request.clientId().isBlank() || request.roles() == null
            || request.roles().isEmpty() || !ALLOWED_ROLES.containsAll(request.roles())) {
         return Response.status(Response.Status.BAD_REQUEST).build();
      }
      if (repository.findByClientId(request.clientId()) != null) {
         return Response.status(Response.Status.CONFLICT).build();
      }
      String secret = service.create(request.clientId(), request.description(), request.roles());
      return Response.status(Response.Status.CREATED)
            .entity(Map.of("clientId", request.clientId(), "clientSecret", secret)).build();
   }

   /**
    * Delete a service account.
    * @param id The account id
    * @return 204 or 404
    */
   @DELETE
   @Path("/{id}")
   public Response delete(@PathParam("id") String id) {
      ServiceAccount account = repository.findById(id);
      if (account == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      repository.deleteById(id);
      return Response.noContent().build();
   }

   /**
    * Service account creation request.
    * @param clientId    The unique client id
    * @param description A description
    * @param roles       Granted roles among user, manager, admin
    */
   public record CreateRequest(String clientId, String description, List<String> roles) {
   }
}
