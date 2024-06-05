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

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is a test case for MicrocksJwtConverter class.
 * @author laurent
 */
class MicrocksJwtConverterTest {

   /**
    * { "alg": "RS256", "typ": "JWT", "kid": "bMWP9GYcT9PpSPKhkBWD9mOZeHq1YQKHq4JXTaxzd-o" } { "exp": 1695715998, "iat":
    * 1695715698, "auth_time": 1695715698, "jti": "dda2e10f-f908-45c9-81a7-088fc80d9b02", "iss":
    * "http://localhost:8180/realms/microcks", "aud": "microcks-app", "sub": "e9a5e235-31ac-4bf8-943d-76df95d548a3",
    * "typ": "Bearer", "azp": "microcks-app-js", "nonce": "30f09cc0-3cdb-4afc-85b4-36dd9fd498da", "session_state":
    * "e892de3b-7054-4967-bbef-677b62c32aa0", "acr": "1", "allowed-origins": [ "http://localhost:8080",
    * "http://localhost:4200" ], "resource_access": { "microcks-app": { "roles": [ "manager", "user" ] } }, "scope":
    * "openid profile email", "sid": "e892de3b-7054-4967-bbef-677b62c32aa0", "email_verified": false, "name": "Pastry
    * Manager", "microcks-groups": [ "/microcks/manager/pastry" ], "preferred_username": "pastry-manager", "given_name":
    * "Pastry", "family_name": "Manager" }
    */
   private static final String jwtBearer = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJiTVdQOUdZY1Q5UHBTUEtoa0JXRDltT1plSHExWVFLSHE0SlhUYXh6ZC1vIn0.eyJleHAiOjE2OTU3MTU5OTgsImlhdCI6MTY5NTcxNTY5OCwiYXV0aF90aW1lIjoxNjk1NzE1Njk4LCJqdGkiOiJkZGEyZTEwZi1mOTA4LTQ1YzktODFhNy0wODhmYzgwZDliMDIiLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjgxODAvcmVhbG1zL21pY3JvY2tzIiwiYXVkIjoibWljcm9ja3MtYXBwIiwic3ViIjoiZTlhNWUyMzUtMzFhYy00YmY4LTk0M2QtNzZkZjk1ZDU0OGEzIiwidHlwIjoiQmVhcmVyIiwiYXpwIjoibWljcm9ja3MtYXBwLWpzIiwibm9uY2UiOiIzMGYwOWNjMC0zY2RiLTRhZmMtODViNC0zNmRkOWZkNDk4ZGEiLCJzZXNzaW9uX3N0YXRlIjoiZTg5MmRlM2ItNzA1NC00OTY3LWJiZWYtNjc3YjYyYzMyYWEwIiwiYWNyIjoiMSIsImFsbG93ZWQtb3JpZ2lucyI6WyJodHRwOi8vbG9jYWxob3N0OjgwODAiLCJodHRwOi8vbG9jYWxob3N0OjQyMDAiXSwicmVzb3VyY2VfYWNjZXNzIjp7Im1pY3JvY2tzLWFwcCI6eyJyb2xlcyI6WyJtYW5hZ2VyIiwidXNlciJdfX0sInNjb3BlIjoib3BlbmlkIHByb2ZpbGUgZW1haWwiLCJzaWQiOiJlODkyZGUzYi03MDU0LTQ5NjctYmJlZi02NzdiNjJjMzJhYTAiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsIm5hbWUiOiJQYXN0cnkgTWFuYWdlciIsIm1pY3JvY2tzLWdyb3VwcyI6WyIvbWljcm9ja3MvbWFuYWdlci9wYXN0cnkiXSwicHJlZmVycmVkX3VzZXJuYW1lIjoicGFzdHJ5LW1hbmFnZXIiLCJnaXZlbl9uYW1lIjoiUGFzdHJ5IiwiZmFtaWx5X25hbWUiOiJNYW5hZ2VyIn0.Z1F3OjBJl3ko4O4BzpKZ2rcy086_vtNqrHFGw10MZpXmumAk1Yww_gf2yz1KhgjZkxNLxfr_1kEefK223Pi3yYCFboXXWbAtFCb2TztOw9RgZU9Fs1Z4mNCCAkwYVLYG2iQr-TlOje9JMYliptHtm5FRRqF-bfsd0tKWhJRezk_DCxdCTVQ_Hx9fFHY1if9-OiRcKYU7F5XU_yFSDP-P0j6KKqX2lpMuWKOKsfWfdZkoBm02JbSAiCKqLKG8R14d3D-cYkxGnil-QSXIsQqSK8DL7RLKxLKCKykkDunbCx2JBw9MvV1TDmSrEszMF1jj46DpYO036gJV7F0PhKePdg";

   private static final String DECODING_ERROR_MESSAGE_TEMPLATE = "An error occurred while attempting to decode the Jwt: %s";

   @Test
   void testConvert() {
      MicrocksJwtConverter converter = new MicrocksJwtConverter();
      Jwt jwt = null;

      try {
         JWT parsedJwt = JWTParser.parse(jwtBearer);
         jwt = createJwt(jwtBearer, parsedJwt);
      } catch (Exception e) {
         fail("Parsing Jwt bearer should not fail");
      }

      // Convert and assert granted authorities.
      JwtAuthenticationToken authenticationToken = converter.convert(jwt);

      assertTrue(authenticationToken.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_user")));
      assertTrue(authenticationToken.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_manager")));
   }

   public static Jwt createJwt(String token, JWT parsedJwt) {
      try {
         Map<String, Object> headers = new LinkedHashMap<>(parsedJwt.getHeader().toJSONObject());
         Map<String, Object> claims = parsedJwt.getJWTClaimsSet().getClaims();
         // See https://github.com/jhipster/generator-jhipster/issues/19309
         Map<String, Object> tempClaims = new LinkedHashMap<>();
         for (String key : claims.keySet()) {
            Object value = claims.get(key);
            if (key.equals("exp") || key.equals("iat")) {
               value = ((Date) value).toInstant();
            }
            tempClaims.put(key, value);
         }
         return Jwt.withTokenValue(token).headers(h -> h.putAll(headers)).claims(c -> c.putAll(tempClaims)).build();
      } catch (Exception ex) {
         throw new JwtException(
               String.format("An error occurred while attempting to decode the Jwt: %s", "Malformed payload"));
      }
   }
}
