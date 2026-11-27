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
import io.microcks.security.MembershipService;
import io.microcks.security.OidcConfig;
import io.microcks.security.ServiceAccountService;
import io.microcks.security.TokenService;
import io.microcks.security.User;
import io.microcks.security.UserInfo;
import io.microcks.security.UserService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the OIDC login flow against a fake identity provider.
 * @author laurent
 */
class OidcLoginTest {

   private HttpServer idp;
   private OidcLoginController controller;
   private OidcConfig oidc;
   private MembershipService membershipService;
   private UserService userService;
   private TokenService tokens;
   private volatile String idTokenToReturn;

   @BeforeEach
   void setUp() throws Exception {
      idp = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      idp.createContext("/token", exchange -> {
         byte[] body = ("{\"id_token\":\"" + idTokenToReturn + "\"}").getBytes(StandardCharsets.UTF_8);
         exchange.getResponseHeaders().add("Content-Type", "application/json");
         exchange.sendResponseHeaders(200, body.length);
         exchange.getResponseBody().write(body);
         exchange.close();
      });
      idp.start();

      AuthConfig authConfig = mock(AuthConfig.class);
      when(authConfig.enabled()).thenReturn(true);
      when(authConfig.issuer()).thenReturn("microcks");
      when(authConfig.tokenTtl()).thenReturn(Duration.ofMinutes(5));
      when(authConfig.privateKeyLocation()).thenReturn(Optional.empty());
      when(authConfig.publicKeyLocation()).thenReturn(Optional.empty());
      tokens = new TokenService(authConfig);

      oidc = new OidcConfig();
      oidc.enabled = true;
      oidc.authorizationUrl = Optional.of("http://idp/auth");
      oidc.tokenUrl = Optional.of("http://127.0.0.1:" + idp.getAddress().getPort() + "/token");
      oidc.clientId = "microcks-app";
      oidc.clientSecret = Optional.of("secret");
      oidc.scopes = List.of("openid");
      oidc.publicUrl = "http://localhost:8080";
      oidc.allowedRedirectUris = Optional.of(List.of("https://ui.example.com/callback"));
      oidc.allowCliLoopbackRedirect = true;
      oidc.groupsClaim = "groups";
      oidc.roleClaim = Optional.empty();
      oidc.roleGroupPrefix = Optional.of("microcks");
      oidc.adminGroup = Optional.empty();
      oidc.managerGroup = Optional.empty();
      oidc.guardGroup = Optional.empty();
      oidc.defaultRoles = List.of("user");
      oidc.guardClaim = Optional.empty();

      controller = new OidcLoginController();
      controller.config = oidc;
      controller.tokenService = tokens;
      controller.mapper = new ObjectMapper();
      membershipService = mock(MembershipService.class);
      controller.memberships = membershipService;
      userService = mock(UserService.class);
      when(userService.recordLogin(any(), any(), anyBoolean())).thenReturn(new User());
      controller.userService = userService;
   }

   @AfterEach
   void tearDown() {
      idp.stop(0);
   }

   private static String jwt(String json) {
      return "h." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8))
            + ".s";
   }

   private static String param(URI uri, String name) {
      for (String pair : uri.getQuery().split("&")) {
         if (pair.startsWith(name + "=")) {
            return java.net.URLDecoder.decode(pair.substring(name.length() + 1), StandardCharsets.UTF_8);
         }
      }
      return null;
   }

   @Test
   void redirectUrisAreStrictlyValidated() {
      assertTrue(controller.login("https://ui.example.com/callback").getStatus() == 303);
      assertTrue(controller.login("http://localhost:5560").getStatus() == 303);
      for (String bad : new String[] { null, "https://evil.example.com/", "http://localhost:8080",
            "http://localhost:5560?x=1", "https://ui.example.com/callback#f", "/relative" }) {
         assertEquals(400, controller.login(bad).getStatus(), String.valueOf(bad));
      }
   }

   @Test
   void callbackConvertsIdpIdentityIntoMicrocksToken() {
      Response login = controller.login("https://ui.example.com/callback");
      URI idpUrl = login.getLocation();
      String state = param(idpUrl, "state");
      String nonce = param(idpUrl, "nonce");

      idTokenToReturn = jwt("{\"aud\":\"microcks-app\",\"exp\":" + (System.currentTimeMillis() / 1000 + 300)
            + ",\"nonce\":\"" + nonce + "\",\"preferred_username\":\"jane\",\"email\":\"jane@example.com\","
            + "\"groups\":[\"/microcks-manager\",\"microcks-manager\",\"other\"],\"email_verified\":true}");
      Response ok = controller.callback("the-code", state, null, nonce);
      assertEquals(303, ok.getStatus());
      assertTrue(ok.getLocation().toString().startsWith("https://ui.example.com/callback?token="));

      UserInfo user = tokens.verify(param(ok.getLocation(), "token")).orElseThrow();
      assertEquals("jane", user.getUsername());
      assertArrayEquals(new String[] { "manager" }, user.getRoles());
      verify(membershipService).claimPending("jane", "jane@example.com");
      verify(userService).recordLogin(any(UserInfo.class), any(), eq(true));

      // Another browser (wrong nonce cookie) or a forged state is rejected.
      assertEquals(401, controller.callback("the-code", state, null, "other").getStatus());
      assertEquals(401, controller.callback("the-code", "forged", null, nonce).getStatus());
      // A state is not an access token.
      assertTrue(tokens.verify(state).isEmpty());
   }

   @Test
   void callbackRejectsWrongNonceAudienceAndFallsBackToDefaultRole() {
      URI idpUrl = controller.login("https://ui.example.com/callback").getLocation();
      String state = param(idpUrl, "state");
      String nonce = param(idpUrl, "nonce");
      long exp = System.currentTimeMillis() / 1000 + 300;

      idTokenToReturn = jwt("{\"aud\":\"microcks-app\",\"exp\":" + exp + ",\"nonce\":\"nope\",\"sub\":\"x\"}");
      assertEquals(401, controller.callback("c", state, null, nonce).getStatus());

      idTokenToReturn = jwt("{\"aud\":\"other\",\"exp\":" + exp + ",\"nonce\":\"" + nonce + "\",\"sub\":\"x\"}");
      assertEquals(401, controller.callback("c", state, null, nonce).getStatus());

      idTokenToReturn = jwt(
            "{\"aud\":[\"microcks-app\"],\"exp\":" + exp + ",\"nonce\":\"" + nonce + "\",\"sub\":\"x\"}");
      Response ok = controller.callback("c", state, null, nonce);
      assertEquals(303, ok.getStatus());
      assertArrayEquals(new String[] { "user" },
            tokens.verify(param(ok.getLocation(), "token")).orElseThrow().getRoles());
   }

   @Test
   void rolesAreOptionallyDeducedFromClaimsAndGroups() throws Exception {
      JsonNode token = controller.mapper.readTree("{\"groups\":[\"microcks:admin\",\"acme-devs\",\"Ops\"],"
            + "\"app\":{\"role\":[\"Manager\"]},\"plan\":\"user\"}");

      // Nothing configured: everybody gets the default role.
      oidc.roleGroupPrefix = Optional.empty();
      assertEquals(java.util.Set.of("user"), controller.resolveRoles(token));

      // A claim, possibly nested and multi-valued, gives the roles directly (case-insensitive).
      oidc.roleClaim = Optional.of("app.role");
      assertEquals(java.util.Set.of("manager"), controller.resolveRoles(token));
      oidc.roleClaim = Optional.of("plan");
      assertEquals(java.util.Set.of("user"), controller.resolveRoles(token));
      oidc.roleClaim = Optional.empty();

      // Groups with a prefix, whatever the separator.
      oidc.roleGroupPrefix = Optional.of("microcks");
      assertEquals(java.util.Set.of("admin"), controller.resolveRoles(token));

      // Explicit group names, and sources are combined; unknown values are ignored.
      oidc.roleGroupPrefix = Optional.of("acme");
      oidc.managerGroup = Optional.of("Ops");
      oidc.adminGroup = Optional.of("microcks:admin");
      assertEquals(java.util.Set.of("manager", "admin"), controller.resolveRoles(token));
   }

   @Test
   void guardRequiresGroupAndClaimWhenConfigured() throws Exception {
      JsonNode token = controller.mapper.readTree("{\"groups\":[\"/staff\"],\"allowed\":true}");
      oidc.guardGroup = Optional.of("staff");
      oidc.guardClaim = Optional.of("allowed=true");
      assertTrue(controller.guardAllows(token));
      oidc.guardGroup = Optional.of("contractors");
      assertFalse(controller.guardAllows(token));
      oidc.guardGroup = Optional.of("staff");
      oidc.guardClaim = Optional.of("allowed=false");
      assertFalse(controller.guardAllows(token));
   }

   @Test
   void serviceAccountBootstrapCredentialsAreChecked() {
      ServiceAccountService service = new ServiceAccountService();
      service.bootstrapClientId = Optional.of("minion");
      service.bootstrapClientSecret = Optional.of("s3cret");
      service.bootstrapRoles = List.of("user");
      service.repository = org.mockito.Mockito.mock(io.microcks.security.ServiceAccountRepository.class);
      assertArrayEquals(new String[] { "user" }, service.authenticate("minion", "s3cret").orElseThrow().getRoles());
      assertTrue(service.authenticate("minion", "bad").isEmpty());
      assertTrue(service.authenticate(null, null).isEmpty());
   }
}
