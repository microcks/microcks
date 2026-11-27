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

package io.microcks.minion.async.client;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for the service account token provider.
 * @author laurent
 */
class MicrocksTokenProviderTest {

   private HttpServer server;
   private final AtomicInteger tokenCalls = new AtomicInteger();
   private final AtomicReference<String> basicAuth = new AtomicReference<>();

   @AfterEach
   void stop() {
      if (server != null) {
         server.stop(0);
      }
   }

   private MicrocksTokenProvider provider(boolean authEnabled, int tokenStatus) throws Exception {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/api/auth/config", ex -> reply(ex, 200, "{\"enabled\":" + authEnabled + "}"));
      server.createContext("/api/auth/token", ex -> {
         tokenCalls.incrementAndGet();
         basicAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
         reply(ex, tokenStatus, "{\"access_token\":\"jwt-" + tokenCalls.get() + "\",\"expires_in\":300}");
      });
      server.start();
      MicrocksTokenProvider provider = new MicrocksTokenProvider();
      provider.microcksUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
      provider.serviceAccount = "minion";
      provider.serviceAccountCredentials = "s3cret";
      return provider;
   }

   private static void reply(HttpExchange ex, int status, String body) throws IOException {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      ex.getResponseHeaders().add("Content-Type", "application/json");
      ex.sendResponseHeaders(status, bytes.length);
      ex.getResponseBody().write(bytes);
      ex.close();
   }

   @Test
   void noTokenIsRequestedWhenAuthenticationIsDisabled() throws Exception {
      MicrocksTokenProvider provider = provider(false, 200);
      assertTrue(provider.authorization().isEmpty());
      assertEquals(0, tokenCalls.get());
   }

   @Test
   void tokenIsObtainedWithBasicCredentialsAndCached() throws Exception {
      MicrocksTokenProvider provider = provider(true, 200);
      assertEquals("Bearer jwt-1", provider.authorization().orElseThrow());
      assertEquals("Bearer jwt-1", provider.authorization().orElseThrow());
      assertEquals(1, tokenCalls.get());
      assertEquals("Basic bWluaW9uOnMzY3JldA==", basicAuth.get());

      provider.reset();
      assertEquals("Bearer jwt-2", provider.authorization().orElseThrow());
   }

   @Test
   void refusedCredentialsRaiseAConnectorException() throws Exception {
      MicrocksTokenProvider provider = provider(true, 401);
      assertThrows(ConnectorException.class, provider::authorization);
   }
}
