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
package io.microcks.security;

import jakarta.inject.Singleton;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;

/**
 * Configuration of the external OIDC identity provider (prefix {@code microcks.auth.oidc}). The IDP is only used at
 * login time: the identity it asserts is converted into a Microcks-issued token.
 * @author laurent
 */
// A singleton, not a normal-scoped bean: its public fields are read directly and a client proxy would hide them.
@Singleton
public class OidcConfig {

   @ConfigProperty(name = "microcks.auth.oidc.enabled", defaultValue = "false")
   public boolean enabled;

   /** @return Whether OIDC login is configured */
   public boolean isEnabled() {
      return enabled && authorizationUrl.isPresent() && tokenUrl.isPresent();
   }

   @ConfigProperty(name = "microcks.auth.oidc.authorization-url")
   public Optional<String> authorizationUrl;

   @ConfigProperty(name = "microcks.auth.oidc.token-url")
   public Optional<String> tokenUrl;

   @ConfigProperty(name = "microcks.auth.oidc.client-id", defaultValue = "microcks-app")
   public String clientId;

   @ConfigProperty(name = "microcks.auth.oidc.client-secret")
   public Optional<String> clientSecret;

   @ConfigProperty(name = "microcks.auth.oidc.scopes", defaultValue = "openid,profile,email")
   public List<String> scopes;

   /** Public base URL of this Microcks instance, used to build the callback URL given to the IDP. */
   @ConfigProperty(name = "microcks.auth.oidc.public-url", defaultValue = "http://localhost:8080")
   public String publicUrl;

   @ConfigProperty(name = "microcks.auth.oidc.allowed-redirect-uris")
   public Optional<List<String>> allowedRedirectUris;

   @ConfigProperty(name = "microcks.auth.oidc.allow-cli-loopback-redirect", defaultValue = "true")
   public boolean allowCliLoopbackRedirect;

   /** Name of the token claim holding the groups of the user (a JSON array of strings). */
   @ConfigProperty(name = "microcks.auth.oidc.groups-claim", defaultValue = "groups")
   public String groupsClaim;

   /**
    * Optional: name (dot separated path allowed) of a token claim whose value, a string or an array, directly gives the
    * Microcks roles of the user (user, manager, admin), eg. <code>microcks_role</code>.
    */
   @ConfigProperty(name = "microcks.auth.oidc.role-claim")
   public Optional<String> roleClaim;

   /**
    * Optional: prefix of the groups that carry a Microcks role. With <code>microcks</code>, the group
    * <code>microcks-admin</code> (or <code>microcks/admin</code>, <code>microcks:admin</code>) grants the admin role.
    */
   @ConfigProperty(name = "microcks.auth.oidc.role-group-prefix")
   public Optional<String> roleGroupPrefix;

   /** Optional: name of the group granting the admin role, whatever the naming convention of the IDP. */
   @ConfigProperty(name = "microcks.auth.oidc.admin-group")
   public Optional<String> adminGroup;

   /** Optional: name of the group granting the manager role. */
   @ConfigProperty(name = "microcks.auth.oidc.manager-group")
   public Optional<String> managerGroup;

   /** Roles granted when no role mapping rule matches (or none is configured). */
   @ConfigProperty(name = "microcks.auth.oidc.default-roles", defaultValue = "user")
   public List<String> defaultRoles;

   /** Optional claim that must be present and true/equal to name=value, eg. microcksAllowed=true. */
   @ConfigProperty(name = "microcks.auth.oidc.guard-claim")
   public Optional<String> guardClaim;

   /** Optional group the user must belong to in order to sign in. Combined with the guard claim (both must match). */
   @ConfigProperty(name = "microcks.auth.oidc.guard-group")
   public Optional<String> guardGroup;
}
