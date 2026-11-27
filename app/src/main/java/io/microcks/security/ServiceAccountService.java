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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Manages service accounts and authenticates them with the client credentials. Secrets are high entropy random values
 * so a plain SHA-256 digest is sufficient to store them. A bootstrap account can be declared through configuration
 * ({@code microcks.auth.bootstrap-client-id/secret}) so that a minion can be wired without calling the admin API.
 * @author laurent
 */
@ApplicationScoped
public class ServiceAccountService {

   private static final SecureRandom RANDOM = new SecureRandom();

   @Inject
   public ServiceAccountRepository repository;

   @ConfigProperty(name = "microcks.auth.bootstrap-client-id")
   public Optional<String> bootstrapClientId;

   @ConfigProperty(name = "microcks.auth.bootstrap-client-secret")
   public Optional<String> bootstrapClientSecret;

   @ConfigProperty(name = "microcks.auth.bootstrap-roles", defaultValue = "user")
   public List<String> bootstrapRoles;

   /**
    * Create a service account and return its generated secret, which is never retrievable afterwards.
    * @param clientId    The unique client identifier
    * @param description A description
    * @param roles       The roles granted to the account
    * @return The generated clear secret
    */
   public String create(String clientId, String description, List<String> roles) {
      byte[] bytes = new byte[32];
      RANDOM.nextBytes(bytes);
      String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      ServiceAccount account = new ServiceAccount();
      account.clientId = clientId;
      account.description = description;
      account.roles = roles;
      account.secretHash = hash(secret);
      repository.persist(account);
      return secret;
   }

   /**
    * Authenticate a service account.
    * @param clientId The client identifier
    * @param secret   The client secret
    * @return The identity to issue a token for, or empty if credentials are invalid
    */
   public Optional<UserInfo> authenticate(String clientId, String secret) {
      if (clientId == null || secret == null) {
         return Optional.empty();
      }
      if (bootstrapClientId.isPresent() && bootstrapClientSecret.isPresent() && bootstrapClientId.get().equals(clientId)
            && equal(bootstrapClientSecret.get(), secret)) {
         return Optional.of(identity(clientId, bootstrapRoles));
      }
      ServiceAccount account = repository.findByClientId(clientId);
      if (account != null && equal(account.secretHash, hash(secret))) {
         return Optional.of(identity(clientId, account.roles));
      }
      return Optional.empty();
   }

   private static UserInfo identity(String clientId, List<String> roles) {
      return new UserInfo(clientId, clientId, null, null, null, roles.toArray(new String[0]), new String[0]);
   }

   private static boolean equal(String a, String b) {
      return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
   }

   private static String hash(String secret) {
      try {
         byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
         return Base64.getEncoder().encodeToString(digest);
      } catch (NoSuchAlgorithmException e) {
         throw new IllegalStateException(e);
      }
   }
}
