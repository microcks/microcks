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

import io.microcks.domain.Secret;
import io.microcks.repository.SecretRepository;
import io.microcks.security.AuthorizationChecker;
import io.microcks.security.UserInfo;
import io.microcks.util.SafeLogger;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A Rest controller for API defined on services.
 * @author laurent
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class SecretController {

   /** A safe logger for filtering user-controlled data in diagnostic messages. */
   private static final SafeLogger log = SafeLogger.getLogger(SecretController.class);

   private final SecretRepository secretRepository;
   private final AuthorizationChecker authorizationChecker;

   /**
    * Build a new SecretController with its dependencies.
    * @param secretRepository     to have access to Secrets definition
    * @param authorizationChecker to check user authorization
    */
   @Inject
   public SecretController(SecretRepository secretRepository, AuthorizationChecker authorizationChecker) {
      this.secretRepository = secretRepository;
      this.authorizationChecker = authorizationChecker;
   }

   @GET
   @Path("/secrets")
   public List<Secret> listSecrets(@QueryParam("page") @DefaultValue("0") int page,
         @QueryParam("size") @DefaultValue("20") int size) {
      log.debug("Getting secrets list for page {} and size {}", page, size);

      if (!authorizationChecker.hasRole(null, AuthorizationChecker.ROLE_ADMIN)) {
         return secretRepository.findAll(Sort.ascending("name")).page(Page.of(page, size)).list().stream()
               .map(this::filterSensitiveData).toList();
      }
      // We're admin, so we can return all secrets with sensitive data.
      return secretRepository.findAll(Sort.ascending("name")).page(Page.of(page, size)).list();
   }

   @GET
   @Path("/secrets/search")
   public List<Secret> searchSecrets(@QueryParam("name") String name) {
      log.debug("Searching secrets corresponding to {}", name);
      List<Secret> secrets = secretRepository.findByNameLike(name);
      if (!authorizationChecker.hasRole(null, AuthorizationChecker.ROLE_ADMIN)) {
         return secrets.stream().map(this::filterSensitiveData).toList();
      }
      // We're admin, so we can return all secrets with sensitive data.
      return secrets;
   }

   @GET
   @Path("/secrets/count")
   public Map<String, Long> countSecrets() {
      log.debug("Counting secrets...");
      Map<String, Long> counter = new HashMap<>();
      counter.put("counter", secretRepository.count());
      return counter;
   }

   @POST
   @Path("/secrets")
   public Response createSecret(Secret secret) {
      log.debug("Creating new secret: {}", secret);
      return saveAndRespond(secret, Response.Status.CREATED);
   }

   @GET
   @Path("/secrets/{id}")
   public Response getSecret(@PathParam("id") String secretId) {
      log.debug("Getting secret with id {}", secretId);

      if (!authorizationChecker.hasRole(null, AuthorizationChecker.ROLE_ADMIN)) {
         Secret secret = secretRepository.findById(secretId);
         if (secret != null) {
            return Response.status(Response.Status.OK).entity(filterSensitiveData(secret)).build();
         }
         return Response.ok().build();
      }
      // We're admin, so we can return all secrets with sensitive data.
      Secret found = secretRepository.findById(secretId);
      return found == null ? Response.ok().build() : Response.ok(found).build();
   }

   @PUT
   @Path("/secrets/{id}")
   public Response saveSecret(@PathParam("id") String secretId, Secret secret) {
      log.debug("Saving existing secret: {}", secret);
      return saveAndRespond(secret, Response.Status.OK);
   }

   private Response saveAndRespond(Secret secret, Response.Status status) {
      secretRepository.persistOrUpdate(secret);
      return Response.status(status).entity(secret).build();
   }

   @DELETE
   @Path("/secrets/{id}")
   public Response deleteService(@PathParam("id") String secretId) {
      log.debug("Removing secret with id {}", secretId);
      secretRepository.deleteById(secretId);
      return Response.status(Response.Status.OK).build();
   }

   /** Filter sensitive data from a Secret before returning it to the user. */
   private Secret filterSensitiveData(Secret secret) {
      secret.setUsername(null);
      secret.setPassword(null);
      secret.setToken(null);
      secret.setCaCertPem(null);
      return secret;
   }
}
