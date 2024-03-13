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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * A JWT converter responsible for retrieving microcks-app specific granted authorities.
 * @author laurent
 */
public class MicrocksJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(MicrocksJwtConverter.class);

   @Override
   public JwtAuthenticationToken convert(Jwt jwt) {
      Map<String, Map<String, Collection<String>>> resourceAccess = jwt
            .getClaim(KeycloakJwtToken.RESOURCE_ACCESS_TOKEN_CLAIM);

      if (resourceAccess != null) {
         Map<String, Collection<String>> microcksResource = resourceAccess.get(KeycloakJwtToken.MICROCKS_APP_RESOURCE);

         if (microcksResource != null) {
            Collection<String> roles = microcksResource.get("roles");
            log.trace("JWT extracted roles for microcks-app: {}", roles);

            var grantedAuthorities = roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
            return new JwtAuthenticationToken(jwt, grantedAuthorities);
         }
      }
      return new JwtAuthenticationToken(jwt, Collections.emptyList());
   }
}
