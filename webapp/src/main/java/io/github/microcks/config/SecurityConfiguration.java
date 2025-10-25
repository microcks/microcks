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
package io.github.microcks.config;

import io.github.microcks.security.MicrocksJwtConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static io.github.microcks.security.AuthorizationChecker.*;

/**
 * A bean responsible for Security filter chain configuration using Spring Security ODIC adapters.
 * @author laurent
 */
@Configuration
@EnableWebSecurity
@EnableAutoConfiguration(exclude = { OAuth2ClientAutoConfiguration.class })
public class SecurityConfiguration {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

   @Value("${keycloak.enabled}")
   private final Boolean keycloakEnabled = true;

   @Bean
   public SecurityFilterChain configureMockSecurityFilterChain(HttpSecurity http) throws Exception {
      log.info("Starting security configuration for mocks");

      // State-less session (state in access-token only)
      http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

      // Disable CSRF because of state-less session-management
      http.csrf(csrf -> csrf.disable());

      // Disable CORS as we already have a filter in WebConfiguration that does the job
      http.cors(cors -> cors.disable());

      http.securityMatcher("/rest/**", "/rest-valid/**", "/graphql/**", "/soap/**")
            .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

      // Disable the publication of X-Frame-Options to allow embedding the UI.
      // See https://github.com/microcks/microcks/issues/952
      http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

      return http.build();
   }

   @Bean
   public SecurityFilterChain configureAPISecurityFilterChain(HttpSecurity http) throws Exception {
      log.info("Starting security configuration for APIs");

      // State-less session (state in access-token only)
      http.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

      // Disable CSRF because of state-less session-management
      http.csrf(csrf -> csrf.disable());

      // Disable CORS as we already have a filter in WebConfiguration that does the job
      http.cors(cors -> cors.disable());
      if (Boolean.TRUE.equals(keycloakEnabled)) {
         log.info("Keycloak is enabled, configuring oauth2 & request authorization");

         // spotless:off
         http.authorizeHttpRequests(registry -> registry
               .requestMatchers(HttpMethod.GET, "/api/services/*").hasAnyRole(ROLE_USER)
               .requestMatchers("/api/services", "/api/jobs", "/api/jobs/*").hasAnyRole(ROLE_USER, ROLE_MANAGER, ROLE_ADMIN)
               .requestMatchers("/api/services/*").hasAnyRole(ROLE_MANAGER, ROLE_ADMIN)
               .requestMatchers("/api/services/*/*").hasAnyRole(ROLE_MANAGER, ROLE_ADMIN)
               .requestMatchers("/api/jobs/*/*").hasAnyRole(ROLE_MANAGER, ROLE_ADMIN)
               .requestMatchers("/api/artifact/*").hasAnyRole(ROLE_MANAGER, ROLE_ADMIN)
               .requestMatchers("/api/import", "/api/export").hasAnyRole(ROLE_ADMIN)
               .requestMatchers(HttpMethod.GET, "/api/secrets").hasAnyRole(ROLE_USER, ROLE_MANAGER, ROLE_ADMIN)
               .requestMatchers(HttpMethod.GET, "/api/secrets/*").hasAnyRole(ROLE_USER, ROLE_MANAGER, ROLE_ADMIN)
               .requestMatchers(HttpMethod.POST, "/api/secrets").hasAnyRole(ROLE_ADMIN)
               .requestMatchers(HttpMethod.PUT, "/api/secrets/*").hasAnyRole(ROLE_ADMIN)
               .requestMatchers(HttpMethod.DELETE, "/api/secrets/*").hasAnyRole(ROLE_ADMIN).anyRequest().permitAll());
         // spotless:on

         http.oauth2ResourceServer(oauth2Configurer -> oauth2Configurer
               .jwt(jwtConfigurer -> jwtConfigurer.jwtAuthenticationConverter(new MicrocksJwtConverter())));
      } else {
         log.info("Keycloak is disabled, permitting all requests");
         http.authorizeHttpRequests(registry -> registry.anyRequest().permitAll());
      }

      // Disable the publication of X-Frame-Options to allow embedding the UI.
      // See https://github.com/microcks/microcks/issues/952
      http.headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

      return http.build();
   }
}
