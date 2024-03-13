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
import org.springframework.security.oauth2.client.endpoint.DefaultClientCredentialsTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultPasswordTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.DefaultRefreshTokenTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2ClientCredentialsGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2PasswordGrantRequest;
import org.springframework.security.oauth2.client.endpoint.OAuth2RefreshTokenGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;

import java.util.Arrays;

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

   private OAuth2AccessToken getResourceOwnerPasswordAccessToken(OAuth2ClientContext oAuth2ClientContext) {
      // Build a ClientRegistration with PASSWORD grant type.
      ClientRegistration registration = initializeClientRegistration(oAuth2ClientContext)
            .authorizationGrantType(AuthorizationGrantType.PASSWORD).build();

      DefaultPasswordTokenResponseClient client = new DefaultPasswordTokenResponseClient();
      OAuth2PasswordGrantRequest request = new OAuth2PasswordGrantRequest(registration,
            oAuth2ClientContext.getUsername(), oAuth2ClientContext.getPassword());
      OAuth2AccessTokenResponse response = client.getTokenResponse(request);

      return response.getAccessToken();
   }

   private OAuth2AccessToken getClientCredentialsAccessToken(OAuth2ClientContext oAuth2ClientContext) {
      // Build a ClientRegistration with CLIENT_CREDENTIALS grant type.
      ClientRegistration registration = initializeClientRegistration(oAuth2ClientContext)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS).build();

      DefaultClientCredentialsTokenResponseClient client = new DefaultClientCredentialsTokenResponseClient();
      OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(registration);
      OAuth2AccessTokenResponse response = client.getTokenResponse(request);

      return response.getAccessToken();
   }

   private OAuth2AccessToken getRefreshTokenAccessToken(OAuth2ClientContext oAuth2ClientContext) {
      // Build a ClientRegistration with REFRESH_TOKEN grant type.
      ClientRegistration registration = initializeClientRegistration(oAuth2ClientContext)
            .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN).build();

      DefaultRefreshTokenTokenResponseClient clientRT = new DefaultRefreshTokenTokenResponseClient();
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
