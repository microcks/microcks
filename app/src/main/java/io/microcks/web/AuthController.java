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

import io.microcks.security.AuthConfig;
import io.microcks.security.CurrentUser;
import io.microcks.security.OidcConfig;
import io.microcks.security.ServiceAccountService;
import io.microcks.security.TokenService;
import io.microcks.security.UserInfo;

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Exposes the authentication mode to the web UI and the identity of the caller.
 * @author laurent
 */
@Path("/api/auth")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class AuthController {

   @Inject
   AuthConfig config;

   @Inject
   CurrentUser currentUser;

   @Inject
   OidcConfig oidcConfig;

   @Inject
   TokenService tokenService;

   @Inject
   ServiceAccountService serviceAccounts;

   /**
    * Tell clients whether authentication is enforced.
    * @return The authentication configuration
    */
   @GET
   @Path("/config")
   public AuthInfo getConfig() {
      return new AuthInfo(config.enabled(), config.enabled() && oidcConfig.isEnabled());
   }

   /**
    * Get the user the current request is executed as.
    * @return The user or 401 if authentication is enabled and the caller is not authenticated
    */
   @GET
   @Path("/me")
   public Response me() {
      UserInfo user = currentUser.get();
      if (user == null) {
         return Response.status(Response.Status.UNAUTHORIZED).header("WWW-Authenticate", "Bearer").build();
      }
      return Response.ok(user).build();
   }

   /**
    * OAuth2 client credentials grant for service accounts (minions, CI, CLI). Credentials are accepted either as form
    * parameters or through HTTP Basic.
    * @param grantType     Must be client_credentials
    * @param clientId      The service account client id
    * @param clientSecret  The service account secret
    * @param authorization An optional HTTP Basic header
    * @return A Microcks access token
    */
   @POST
   @Path("/token")
   @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
   public Response token(@FormParam("grant_type") String grantType, @FormParam("client_id") String clientId,
         @FormParam("client_secret") String clientSecret, @HeaderParam("Authorization") String authorization) {
      if (!"client_credentials".equals(grantType)) {
         return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "unsupported_grant_type")).build();
      }
      if (authorization != null && authorization.regionMatches(true, 0, "Basic ", 0, 6)) {
         String[] pair = new String(Base64.getDecoder().decode(authorization.substring(6).trim()),
               StandardCharsets.UTF_8).split(":", 2);
         if (pair.length == 2) {
            clientId = pair[0];
            clientSecret = pair[1];
         }
      }
      Optional<UserInfo> account = serviceAccounts.authenticate(clientId, clientSecret);
      if (account.isEmpty()) {
         return Response.status(Response.Status.UNAUTHORIZED).entity(Map.of("error", "invalid_client")).build();
      }
      return Response.ok(Map.of("access_token", tokenService.issue(account.get()), "token_type", "Bearer", "expires_in",
            config.tokenTtl().toSeconds())).build();
   }

   /**
    * Authentication configuration for the web UI.
    * @param enabled     Whether API authentication is enforced
    * @param oidcEnabled Whether login through an external OIDC provider is available
    */
   public record AuthInfo(boolean enabled, boolean oidcEnabled) {
   }
}
