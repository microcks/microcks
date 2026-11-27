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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Duration;
import java.util.Optional;

/**
 * Configuration of Microcks authentication (prefix {@code microcks.auth}). Authentication is disabled by default: every
 * caller is then granted all roles. When enabled, the API is a resource server validating Microcks-issued JWT tokens.
 * @author laurent
 */
@ApplicationScoped
public class AuthConfig {

   @ConfigProperty(name = "microcks.auth.enabled", defaultValue = "false")
   boolean enabled;

   @ConfigProperty(name = "microcks.auth.issuer", defaultValue = "microcks")
   String issuer;

   @ConfigProperty(name = "microcks.auth.token-ttl", defaultValue = "PT2H")
   Duration tokenTtl;

   @ConfigProperty(name = "microcks.auth.private-key-location")
   Optional<String> privateKeyLocation;

   @ConfigProperty(name = "microcks.auth.public-key-location")
   Optional<String> publicKeyLocation;

   /** @return Whether API authentication is enforced */
   public boolean enabled() {
      return enabled;
   }

   /** @return The issuer (iss claim) of tokens issued and accepted by this instance */
   public String issuer() {
      return issuer;
   }

   /** @return The lifetime of issued tokens */
   public Duration tokenTtl() {
      return tokenTtl;
   }

   /** @return Location (file or classpath resource) of the PKCS#8 PEM private key used to sign tokens */
   public Optional<String> privateKeyLocation() {
      return privateKeyLocation;
   }

   /** @return Location (file or classpath resource) of the PEM public key used to verify tokens */
   public Optional<String> publicKeyLocation() {
      return publicKeyLocation;
   }
}
