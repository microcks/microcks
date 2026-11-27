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

package io.microcks.listener;

import io.microcks.security.AuthConfig;
import io.microcks.security.TokenService;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Protects the WebSocket endpoints consumed by minions: when authentication is enabled, the HTTP upgrade requires a
 * valid Microcks Bearer token (a service account one) in the Authorization header. Nothing is checked otherwise.
 * @author laurent
 */
@ApplicationScoped
public class WebSocketAuthenticationCheck implements HttpUpgradeCheck {

   private static final String BEARER_PREFIX = "Bearer ";

   @Inject
   AuthConfig config;

   @Inject
   TokenService tokenService;

   @Override
   public Uni<CheckResult> perform(HttpUpgradeContext context) {
      if (!config.enabled()) {
         return CheckResult.permitUpgrade();
      }
      String header = context.httpRequest().getHeader("Authorization");
      if (header != null && header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
            && tokenService.verify(header.substring(BEARER_PREFIX.length()).trim()).isPresent()) {
         return CheckResult.permitUpgrade();
      }
      return CheckResult.rejectUpgrade(401);
   }
}
