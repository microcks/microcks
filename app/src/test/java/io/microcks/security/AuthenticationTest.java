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

import jakarta.enterprise.inject.Instance;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the token service, the authentication filter and the authorization checker.
 * @author laurent
 */
class AuthenticationTest {

   private static final UserInfo MANAGER = new UserInfo("Jane Doe", "jane", "Jane", "Doe", "jane@example.com",
         new String[] { "user", "manager" }, new String[] { "/microcks/manager/team-a" });

   private static AuthConfig config(boolean enabled) {
      AuthConfig config = mock(AuthConfig.class);
      when(config.enabled()).thenReturn(enabled);
      when(config.issuer()).thenReturn("microcks");
      when(config.tokenTtl()).thenReturn(Duration.ofMinutes(5));
      when(config.privateKeyLocation()).thenReturn(Optional.empty());
      when(config.publicKeyLocation()).thenReturn(Optional.empty());
      return config;
   }

   private static AuthenticationFilter filter(AuthConfig config, TokenService tokens, CurrentUser current) {
      AuthenticationFilter filter = new AuthenticationFilter();
      filter.config = config;
      filter.tokenService = tokens;
      filter.currentUser = current;
      filter.memberships = mock(MembershipService.class);
      filter.userService = mock(UserService.class);
      return filter;
   }

   private static ContainerRequestContext request(String method, String path, String token) {
      ContainerRequestContext ctx = mock(ContainerRequestContext.class);
      UriInfo uri = mock(UriInfo.class);
      when(uri.getPath()).thenReturn(path);
      when(ctx.getUriInfo()).thenReturn(uri);
      when(ctx.getMethod()).thenReturn(method);
      when(ctx.getHeaderString("Authorization")).thenReturn(token == null ? null : "Bearer " + token);
      return ctx;
   }

   private static int abortedStatus(ContainerRequestContext ctx) {
      ArgumentCaptor<Response> captor = ArgumentCaptor.forClass(Response.class);
      verify(ctx).abortWith(captor.capture());
      return captor.getValue().getStatus();
   }

   @Test
   void tokenRoundTripKeepsIdentity() {
      TokenService tokens = new TokenService(config(true));
      Optional<UserInfo> user = tokens.verify(tokens.issue(MANAGER));
      assertTrue(user.isPresent());
      assertEquals("jane", user.get().getUsername());
      assertEquals("jane@example.com", user.get().getEmail());
      assertArrayEquals(MANAGER.getRoles(), user.get().getRoles());
   }

   @Test
   void expiredAndForeignTokensAreRejected() {
      TokenService tokens = new TokenService(config(true));
      assertTrue(tokens.verify(tokens.issue(MANAGER, Duration.ofSeconds(-120))).isEmpty());
      // Signed by another (ephemeral) key.
      assertTrue(tokens.verify(new TokenService(config(true)).issue(MANAGER)).isEmpty());
      assertTrue(tokens.verify("not-a-jwt").isEmpty());
   }

   @Test
   void everythingIsAllowedWithAllRolesWhenDisabled() {
      CurrentUser current = new CurrentUser();
      ContainerRequestContext ctx = request("DELETE", "/api/secrets/1", null);
      filter(config(false), new TokenService(config(false)), current).filter(ctx);
      verify(ctx, never()).abortWith(any());
      assertArrayEquals(new String[] { "user", "manager", "admin" }, current.get().getRoles());
   }

   @Test
   void protectedPathsRequireTokenAndRole() {
      AuthConfig config = config(true);
      TokenService tokens = new TokenService(config);
      CurrentUser current = new CurrentUser();
      AuthenticationFilter filter = filter(config, tokens, current);

      ContainerRequestContext anonymous = request("GET", "/api/services", null);
      filter.filter(anonymous);
      assertEquals(401, abortedStatus(anonymous));

      String token = tokens.issue(MANAGER);
      ContainerRequestContext forbidden = request("POST", "/api/secrets", token);
      filter.filter(forbidden);
      assertEquals(403, abortedStatus(forbidden));

      ContainerRequestContext allowed = request("DELETE", "/api/services/abc", token);
      filter.filter(allowed);
      verify(allowed, never()).abortWith(any());
      assertEquals("jane", current.get().getUsername());
   }

   @Test
   void publicPathsStayOpen() {
      AuthConfig config = config(true);
      CurrentUser current = new CurrentUser();
      for (String path : new String[] { "/api/auth/config", "/api/features/config", "/rest/Svc/1.0/op" }) {
         ContainerRequestContext ctx = request("GET", path, null);
         filter(config, new TokenService(config), current).filter(ctx);
         verify(ctx, never()).abortWith(any());
      }
      assertNull(current.get());
   }

   @Test
   @SuppressWarnings("unchecked")
   void checkerHonoursRolesAndDisabledMode() {
      AuthorizationChecker checker = new AuthorizationChecker();
      checker.currentUser = mock(Instance.class);
      checker.authConfig = config(false);
      assertTrue(checker.hasRole(null, AuthorizationChecker.ROLE_ADMIN));

      checker.authConfig = config(true);
      assertTrue(checker.hasRole(MANAGER, AuthorizationChecker.ROLE_MANAGER));
      assertFalse(checker.hasRole(MANAGER, AuthorizationChecker.ROLE_ADMIN));
      assertFalse(checker.hasRole(null, AuthorizationChecker.ROLE_USER));
   }
}
