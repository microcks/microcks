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
package io.microcks.security;

import io.smallrye.jwt.build.Jwt;
import io.smallrye.jwt.util.KeyUtils;
import jakarta.enterprise.context.ApplicationScoped;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Issues and verifies the Microcks JWT tokens (RS256). Keys are loaded from configuration; when none is provided an
 * ephemeral key pair is generated, which is only suitable for a single replica.
 * @author laurent
 */
@ApplicationScoped
public class TokenService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(TokenService.class);

   /** Token claim holding the user roles. */
   public static final String ROLES_CLAIM = "roles";
   private static final String STATE_ISSUER = "microcks-oidc-state";

   private final AuthConfig config;

   private PrivateKey privateKey;
   private PublicKey publicKey;
   private JwtConsumer consumer;
   private JwtConsumer stateConsumer;

   /**
    * Build a TokenService.
    * @param config The authentication configuration
    */
   public TokenService(AuthConfig config) {
      this.config = config;
   }

   /**
    * Issue a signed token for given user, valid for the configured lifetime.
    * @param user The user to issue a token for
    * @return A serialized JWT
    */
   public String issue(UserInfo user) {
      return issue(user, config.tokenTtl());
   }

   /**
    * Issue a signed token for given user.
    * @param user The user to issue a token for
    * @param ttl  The token lifetime
    * @return A serialized JWT
    */
   public String issue(UserInfo user, Duration ttl) {
      ensureKeys();
      var builder = Jwt.issuer(config.issuer()).subject(user.getUsername()).upn(user.getUsername()).expiresIn(ttl);
      if (user.getName() != null) {
         builder.claim("name", user.getName());
      }
      if (user.getGivenName() != null) {
         builder.claim("given_name", user.getGivenName());
      }
      if (user.getFamilyName() != null) {
         builder.claim("family_name", user.getFamilyName());
      }
      if (user.getEmail() != null) {
         builder.claim("email", user.getEmail());
      }
      builder.claim(ROLES_CLAIM, user.getRoles() != null ? List.of(user.getRoles()) : List.of());
      return builder.sign(privateKey);
   }

   /**
    * Verify the signature, issuer and expiration of a token and extract the user it represents.
    * @param token The serialized JWT
    * @return The user or empty if the token is invalid
    */
   public Optional<UserInfo> verify(String token) {
      ensureKeys();
      try {
         JwtClaims claims = consumer.processToClaims(token);
         List<String> roles = claims.getStringListClaimValue(ROLES_CLAIM);
         String username = claims.getSubject();
         return Optional.of(new UserInfo(claims.getStringClaimValue("name"), username,
               claims.getStringClaimValue("given_name"), claims.getStringClaimValue("family_name"),
               claims.getStringClaimValue("email"), roles.toArray(new String[0]), new String[0]));
      } catch (Exception e) {
         log.debug("Rejected invalid token: {}", e.getMessage());
         return Optional.empty();
      }
   }

   /**
    * Issue a short-lived signed state for the OIDC login. It uses a dedicated issuer so it can never be accepted as an
    * access token.
    * @param nonce       The login nonce, also bound to the browser through a cookie
    * @param redirectUri Where to send the browser once logged in
    * @return A serialized JWT
    */
   public String issueState(String nonce, String redirectUri) {
      ensureKeys();
      return Jwt.issuer(STATE_ISSUER).subject("oidc-login").expiresIn(Duration.ofMinutes(5)).claim("nonce", nonce)
            .claim("redirect_uri", redirectUri).sign(privateKey);
   }

   /**
    * Verify an OIDC login state.
    * @param state The serialized state
    * @return The claims or empty if invalid or expired
    */
   public Optional<JwtClaims> verifyState(String state) {
      ensureKeys();
      try {
         return Optional.of(stateConsumer.processToClaims(state));
      } catch (Exception e) {
         return Optional.empty();
      }
   }

   private synchronized void ensureKeys() {
      if (consumer != null) {
         return;
      }
      try {
         if (config.privateKeyLocation().isPresent() && config.publicKeyLocation().isPresent()) {
            privateKey = KeyUtils.decodePrivateKey(readPem(config.privateKeyLocation().get()));
            publicKey = KeyUtils.decodePublicKey(readPem(config.publicKeyLocation().get()));
         } else {
            log.warn("No microcks.auth.private-key-location/public-key-location: using an ephemeral signing key. "
                  + "Tokens will not be valid across restarts or replicas.");
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            privateKey = pair.getPrivate();
            publicKey = pair.getPublic();
         }
      } catch (Exception e) {
         throw new IllegalStateException("Cannot initialize Microcks token signing keys", e);
      }
      stateConsumer = new JwtConsumerBuilder().setVerificationKey(publicKey).setExpectedIssuer(STATE_ISSUER)
            .setRequireExpirationTime().setRequireSubject().setSkipDefaultAudienceValidation().build();
      consumer = new JwtConsumerBuilder().setVerificationKey(publicKey).setExpectedIssuer(config.issuer())
            .setRequireExpirationTime().setRequireSubject().setSkipDefaultAudienceValidation().build();
   }

   private String readPem(String location) throws IOException {
      Path path = Path.of(location);
      if (Files.exists(path)) {
         return Files.readString(path, StandardCharsets.UTF_8);
      }
      try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(location)) {
         if (is == null) {
            throw new IOException("Key not found: " + location);
         }
         return new String(is.readAllBytes(), StandardCharsets.UTF_8);
      }
   }
}
