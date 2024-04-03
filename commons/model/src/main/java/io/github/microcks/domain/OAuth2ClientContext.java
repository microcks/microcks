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
package io.github.microcks.domain;

import java.io.Serializable;

/**
 * Represents a volatile OAuth2 client context usually associated with a Test request.
 * @author laurent
 */
public class OAuth2ClientContext implements Serializable {

   private String clientId;
   private String clientSecret;
   private String tokenUri;
   private String scopes;
   private String username;
   private String password;
   private String refreshToken;
   private OAuth2GrantType grantType;

   public String getClientId() {
      return clientId;
   }

   public void setClientId(String clientId) {
      this.clientId = clientId;
   }

   public String getClientSecret() {
      return clientSecret;
   }

   public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
   }

   public String getTokenUri() {
      return tokenUri;
   }

   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   public String getScopes() {
      return scopes;
   }

   public void setScopes(String scopes) {
      this.scopes = scopes;
   }

   public String getUsername() {
      return username;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public String getPassword() {
      return password;
   }

   public void setPassword(String password) {
      this.password = password;
   }

   public String getRefreshToken() {
      return refreshToken;
   }

   public void setRefreshToken(String refreshToken) {
      this.refreshToken = refreshToken;
   }

   public OAuth2GrantType getGrantType() {
      return grantType;
   }

   public void setGrantType(OAuth2GrantType grantType) {
      this.grantType = grantType;
   }
}
