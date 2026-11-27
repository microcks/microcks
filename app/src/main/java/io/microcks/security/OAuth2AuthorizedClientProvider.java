/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.microcks.security;

import io.microcks.domain.OAuth2AuthorizedClient;
import io.microcks.domain.OAuth2ClientContext;
import io.microcks.domain.OAuth2GrantType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Process OAuth2 authorization flow and tries to authorize from a client context. This is a dependency-free
 * implementation of the token endpoint calls (RFC 6749 sections 4.3, 4.4 and 6) using the JDK HTTP client.
 * @author laurent
 */
public class OAuth2AuthorizedClientProvider {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(OAuth2AuthorizedClientProvider.class);

   private static final ObjectMapper MAPPER = new ObjectMapper();

   /**
    * Realize OAuth2 authorization trying to get an access token from the given OAuth2 client context. Token as well as
    * traceability information are then transferred as a result in the OAuth2AuthorizedClient
    * @param oAuth2ClientContext The client context for OAuth2 authorization flow
    * @return An authorized client with traceability elements being principal and access token.
    * @throws AuthorizationException if the token endpoint call fails
    */
   public OAuth2AuthorizedClient authorize(OAuth2ClientContext oAuth2ClientContext) throws AuthorizationException {
      Map<String, String> form = new LinkedHashMap<>();
      switch (oAuth2ClientContext.getGrantType()) {
         case PASSWORD -> {
            form.put("grant_type", "password");
            form.put("username", oAuth2ClientContext.getUsername());
            form.put("password", oAuth2ClientContext.getPassword());
         }
         case CLIENT_CREDENTIALS -> form.put("grant_type", "client_credentials");
         case REFRESH_TOKEN -> {
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", oAuth2ClientContext.getRefreshToken());
         }
         default -> throw new AuthorizationException("Unsupported grant type " + oAuth2ClientContext.getGrantType());
      }
      form.put("scope",
            oAuth2ClientContext.getScopes() != null ? oAuth2ClientContext.getScopes() + " openid" : "openid");

      JsonNode token = callTokenEndpoint(oAuth2ClientContext, form);
      if (token == null || !token.hasNonNull("access_token")) {
         throw new AuthorizationException("No access_token in response of " + oAuth2ClientContext.getTokenUri());
      }

      String principalName = oAuth2ClientContext.getClientId();
      if (oAuth2ClientContext.getGrantType() == OAuth2GrantType.PASSWORD) {
         principalName = oAuth2ClientContext.getUsername();
      }
      String scopes = token.path("scope").asText(form.get("scope"));
      return new OAuth2AuthorizedClient(oAuth2ClientContext.getGrantType(), principalName,
            oAuth2ClientContext.getTokenUri(), scopes, token.get("access_token").asText());
   }

   private JsonNode callTokenEndpoint(OAuth2ClientContext context, Map<String, String> form)
         throws AuthorizationException {
      String body = form.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                  + URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));

      String credentials = URLEncoder.encode(context.getClientId(), StandardCharsets.UTF_8) + ":" + URLEncoder
            .encode(context.getClientSecret() == null ? "" : context.getClientSecret(), StandardCharsets.UTF_8);
      try {
         HttpRequest request = HttpRequest.newBuilder(URI.create(context.getTokenUri())).timeout(Duration.ofSeconds(30))
               .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
               .header("Authorization",
                     "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
               .POST(HttpRequest.BodyPublishers.ofString(body)).build();
         HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
         if (response.statusCode() / 100 != 2) {
            log.error("Error during {} grant type fetching: HTTP {}", context.getGrantType(), response.statusCode());
            throw new AuthorizationException(
                  "Error during " + context.getGrantType() + " grant type fetching: HTTP " + response.statusCode());
         }
         return MAPPER.readTree(response.body());
      } catch (IOException | IllegalArgumentException e) {
         throw new AuthorizationException("Error during " + context.getGrantType() + " grant type fetching", e);
      } catch (InterruptedException ie) {
         Thread.currentThread().interrupt();
         throw new AuthorizationException("Interrupted during " + context.getGrantType() + " grant type fetching", ie);
      }
   }
}
