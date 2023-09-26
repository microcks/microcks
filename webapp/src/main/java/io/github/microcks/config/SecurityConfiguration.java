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
package io.github.microcks.config;

import io.github.microcks.security.MicrocksJwtConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A bean responsible for Security filter chain configuration using Spring Security ODIC adapters.
 * @author laurent
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

   @Value("${keycloak.enabled}")
   private final Boolean keycloakEnabled = true;

   @Bean
   public SecurityFilterChain configureSecurityFilterChain(HttpSecurity http) throws Exception {
      log.info("Starting security configuration");

      // State-less session (state in access-token only)
      http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

      // Disable CSRF because of state-less session-management
      http.csrf(csrf -> csrf.disable());

      if (keycloakEnabled) {
         log.info("Keycloak is enabled, configuring oauth2 & request authorization");

         http.authorizeHttpRequests(registry -> registry
               .requestMatchers("/api/services", "/api/services/*", "/api/jobs", "/api/jobs/*").hasAnyRole("user", "manager", "admin")
               .requestMatchers("/api/services/*/*").hasAnyRole("manager", "admin")
               .requestMatchers("/api/jobs/*/*").hasAnyRole("manager", "admin")
               .requestMatchers("/api/artifact/*").hasAnyRole("manager", "admin")
               .requestMatchers("/api/import", "/api/export").hasAnyRole("admin")
               .anyRequest().permitAll()
         );

         http.oauth2ResourceServer(oauth2Configurer ->
               oauth2Configurer.jwt(jwtConfigurer ->
                     jwtConfigurer.jwtAuthenticationConverter(new MicrocksJwtConverter())));
      } else {
         log.info("Keycloak is disabled, permitting all requests");
         http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
      }

      return http.build();
   }
}
