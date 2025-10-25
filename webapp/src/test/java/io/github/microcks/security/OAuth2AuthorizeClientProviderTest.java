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

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.endpoint.DefaultPasswordTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2PasswordGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for OAuth2AuthorizeClientProvider class.
 * @author laurent
 */
@Testcontainers
public class OAuth2AuthorizeClientProviderTest {

   @Container
   public static KeycloakContainer keycloak = new KeycloakContainer("quay.io/keycloak/keycloak:26.0.0")
         .withRealmImportFile("io/github/microcks/security/myrealm-realm.json");

   @Test
   void testResourceOwnerPassword() {
      OAuth2AuthorizedClientProvider provider = new OAuth2AuthorizedClientProvider();

      OAuth2ClientContext clientContext = new OAuth2ClientContext();
      clientContext.setClientId("myrealm-test");
      clientContext.setClientSecret("QAzrPEJJeDkjKtePKoXyzkqY6exkBauh");
      clientContext.setTokenUri(keycloak.getAuthServerUrl() + "/realms/myrealm/protocol/openid-connect/token");
      clientContext.setGrantType(OAuth2GrantType.PASSWORD);
      clientContext.setUsername("admin");
      clientContext.setPassword("myrealm123");

      OAuth2AuthorizedClient authorizedClient = null;
      try {
         authorizedClient = provider.authorize(clientContext);
      } catch (AuthorizationException authException) {
         fail("No AuthorizationException should be thrown");
      }

      assertEquals("admin", authorizedClient.getPrincipalName());
      assertEquals(keycloak.getAuthServerUrl() + "/realms/myrealm/protocol/openid-connect/token",
            authorizedClient.getTokenUri());
      assertEquals(OAuth2GrantType.PASSWORD, authorizedClient.getGrantType());
      assertNotNull(authorizedClient.getEncodedAccessToken());
   }

   @Test
   void testClientCredentials() {
      OAuth2AuthorizedClientProvider provider = new OAuth2AuthorizedClientProvider();

      OAuth2ClientContext clientContext = new OAuth2ClientContext();
      clientContext.setClientId("myrealm-serviceaccount");
      clientContext.setClientSecret("ab54d329-e435-41ae-a900-ec6b3fe15c54");
      clientContext.setTokenUri(keycloak.getAuthServerUrl() + "/realms/myrealm/protocol/openid-connect/token");
      clientContext.setGrantType(OAuth2GrantType.CLIENT_CREDENTIALS);

      OAuth2AuthorizedClient authorizedClient = null;
      try {
         authorizedClient = provider.authorize(clientContext);
      } catch (AuthorizationException authException) {
         fail("No AuthorizationException should be thrown");
      }

      assertEquals("myrealm-serviceaccount", authorizedClient.getPrincipalName());
      assertEquals(keycloak.getAuthServerUrl() + "/realms/myrealm/protocol/openid-connect/token",
            authorizedClient.getTokenUri());
      assertEquals(OAuth2GrantType.CLIENT_CREDENTIALS, authorizedClient.getGrantType());
      assertNotNull(authorizedClient.getEncodedAccessToken());
   }

   @Test
   void testClientCredentialsFailure() throws AuthorizationException {
      assertThrows(AuthorizationException.class, () -> {
         OAuth2AuthorizedClientProvider provider = new OAuth2AuthorizedClientProvider();

         OAuth2ClientContext clientContext = new OAuth2ClientContext();
         clientContext.setClientId("myrealm-serviceaccount");
         clientContext.setClientSecret("ab54d329-e435-41ae-a900-ec6b3fe15c55");
         clientContext.setTokenUri(keycloak.getAuthServerUrl() + "/realms/myrealm/protocol/openid-connect/token");
         clientContext.setGrantType(OAuth2GrantType.CLIENT_CREDENTIALS);

         OAuth2AuthorizedClient authorizedClient = provider.authorize(clientContext);
      });
   }

   @Test
   void testRefreshToken() {
      // First use standard Spring Security classes to authorize and retrieve a refresh token.
      DefaultPasswordTokenResponseClient client = new DefaultPasswordTokenResponseClient();

      ClientRegistration registration = ClientRegistration.withRegistrationId("test-idp").clientId("myrealm-test")
            .clientSecret("QAzrPEJJeDkjKtePKoXyzkqY6exkBauh")
            .tokenUri(keycloak.getAuthServerUrl() + "/realms/myrealm/protocol/openid-connect/token")
            .authorizationGrantType(AuthorizationGrantType.PASSWORD).scope("openid").build();

      OAuth2PasswordGrantRequest request = new OAuth2PasswordGrantRequest(registration, "admin", "myrealm123");
      OAuth2AccessTokenResponse response = client.getTokenResponse(request);

      // Then test our Autho provider with this refresh token.
      OAuth2AuthorizedClientProvider provider = new OAuth2AuthorizedClientProvider();

      OAuth2ClientContext clientContext = new OAuth2ClientContext();
      clientContext.setClientId("myrealm-test");
      clientContext.setClientSecret("QAzrPEJJeDkjKtePKoXyzkqY6exkBauh");
      clientContext.setTokenUri(keycloak.getAuthServerUrl() + "/realms/myrealm/protocol/openid-connect/token");
      clientContext.setGrantType(OAuth2GrantType.REFRESH_TOKEN);
      clientContext.setRefreshToken(response.getRefreshToken().getTokenValue());

      OAuth2AuthorizedClient authorizedClient = null;
      try {
         authorizedClient = provider.authorize(clientContext);
      } catch (AuthorizationException authException) {
         fail("No AuthorizationException should be thrown");
      }

      assertEquals("myrealm-test", authorizedClient.getPrincipalName());
      assertEquals(keycloak.getAuthServerUrl() + "/realms/myrealm/protocol/openid-connect/token",
            authorizedClient.getTokenUri());
      assertEquals(OAuth2GrantType.REFRESH_TOKEN, authorizedClient.getGrantType());
      assertNotNull(authorizedClient.getEncodedAccessToken());
   }
}
