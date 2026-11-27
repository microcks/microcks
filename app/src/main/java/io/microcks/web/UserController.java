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

import io.microcks.security.CurrentUser;
import io.microcks.security.User;
import io.microcks.security.UserRepository;
import io.microcks.security.UserService;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Comparator;
import java.util.List;

/**
 * API on the users known to Microcks (created at first login). Listing is open to managers, who need it to add members
 * to their groups; disabling and removing are reserved to admins.
 * @author laurent
 */
@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class UserController {

   @Inject
   UserRepository users;

   @Inject
   UserService service;

   @Inject
   CurrentUser currentUser;

   /** @return All the users, most recent login first */
   @GET
   public List<User> list() {
      return users.listAll().stream()
            .sorted(Comparator.comparing((User u) -> u.lastLoginAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
   }

   /**
    * Enable or disable a user.
    * @param id      The user id
    * @param request The new state
    * @return The user
    */
   @PUT
   @Path("{id}")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response update(@PathParam("id") String id, UserUpdate request) {
      User user = users.findById(id);
      if (user == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      if (request == null || request.disabled() == null) {
         return Response.status(Response.Status.BAD_REQUEST).build();
      }
      if (request.disabled() && isSelf(user)) {
         return Response.status(Response.Status.CONFLICT).entity("You cannot disable yourself").build();
      }
      service.setDisabled(user, request.disabled());
      return Response.ok(user).build();
   }

   /**
    * Remove a user record; it is created again at its next login.
    * @param id The user id
    * @return No content, or not found
    */
   @DELETE
   @Path("{id}")
   public Response delete(@PathParam("id") String id) {
      User user = users.findById(id);
      if (user == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      if (isSelf(user)) {
         return Response.status(Response.Status.CONFLICT).entity("You cannot remove yourself").build();
      }
      service.delete(user);
      return Response.noContent().build();
   }

   private boolean isSelf(User user) {
      return currentUser.get() != null && user.username.equals(currentUser.get().getUsername());
   }

   /** User update request. */
   public record UserUpdate(Boolean disabled) {
   }
}
