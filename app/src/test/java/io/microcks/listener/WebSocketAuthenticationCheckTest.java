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
import io.microcks.security.UserInfo;

import io.quarkus.websockets.next.HttpUpgradeCheck;
import io.quarkus.websockets.next.HttpUpgradeCheck.CheckResult;
import io.vertx.core.http.HttpServerRequest;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for the WebSocket upgrade authentication check.
 * @author laurent
 */
class WebSocketAuthenticationCheckTest {

   private static WebSocketAuthenticationCheck check(boolean enabled) {
      AuthConfig config = mock(AuthConfig.class);
      when(config.enabled()).thenReturn(enabled);
      when(config.issuer()).thenReturn("microcks");
      when(config.tokenTtl()).thenReturn(Duration.ofMinutes(5));
      when(config.privateKeyLocation()).thenReturn(Optional.empty());
      when(config.publicKeyLocation()).thenReturn(Optional.empty());
      WebSocketAuthenticationCheck check = new WebSocketAuthenticationCheck();
      check.config = config;
      check.tokenService = new TokenService(config);
      return check;
   }

   private static CheckResult perform(WebSocketAuthenticationCheck check, String authorization) {
      HttpServerRequest request = mock(HttpServerRequest.class);
      when(request.getHeader("Authorization")).thenReturn(authorization);
      HttpUpgradeCheck.HttpUpgradeContext context = mock(HttpUpgradeCheck.HttpUpgradeContext.class);
      when(context.httpRequest()).thenReturn(request);
      return check.perform(context).await().indefinitely();
   }

   @Test
   void upgradeIsOpenWhenAuthenticationIsDisabled() {
      assertTrue(perform(check(false), null).isUpgradePermitted());
   }

   @Test
   void upgradeRequiresAValidBearerWhenAuthenticationIsEnabled() {
      WebSocketAuthenticationCheck check = check(true);
      String token = check.tokenService
            .issue(new UserInfo("sa", "sa", null, null, null, new String[] { "user" }, new String[0]));

      assertTrue(perform(check, "Bearer " + token).isUpgradePermitted());
      CheckResult missing = perform(check, null);
      assertTrue(!missing.isUpgradePermitted());
      assertEquals(401, missing.getHttpResponseCode());
      assertTrue(!perform(check, "Bearer garbage").isUpgradePermitted());
   }
}
