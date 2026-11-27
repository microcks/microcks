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
 * Vendored from Spring Security 6.5
 * (org.springframework.security.oauth2.client.endpoint.OAuth2PasswordGrantRequest).
 * Spring Security 7 removed Password Grant support per OAuth 2.1.
 * Original copyright belongs to the Spring Security project authors.
 */
package io.github.microcks.security.oauth2;

import org.springframework.security.oauth2.client.endpoint.AbstractOAuth2AuthorizationGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.util.Assert;

/**
 * An OAuth 2.0 Resource Owner Password Credentials Grant request holding the resource owner's credentials.
 * <p>
 * Vendored from Spring Security 6.5 since Spring Security 7 dropped Password Grant per OAuth 2.1. Microcks retains it
 * for legacy IDPs in test contexts only. Scheduled for removal in Microcks 1.16.0.
 * @deprecated RFC 9700 disallows the Resource Owner Password Credentials grant. Migrate test-time IDP integrations to
 *             Authorization Code or Client Credentials.
 */
@Deprecated(since = "1.15.0", forRemoval = true)
public class OAuth2PasswordGrantRequest extends AbstractOAuth2AuthorizationGrantRequest {

   /** Replacement for the AuthorizationGrantType.PASSWORD constant removed in Spring Security 7. */
   public static final AuthorizationGrantType PASSWORD = new AuthorizationGrantType("password");

   private final String username;
   private final String password;

   /**
    * Constructs an {@code OAuth2PasswordGrantRequest} using the provided parameters.
    * @param clientRegistration the client registration
    * @param username           the resource owner's username
    * @param password           the resource owner's password
    */
   public OAuth2PasswordGrantRequest(ClientRegistration clientRegistration, String username, String password) {
      super(PASSWORD, clientRegistration);
      Assert.isTrue(PASSWORD.equals(clientRegistration.getAuthorizationGrantType()),
            "clientRegistration.authorizationGrantType must be AuthorizationGrantType.PASSWORD");
      Assert.hasText(username, "username cannot be empty");
      Assert.hasText(password, "password cannot be empty");
      this.username = username;
      this.password = password;
   }

   public String getUsername() {
      return this.username;
   }

   public String getPassword() {
      return this.password;
   }
}
