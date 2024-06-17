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
package io.github.microcks.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Rest controller for dispatching Keycloak configuration to frontend.
 * @author laurent
 */
@RestController
@RequestMapping("/api/keycloak")
public class KeycloakConfigController {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(KeycloakConfigController.class);

   @Value("${keycloak.enabled}")
   private Boolean keycloakEnabled = true;

   @Value("${sso.public-url}")
   private String keycloakServerUrl = null;

   @Value("${keycloak.realm}")
   private String keycloakRealmName = null;

   @GetMapping(value = "/config")
   public ResponseEntity<Config> getConfig() {
      final Config config = new Config(keycloakEnabled, keycloakRealmName, keycloakServerUrl);

      log.debug("Returning '{}' realm config, for {}", keycloakRealmName, keycloakServerUrl);

      return new ResponseEntity<>(config, HttpStatus.OK);
   }


   private class Config {

      private boolean enabled = true;

      private String realm = "microcks";

      @JsonProperty("auth-server-url")
      private String authServerUrl = "http://localhost:8180/auth";

      @JsonProperty("ssl-required")
      private String sslRequired = "external";

      @JsonProperty("public-client")
      private boolean publicClient = true;

      private String resource = "microcks-app-js";

      public Config(boolean enabled, String realmName, String authServerUrl) {
         this.enabled = enabled;
         if (realmName != null && !realm.isEmpty()) {
            this.realm = realmName;
         }
         if (authServerUrl != null && !authServerUrl.isEmpty()) {
            this.authServerUrl = authServerUrl;
         }
      }

      public boolean isEnabled() {
         return enabled;
      }

      public String getRealm() {
         return realm;
      }

      public String getAuthServerUrl() {
         return authServerUrl;
      }

      public String getSslRequired() {
         return sslRequired;
      }

      public boolean isPublicClient() {
         return publicClient;
      }

      public String getResource() {
         return resource;
      }
   }
}
