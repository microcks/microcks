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

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Resource server filter: resolves the user from the Bearer token and enforces role-based access on the protected parts
 * of the management API. Mock endpoints are never concerned. When authentication is disabled, every request is executed
 * as {@link CurrentUser#ANONYMOUS} (all roles).
 * @author laurent
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class AuthenticationFilter implements ContainerRequestFilter {

   private static final String BEARER_PREFIX = "Bearer ";

   private static final String[] USER = { AuthorizationChecker.ROLE_USER, AuthorizationChecker.ROLE_MANAGER,
         AuthorizationChecker.ROLE_ADMIN };
   private static final String[] MANAGER = { AuthorizationChecker.ROLE_MANAGER, AuthorizationChecker.ROLE_ADMIN };
   private static final String[] ADMIN = { AuthorizationChecker.ROLE_ADMIN };

   /** Access rules, first match wins. Paths not matching any rule are public. */
   private static final List<AccessRule> RULES = List.of(new AccessRule("GET", "/api/services/*", USER),
         new AccessRule(null, "/api/services", USER), new AccessRule(null, "/api/jobs", USER),
         new AccessRule(null, "/api/jobs/*", USER), new AccessRule(null, "/api/services/*", MANAGER),
         new AccessRule(null, "/api/services/*/*", MANAGER), new AccessRule(null, "/api/jobs/*/*", MANAGER),
         new AccessRule(null, "/api/artifact/*", MANAGER),
         new AccessRule("POST", "/api/workflows/documents", MANAGER),
         new AccessRule("POST", "/api/workflows/documents/analysis", MANAGER),
         new AccessRule("POST", "/api/workflows/documents/*/analysis", MANAGER),
         new AccessRule("PUT", "/api/workflows/documents/*/sources", MANAGER),
         new AccessRule(null, "/api/import", ADMIN),
         new AccessRule(null, "/api/export", ADMIN), new AccessRule("GET", "/api/secrets", USER),
         new AccessRule("GET", "/api/secrets/*", USER), new AccessRule(null, "/api/secrets", ADMIN),
         new AccessRule(null, "/api/secrets/*", ADMIN), new AccessRule("GET", "/api/groups", USER),
         new AccessRule("GET", "/api/groups/*", USER), new AccessRule("GET", "/api/groups/*/members", USER),
         new AccessRule(null, "/api/groups", ADMIN), new AccessRule(null, "/api/groups/*", ADMIN),
         new AccessRule(null, "/api/groups/*/members", MANAGER),
         new AccessRule(null, "/api/groups/*/members/*", MANAGER), new AccessRule("GET", "/api/users", MANAGER),
         new AccessRule(null, "/api/users", ADMIN), new AccessRule(null, "/api/users/*", ADMIN),
         new AccessRule(null, "/api/service-accounts", ADMIN), new AccessRule(null, "/api/service-accounts/*", ADMIN));

   @Inject
   AuthConfig config;

   @Inject
   TokenService tokenService;

   @Inject
   CurrentUser currentUser;

   @Inject
   MembershipService memberships;

   @Inject
   UserService userService;

   private String[] groupsOf(UserInfo user) {
      return memberships.groupPaths(user.getUsername()).toArray(new String[0]);
   }

   @Override
   public void filter(ContainerRequestContext ctx) {
      if (!config.enabled()) {
         currentUser.set(CurrentUser.ANONYMOUS);
         return;
      }

      String header = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
      Optional<UserInfo> resolved = Optional.empty();
      if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
         resolved = tokenService.verify(header.substring(BEARER_PREFIX.length()).trim());
      }
      // A user disabled by an admin loses access immediately, even with a token not yet expired.
      final Optional<UserInfo> user = resolved.filter(u -> !userService.isDisabled(u.getUsername()));
      user.ifPresent(u -> currentUser.set(u.withGroups(groupsOf(u))));

      String path = normalize(ctx.getUriInfo().getPath());
      for (AccessRule rule : RULES) {
         if (rule.matches(ctx.getMethod(), path)) {
            if (user.isEmpty()) {
               ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                     .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build());
            } else if (Arrays.stream(rule.roles).noneMatch(r -> Arrays.asList(user.get().getRoles()).contains(r))) {
               ctx.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            }
            return;
         }
      }
   }

   private static String normalize(String path) {
      String p = path.startsWith("/") ? path : "/" + path;
      return p.length() > 1 && p.endsWith("/") ? p.substring(0, p.length() - 1) : p;
   }

   private record AccessRule(String method, String pattern, String[] roles) {

      boolean matches(String requestMethod, String path) {
         if (method != null && !method.equalsIgnoreCase(requestMethod)) {
            return false;
         }
         String[] expected = pattern.split("/");
         String[] actual = path.split("/");
         if (expected.length != actual.length) {
            return false;
         }
         for (int i = 0; i < expected.length; i++) {
            if (!"*".equals(expected[i]) && !expected[i].equals(actual[i])) {
               return false;
            }
         }
         return true;
      }
   }
}
