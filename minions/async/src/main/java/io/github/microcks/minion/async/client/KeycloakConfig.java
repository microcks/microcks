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
package io.github.microcks.minion.async.client;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Simple bean representing the Keycloak configuration returned by the Microcks API config endpoint.
 * @author laurent
 */
public class KeycloakConfig {

   private boolean enabled = true;

   private String realm;

   @JsonProperty("auth-server-url")
   private String authServerUrl;

   @JsonProperty("ssl-required")
   private String sslRequired;

   @JsonProperty("public-client")
   private boolean publicClient;

   private String resource;


   public boolean isEnabled() {
      return enabled;
   }

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public String getRealm() {
      return realm;
   }

   public void setRealm(String realm) {
      this.realm = realm;
   }

   public String getAuthServerUrl() {
      return authServerUrl;
   }

   public void setAuthServerUrl(String authServerUrl) {
      this.authServerUrl = authServerUrl;
   }

   public String getSslRequired() {
      return sslRequired;
   }

   public void setSslRequired(String sslRequired) {
      this.sslRequired = sslRequired;
   }

   public boolean isPublicClient() {
      return publicClient;
   }

   public void setPublicClient(boolean publicClient) {
      this.publicClient = publicClient;
   }

   public String getResource() {
      return resource;
   }

   public void setResource(String resource) {
      this.resource = resource;
   }
}
