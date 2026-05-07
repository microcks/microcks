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
package io.github.microcks.security;

import io.github.microcks.domain.OAuth2AuthorizedClient;
import io.github.microcks.domain.OAuth2ClientContext;
import io.github.microcks.domain.OAuth2GrantType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.RestClientRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Process OAuth2 authorization flow and tries to authorize from a client context.
 * @author laurent
 */
public class OAuth2AuthorizedClientProvider {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(OAuth2AuthorizedClientProvider.class);

   /**
    * Realize OAuth2 authorization trying to get an access token from the given OAuth2 client context. Token as well as
    * traceability information are then transferred as a result in the OAuth2AuthorizedClient
    * @param oAuth2ClientContext The client context for OAuth2 authorization flow
    * @return An authorized client with traceability elements being principal and access token.
    * @throws AuthorizationException
    */
   public OAuth2AuthorizedClient authorize(OAuth2ClientContext oAuth2ClientContext) throws AuthorizationException {

      OAuth2AccessToken accessToken = null;
      try {
         switch (oAuth2ClientContext.getGrantType()) {
            case PASSWORD -> accessToken = getResourceOwnerPasswordAccessToken(oAuth2ClientContext);
            case CLIENT_CREDENTIALS -> accessToken = getClientCredentialsAccessToken(oAuth2ClientContext);
            case REFRESH_TOKEN -> accessToken = getRefreshTokenAccessToken(oAuth2ClientContext);
         }
      } catch (OAuth2AuthorizationException oAuth2AuthorizationException) {
         log.error("Error during {} grant type fetching", oAuth2ClientContext.getGrantType(),
               oAuth2AuthorizationException);
         throw new AuthorizationException("Error during " + oAuth2ClientContext.getGrantType() + " grant type fetching",
               oAuth2AuthorizationException);
      }

      String principalName = oAuth2ClientContext.getClientId();
      if (oAuth2ClientContext.getGrantType() == OAuth2GrantType.PASSWORD) {
         principalName = oAuth2ClientContext.getUsername();
      }

      return new OAuth2AuthorizedClient(oAuth2ClientContext.getGrantType(), principalName,
            oAuth2ClientContext.getTokenUri(), String.join(" ", accessToken.getScopes()), accessToken.getTokenValue());
   }

   /**
    * Spring Security 7 removed the Resource Owner Password Credentials grant per OAuth 2.1. Microcks keeps it for
    * legacy IDPs in test contexts only by issuing the token request manually against the configured endpoint.
    */
   private OAuth2AccessToken getResourceOwnerPasswordAccessToken(OAuth2ClientContext oAuth2ClientContext) {
      String credentials = Base64.getEncoder()
            .encodeToString((oAuth2ClientContext.getClientId() + ":" + oAuth2ClientContext.getClientSecret())
                  .getBytes(StandardCharsets.UTF_8));

      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      // The PASSWORD/USERNAME constants were dropped alongside the Password Grant in Spring Security 7;
      // the parameter names themselves are still part of the OAuth 2.0 spec.
      form.add(OAuth2ParameterNames.GRANT_TYPE, "password");
      form.add("username", oAuth2ClientContext.getUsername());
      form.add("password", oAuth2ClientContext.getPassword());
      String scope = oAuth2ClientContext.getScopes() != null ? oAuth2ClientContext.getScopes() + " openid" : "openid";
      form.add(OAuth2ParameterNames.SCOPE, scope);

      Map<String, Object> response = RestClient.create().post().uri(oAuth2ClientContext.getTokenUri())
            .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve()
            .body(new ParameterizedTypeReference<>() {
            });

      if (response == null || response.get(OAuth2ParameterNames.ACCESS_TOKEN) == null) {
         throw new OAuth2AuthorizationException(
               new OAuth2Error("invalid_token_response", "Token endpoint returned no access_token", null));
      }

      String tokenValue = response.get(OAuth2ParameterNames.ACCESS_TOKEN).toString();
      long expiresIn = response.get("expires_in") instanceof Number n ? n.longValue() : 3600L;
      Set<String> scopes = response.get(OAuth2ParameterNames.SCOPE) instanceof String s ? Set.of(s.split(" "))
            : Set.of();
      Instant now = Instant.now();
      return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, tokenValue, now, now.plusSeconds(expiresIn),
            scopes);
   }

   private OAuth2AccessToken getClientCredentialsAccessToken(OAuth2ClientContext oAuth2ClientContext) {
      // Build a ClientRegistration with CLIENT_CREDENTIALS grant type.
      ClientRegistration registration = initializeClientRegistration(oAuth2ClientContext)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build();

      RestClientClientCredentialsTokenResponseClient client = new RestClientClientCredentialsTokenResponseClient();
      OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(registration);
      OAuth2AccessTokenResponse response = client.getTokenResponse(request);

      return response.getAccessToken();
   }

   private OAuth2AccessToken getRefreshTokenAccessToken(OAuth2ClientContext oAuth2ClientContext) {
      // Build a ClientRegistration with REFRESH_TOKEN grant type.
      ClientRegistration registration = initializeClientRegistration(oAuth2ClientContext)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).build();

      RestClientRefreshTokenTokenResponseClient clientRT = new RestClientRefreshTokenTokenResponseClient();
      OAuth2RefreshTokenGrantRequest requestRT = new OAuth2RefreshTokenGrantRequest(registration,
            new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "fake-one", null, null),
            new OAuth2RefreshToken(oAuth2ClientContext.getRefreshToken(), null));
      OAuth2AccessTokenResponse response = clientRT.getTokenResponse(requestRT);

      return response.getAccessToken();
   }

   private ClientRegistration.Builder initializeClientRegistration(OAuth2ClientContext oAuth2ClientContext) {
      ClientRegistration.Builder builder = ClientRegistration.withRegistrationId("microcks-test-idp")
            .clientId(oAuth2ClientContext.getClientId()).clientSecret(oAuth2ClientContext.getClientSecret())
            .tokenUri(oAuth2ClientContext.getTokenUri());

      if (oAuth2ClientContext.getScopes() != null) {
         String[] scopes = (oAuth2ClientContext.getScopes() + " openid").split(" ");
         builder.scope(Arrays.asList(scopes));
      } else {
         builder.scope("openid");
      }

      return builder;
   }
}
