/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.domain;

import org.springframework.data.annotation.Transient;

/**
 * Represent persisted information for an OAuth2 authorization/authentication done before launching a test.
 * @author laurent
 */
public class OAuth2AuthorizedClient {

   private OAuth2GrantType grantType;
   private String principalName;
   private String tokenUri;
   @Transient
   private String encodedAccessToken;

   public OAuth2AuthorizedClient() {
   }

   /**
    * Build an OAuth2AuthorizedClient from required information including the volatile encodedAccessToken
    * @param grantType OAuth2 authorization flow/grant type applied.
    * @param principalName Name of authorized principal
    * @param tokenUri IDP URI used for token retrieval
    * @param encodedAccessToken THe volatile access token, encoded in base64
    */
   public OAuth2AuthorizedClient(OAuth2GrantType grantType, String principalName, String tokenUri, String encodedAccessToken) {
      this.grantType = grantType;
      this.principalName = principalName;
      this.tokenUri = tokenUri;
      this.encodedAccessToken = encodedAccessToken;
   }

   public OAuth2GrantType getGrantType() {
      return grantType;
   }

   public void setGrantType(OAuth2GrantType grantType) {
      this.grantType = grantType;
   }

   public String getPrincipalName() {
      return principalName;
   }

   public void setPrincipalName(String principalName) {
      this.principalName = principalName;
   }

   public String getTokenUri() {
      return tokenUri;
   }

   public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
   }

   public String getEncodedAccessToken() {
      return encodedAccessToken;
   }
}
