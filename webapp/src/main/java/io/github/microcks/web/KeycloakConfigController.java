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
package io.github.microcks.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Rest controller for dispatching Keycloak configuration to frontend.
 * @author laurent
 */
@RestController
@RequestMapping("/api/keycloak")
public class KeycloakConfigController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(KeycloakConfigController.class);


   @Value("${sso.public-url}")
   private final String keycloakServerUrl = null;

   @Value("${keycloak.realm}")
   private final String keycloakRealmName = null;

   @RequestMapping(value = "/config", method = RequestMethod.GET)
   public ResponseEntity<?> getConfig() {
      final Config config = new Config(keycloakRealmName, keycloakServerUrl);

      log.debug("Returning '{}' realm config, for {}", keycloakRealmName, keycloakServerUrl);

      return new ResponseEntity<>(config, HttpStatus.OK);
   }


   private class Config{

      private String realm = "microcks";

      @JsonProperty("auth-server-url")
      private String authServerUrl = "http://localhost:8180/auth";

      @JsonProperty("ssl-required")
      private final String sslRequired = "external";

      @JsonProperty("public-client")
      private final boolean publicClient = true;

      private final String resource = "microcks-app-js";


      public Config(String realmName, String authServerUrl) {
         if (realmName != null && !realm.isEmpty()) {
            this.realm = realmName;
         }
         if (authServerUrl != null && !authServerUrl.isEmpty()) {
            this.authServerUrl = authServerUrl;
         }
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
