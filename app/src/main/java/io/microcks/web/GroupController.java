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
import io.microcks.security.Group;
import io.microcks.security.GroupRepository;
import io.microcks.security.Membership;
import io.microcks.security.MembershipRepository;
import io.microcks.security.MembershipService;
import io.microcks.security.AuthorizationChecker;
import io.microcks.security.UserInfo;

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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * API managing groups and their memberships. Creating and deleting groups is reserved to admins; memberships can be
 * managed by admins and by the managers of the group.
 * @author laurent
 */
@Path("/api/groups")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class GroupController {

   @Inject
   GroupRepository groups;

   @Inject
   MembershipRepository memberships;

   @Inject
   MembershipService service;

   @Inject
   CurrentUser currentUser;

   /** @return All the groups */
   @GET
   public List<Group> list() {
      return groups.listAll();
   }

   /**
    * Create a group.
    * @param request The group name and description
    * @return The created group
    */
   @POST
   @Consumes(MediaType.APPLICATION_JSON)
   public Response create(GroupRequest request) {
      if (request == null || request.name() == null || !request.name().matches("[^/\\s]+")) {
         return Response.status(Response.Status.BAD_REQUEST).build();
      }
      if (groups.findByName(request.name()) != null) {
         return Response.status(Response.Status.CONFLICT).build();
      }
      Group group = new Group();
      group.name = request.name();
      group.description = request.description();
      groups.persist(group);
      return Response.status(Response.Status.CREATED).entity(group).build();
   }

   /**
    * Get a group.
    * @param name The group name
    * @return The group
    */
   @GET
   @Path("{name}")
   public Response get(@PathParam("name") String name) {
      Group group = groups.findByName(name);
      return group == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(group).build();
   }

   /**
    * Delete a group and its memberships.
    * @param name The group name
    * @return No content, or not found
    */
   @DELETE
   @Path("{name}")
   public Response delete(@PathParam("name") String name) {
      if (groups.findByName(name) == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      service.deleteGroup(name);
      return Response.noContent().build();
   }

   /**
    * List the members of a group.
    * @param name The group name
    * @return The memberships
    */
   @GET
   @Path("{name}/members")
   public Response members(@PathParam("name") String name) {
      if (groups.findByName(name) == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      return Response.ok(memberships.findByGroupName(name)).build();
   }

   /**
    * Add a member, by subject or by email.
    * @param name    The group name
    * @param request The role and target
    * @return The membership
    */
   @POST
   @Path("{name}/members")
   @Consumes(MediaType.APPLICATION_JSON)
   public Response addMember(@PathParam("name") String name, MemberRequest request) {
      if (!canManage(name)) {
         return Response.status(Response.Status.FORBIDDEN).build();
      }
      if (request == null) {
         return Response.status(Response.Status.BAD_REQUEST).build();
      }
      if (groups.findByName(name) == null) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      try {
         return Response.status(Response.Status.CREATED)
               .entity(service.addMember(name, request.role(), request.subject(), request.email())).build();
      } catch (IllegalArgumentException e) {
         return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", e.getMessage())).build();
      }
   }

   /**
    * Remove a member.
    * @param name The group name
    * @param id   The membership id
    * @return No content, or not found
    */
   @DELETE
   @Path("{name}/members/{id}")
   public Response removeMember(@PathParam("name") String name, @PathParam("id") String id) {
      if (!canManage(name)) {
         return Response.status(Response.Status.FORBIDDEN).build();
      }
      Membership membership = memberships.findById(id);
      if (membership == null || !name.equals(membership.groupName)) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      service.removeMember(membership);
      return Response.noContent().build();
   }

   private boolean canManage(String group) {
      UserInfo user = currentUser.get();
      return Arrays.asList(user.getRoles()).contains(AuthorizationChecker.ROLE_ADMIN)
            || Arrays.asList(user.getGroups()).contains(MembershipService.GROUP_PATH_PREFIX + "manager/" + group);
   }

   /** Group creation request. */
   public record GroupRequest(String name, String description) {
   }

   /** Member addition request. */
   public record MemberRequest(String role, String subject, String email) {
   }
}
