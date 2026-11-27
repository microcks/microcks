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

import io.microcks.security.AuthorizationChecker;
import io.microcks.security.MembershipService;
import io.microcks.security.OidcConfig;
import io.microcks.security.TokenService;
import io.microcks.security.User;
import io.microcks.security.UserInfo;
import io.microcks.security.UserService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.inject.Inject;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.jose4j.jwt.JwtClaims;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Drives the OIDC authorization code flow as a confidential client. The external IDP is only used here: once the user
 * is identified, its identity is converted into a Microcks-issued token handed to the caller (web UI or CLI) through
 * the validated {@code redirect_uri}. The login state is a signed JWT bound to the browser by a cookie, so no server
 * side session or shared cache is needed (works for several replicas and in the uber distribution).
 * @author laurent
 */
@Path("/api/auth")
@RunOnVirtualThread
public class OidcLoginController {

   private static final Logger log = LoggerFactory.getLogger(OidcLoginController.class);

   private static final String NONCE_COOKIE = "microcks_oidc_nonce";
   private static final SecureRandom RANDOM = new SecureRandom();
   private static final Set<String> MICROCKS_ROLES = Set.of("user", "manager", "admin");

   @Inject
   MembershipService memberships;

   @Inject
   UserService userService;

   @Inject
   OidcConfig config;

   @Inject
   TokenService tokenService;

   @Inject
   ObjectMapper mapper;

   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

   /**
    * Start the login: redirect the browser to the IDP.
    * @param redirectUri Where to send the browser, with a {@code token} query parameter, once logged in
    * @return A redirect to the IDP
    */
   @GET
   @Path("/login/oidc")
   public Response login(@QueryParam("redirect_uri") String redirectUri) {
      if (!config.isEnabled()) {
         return Response.status(Response.Status.NOT_FOUND).entity("OIDC login is not enabled").build();
      }
      if (!isAllowedRedirect(redirectUri)) {
         return Response.status(Response.Status.BAD_REQUEST).entity("Invalid redirect_uri").build();
      }
      String nonce = randomString();
      String state = tokenService.issueState(nonce, redirectUri);
      String url = config.authorizationUrl.get() + (config.authorizationUrl.get().contains("?") ? "&" : "?")
            + "response_type=code&client_id=" + enc(config.clientId) + "&redirect_uri=" + enc(callbackUrl()) + "&scope="
            + enc(String.join(" ", config.scopes)) + "&state=" + enc(state) + "&nonce=" + enc(nonce);
      NewCookie cookie = new NewCookie.Builder(NONCE_COOKIE).value(nonce).path("/api/auth").httpOnly(true)
            .secure(config.publicUrl.startsWith("https")).sameSite(NewCookie.SameSite.LAX).maxAge(300).build();
      return Response.seeOther(URI.create(url)).cookie(cookie).build();
   }

   /**
    * Complete the login: exchange the code, map IDP claims to Microcks roles and issue a Microcks token.
    * @param code        The authorization code
    * @param state       The signed state created at login
    * @param error       An error returned by the IDP
    * @param nonceCookie The browser-bound nonce
    * @return A redirect to the original redirect_uri carrying the Microcks token
    */
   @GET
   @Path("/callback/oidc")
   public Response callback(@QueryParam("code") String code, @QueryParam("state") String state,
         @QueryParam("error") String error, @CookieParam(NONCE_COOKIE) String nonceCookie) {
      if (!config.isEnabled()) {
         return Response.status(Response.Status.NOT_FOUND).build();
      }
      if (error != null) {
         return unauthorized("IDP returned an error: " + error);
      }
      Optional<JwtClaims> claims = state == null ? Optional.empty() : tokenService.verifyState(state);
      if (claims.isEmpty() || code == null) {
         return unauthorized("Invalid or expired login state");
      }
      try {
         String nonce = claims.get().getStringClaimValue("nonce");
         String redirectUri = claims.get().getStringClaimValue("redirect_uri");
         if (nonceCookie == null || !nonce.equals(nonceCookie) || !isAllowedRedirect(redirectUri)) {
            return unauthorized("Login state does not match this browser");
         }
         JsonNode tokens = exchangeCode(code);
         JsonNode idToken = payload(tokens.path("id_token").asText(null));
         if (idToken == null || !nonce.equals(idToken.path("nonce").asText())) {
            return unauthorized("Invalid ID token");
         }
         if (idToken.path("exp").asLong(0) < System.currentTimeMillis() / 1000) {
            return unauthorized("Expired ID token");
         }
         if (!audienceMatches(idToken.path("aud"))) {
            return unauthorized("ID token not issued for this client");
         }
         JsonNode access = payload(tokens.path("access_token").asText(null));
         if (!guardAllows(idToken) && !guardAllows(access)) {
            return unauthorized("Access denied by guard claim");
         }
         UserInfo user = toUser(idToken, access);
         boolean emailVerified = idToken.path("email_verified").asBoolean(false);
         User stored = userService.recordLogin(user, idToken.path("sub").asText(null), emailVerified);
         if (stored.disabled) {
            return Response.status(Response.Status.FORBIDDEN).entity("This user has been disabled").build();
         }
         if (emailVerified) {
            memberships.claimPending(user.getUsername(), user.getEmail());
         }
         String sep = redirectUri.contains("?") ? "&" : "?";
         NewCookie clear = new NewCookie.Builder(NONCE_COOKIE).value("").path("/api/auth").maxAge(0).build();
         return Response.seeOther(URI.create(redirectUri + sep + "token=" + enc(tokenService.issue(user))))
               .cookie(clear).build();
      } catch (Exception e) {
         log.warn("OIDC callback failed: {}", e.getMessage());
         return unauthorized("OIDC login failed");
      }
   }

   private JsonNode exchangeCode(String code) throws Exception {
      StringBuilder body = new StringBuilder("grant_type=authorization_code&code=" + enc(code) + "&redirect_uri="
            + enc(callbackUrl()) + "&client_id=" + enc(config.clientId));
      HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(config.tokenUrl.orElseThrow()))
            .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json");
      if (config.clientSecret.isPresent()) {
         request.header("Authorization", "Basic " + Base64.getEncoder()
               .encodeToString((config.clientId + ":" + config.clientSecret.get()).getBytes(StandardCharsets.UTF_8)));
      }
      HttpResponse<String> response = httpClient.send(
            request.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
            HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
         throw new IllegalStateException("Token endpoint answered " + response.statusCode());
      }
      return mapper.readTree(response.body());
   }

   /** Decode (without verifying) the payload of a JWT received directly from the IDP token endpoint over TLS. */
   private JsonNode payload(String jwt) {
      if (jwt == null) {
         return null;
      }
      String[] parts = jwt.split("\\.");
      if (parts.length < 2) {
         return null;
      }
      try {
         return mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
      } catch (Exception e) {
         return null;
      }
   }

   private boolean audienceMatches(JsonNode aud) {
      if (aud.isArray()) {
         for (JsonNode a : aud) {
            if (config.clientId.equals(a.asText())) {
               return true;
            }
         }
         return false;
      }
      return config.clientId.equals(aud.asText());
   }

   /** Both the guard claim and the guard group, when configured, must match. */
   boolean guardAllows(JsonNode claims) {
      if (config.guardClaim.isEmpty() && config.guardGroup.isEmpty()) {
         return true;
      }
      if (claims == null) {
         return false;
      }
      if (config.guardGroup.isPresent() && !hasGroup(claims, config.guardGroup.get())) {
         return false;
      }
      if (config.guardClaim.isEmpty()) {
         return true;
      }
      String[] nv = config.guardClaim.get().split("=", 2);
      JsonNode value = lookup(claims, nv[0]);
      if (value == null) {
         return false;
      }
      String expected = nv.length > 1 ? nv[1] : "true";
      return strings(value).contains(expected);
   }

   /**
    * Microcks roles are optionally deduced from the token, with the same sources as Reshapr: a dedicated claim, the
    * groups carrying a role prefix, and explicit admin/manager groups. Matching roles are combined; when nothing
    * matches (or nothing is configured), the default roles apply.
    */
   Set<String> resolveRoles(JsonNode... sources) {
      Set<String> roles = new LinkedHashSet<>();
      for (JsonNode source : sources) {
         if (source == null) {
            continue;
         }
         config.roleClaim.ifPresent(claim -> strings(lookup(source, claim.trim())).forEach(roles::add));
         config.roleGroupPrefix.ifPresent(prefix -> {
            for (String group : strings(lookup(source, config.groupsClaim))) {
               if (group.startsWith(prefix)) {
                  String remainder = group.substring(prefix.length());
                  if (!remainder.isEmpty() && "-/:".indexOf(remainder.charAt(0)) >= 0) {
                     remainder = remainder.substring(1);
                  }
                  roles.add(remainder);
               }
            }
         });
         config.adminGroup.filter(g -> hasGroup(source, g)).ifPresent(g -> roles.add(AuthorizationChecker.ROLE_ADMIN));
         config.managerGroup.filter(g -> hasGroup(source, g))
               .ifPresent(g -> roles.add(AuthorizationChecker.ROLE_MANAGER));
      }
      Set<String> mapped = roles.stream().map(r -> r.trim().toLowerCase()).filter(MICROCKS_ROLES::contains)
            .collect(Collectors.toCollection(LinkedHashSet::new));
      return mapped.isEmpty() ? new LinkedHashSet<>(config.defaultRoles) : mapped;
   }

   /** Some IDPs (Keycloak) prefix groups with '/': both forms are accepted. */
   private boolean hasGroup(JsonNode claims, String group) {
      return strings(lookup(claims, config.groupsClaim)).stream()
            .anyMatch(g -> group.equals(g) || ("/" + group).equals(g));
   }

   private UserInfo toUser(JsonNode idToken, JsonNode access) {
      Set<String> mapped = resolveRoles(access, idToken);
      String username = firstText(idToken, "preferred_username", "email", "sub");
      return new UserInfo(firstText(idToken, "name"), username, firstText(idToken, "given_name"),
            firstText(idToken, "family_name"), firstText(idToken, "email"), mapped.toArray(new String[0]),
            new String[0]);
   }

   private static String firstText(JsonNode node, String... names) {
      for (String name : names) {
         if (node.hasNonNull(name)) {
            return node.get(name).asText();
         }
      }
      return null;
   }

   private static JsonNode lookup(JsonNode node, String path) {
      JsonNode current = node;
      if (current == null) {
         return null;
      }
      // Try the whole path first (claim names may contain dots), then walk it.
      if (current.has(path)) {
         return current.get(path);
      }
      for (String part : path.split("\\.")) {
         current = current.get(part);
         if (current == null) {
            return null;
         }
      }
      return current;
   }

   private static List<String> strings(JsonNode node) {
      List<String> result = new ArrayList<>();
      if (node == null || node.isNull()) {
         return result;
      }
      if (node.isArray()) {
         node.forEach(n -> result.add(n.asText()));
      } else {
         result.add(node.asText());
      }
      return result;
   }

   /** Redirect targets are strictly validated: allow-listed absolute URIs or CLI loopback ports 5556-5599. */
   boolean isAllowedRedirect(String redirectUri) {
      if (redirectUri == null) {
         return false;
      }
      URI uri;
      try {
         uri = URI.create(redirectUri);
      } catch (IllegalArgumentException e) {
         return false;
      }
      if (!uri.isAbsolute() || uri.getUserInfo() != null || uri.getFragment() != null || uri.getHost() == null) {
         return false;
      }
      if (config.allowCliLoopbackRedirect && "http".equals(uri.getScheme()) && uri.getQuery() == null
            && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost())) && uri.getPort() >= 5556
            && uri.getPort() <= 5599) {
         return true;
      }
      return config.allowedRedirectUris.orElse(List.of()).stream().anyMatch(a -> a.trim().equals(redirectUri));
   }

   private String callbackUrl() {
      String base = config.publicUrl.endsWith("/") ? config.publicUrl.substring(0, config.publicUrl.length() - 1)
            : config.publicUrl;
      return base + "/api/auth/callback/oidc";
   }

   private static Response unauthorized(String message) {
      return Response.status(Response.Status.UNAUTHORIZED).entity(message).build();
   }

   private static String randomString() {
      byte[] bytes = new byte[32];
      RANDOM.nextBytes(bytes);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
   }

   private static String enc(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8);
   }
}
