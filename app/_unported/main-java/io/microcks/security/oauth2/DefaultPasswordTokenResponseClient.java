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
 *
 * Vendored equivalent of Spring Security 6.5
 * (org.springframework.security.oauth2.client.endpoint.DefaultPasswordTokenResponseClient).
 * Spring Security 7 removed Password Grant support per OAuth 2.1, including the
 * {@code AbstractOAuth2AuthorizationGrantRequestEntityConverter} machinery the original
 * relied on. This implementation issues the token request directly via {@link RestClient}
 * to avoid pulling in the removed converter abstractions.
 */
package io.github.microcks.security.oauth2;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Default {@link OAuth2AccessTokenResponseClient} for the Resource Owner Password Credentials grant. Sends a Basic-auth
 * form-encoded POST to the configured token endpoint and parses the JSON access-token response.
 * <p>
 * Vendored from Spring Security 6.5. Scheduled for removal in Microcks 1.16.0.
 * @deprecated RFC 9700 disallows the Resource Owner Password Credentials grant.
 */
@Deprecated(since = "1.15.0", forRemoval = true)
public final class DefaultPasswordTokenResponseClient
      implements OAuth2AccessTokenResponseClient<OAuth2PasswordGrantRequest> {

   private static final String INVALID_TOKEN_RESPONSE_ERROR_CODE = "invalid_token_response";

   @Override
   public OAuth2AccessTokenResponse getTokenResponse(OAuth2PasswordGrantRequest passwordGrantRequest) {
      Assert.notNull(passwordGrantRequest, "passwordGrantRequest cannot be null");
      ClientRegistration registration = passwordGrantRequest.getClientRegistration();

      String credentials = Base64.getEncoder().encodeToString(
            (registration.getClientId() + ":" + registration.getClientSecret()).getBytes(StandardCharsets.UTF_8));

      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("grant_type", "password");
      form.add("username", passwordGrantRequest.getUsername());
      form.add("password", passwordGrantRequest.getPassword());
      if (registration.getScopes() != null && !registration.getScopes().isEmpty()) {
         form.add("scope", String.join(" ", registration.getScopes()));
      }

      try {
         Map<String, Object> response = RestClient.create().post().uri(registration.getProviderDetails().getTokenUri())
               .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
               .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
               .body(new ParameterizedTypeReference<>() {
               });

         if (response == null || response.get("access_token") == null) {
            throw new OAuth2AuthorizationException(
                  new OAuth2Error(INVALID_TOKEN_RESPONSE_ERROR_CODE, "Token endpoint returned no access_token", null));
         }

         String accessToken = response.get("access_token").toString();
         long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 3600L;
         Set<String> scopes = response.get("scope") instanceof String s ? new HashSet<>(Arrays.asList(s.split(" ")))
               : Set.of();

         OAuth2AccessTokenResponse.Builder builder = OAuth2AccessTokenResponse.withToken(accessToken)
               .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(expiresIn).scopes(scopes);
         if (response.get("refresh_token") instanceof String rt) {
            builder.refreshToken(rt);
         }
         return builder.build();
      } catch (RestClientException ex) {
         throw new OAuth2AuthorizationException(new OAuth2Error(INVALID_TOKEN_RESPONSE_ERROR_CODE,
               "An error occurred while attempting to retrieve the OAuth 2.0 Access Token Response: " + ex.getMessage(),
               null), ex);
      }
   }
}
