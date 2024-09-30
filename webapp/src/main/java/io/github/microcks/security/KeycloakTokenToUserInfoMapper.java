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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static io.github.microcks.security.KeycloakJwtToken.MICROCKS_GROUPS_TOKEN_CLAIM;
import static org.springframework.security.oauth2.core.oidc.StandardClaimNames.*;

/**
 * Simpler mapper for transforming KeycloakSecurityContext token into UserInfo bean.
 * @author laurent
 */
public class KeycloakTokenToUserInfoMapper {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(KeycloakTokenToUserInfoMapper.class);

   private KeycloakTokenToUserInfoMapper() {
      // Hide implicit public constructor.
   }

   /**
    * Maps the information from Spring SecurityContext tokens into a UserInfo instance.
    * @param context The current security context provided by Spring Security server adapter.
    * @return A new UserInfo with info coming from Keycloak tokens.
    */
   public static UserInfo map(SecurityContext context) {
      Authentication authentication = context.getAuthentication();

      if (authentication instanceof JwtAuthenticationToken jwtToken) {
         Jwt jwt = jwtToken.getToken();

         String[] microcksGroups = new String[] {};
         if (jwt.hasClaim(MICROCKS_GROUPS_TOKEN_CLAIM)) {
            microcksGroups = jwt.getClaimAsStringList(MICROCKS_GROUPS_TOKEN_CLAIM).toArray(String[]::new);
         }

         // Create and return UserInfo.
         UserInfo userInfo = new UserInfo(jwt.getClaimAsString(NAME), jwt.getClaimAsString(PREFERRED_USERNAME),
               jwt.getClaimAsString(GIVEN_NAME), jwt.getClaimAsString(FAMILY_NAME), jwt.getClaimAsString(EMAIL),
               authentication.getAuthorities().stream()
                     .map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", ""))
                     .toArray(String[]::new),
               microcksGroups);
         log.debug("Current user is: {}", userInfo);
         return userInfo;
      }
      return null;
   }
}
