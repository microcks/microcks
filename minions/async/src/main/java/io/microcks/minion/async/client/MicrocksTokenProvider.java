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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Provides the Bearer token the minion presents to Microcks app. It first asks <code>/api/auth/config</code> to know if
 * authentication is enabled; if so, it obtains an access token with the <code>client_credentials</code> grant on
 * <code>/api/auth/token</code> using the Microcks service account, and renews it shortly before it expires. When
 * authentication is disabled (default, uber distribution), no token is required.
 * @author laurent
 */
@ApplicationScoped
public class MicrocksTokenProvider {

   private static final Logger logger = Logger.getLogger(MicrocksTokenProvider.class);

   private static final long RENEW_MARGIN_SECONDS = 30L;

   @ConfigProperty(name = "io.microcks.minion.async.client.MicrocksAPIConnector/mp-rest/url")
   String microcksUrl;

   @ConfigProperty(name = "microcks.serviceaccount", defaultValue = "")
   String serviceAccount;

   @ConfigProperty(name = "microcks.serviceaccount.credentials", defaultValue = "")
   String serviceAccountCredentials;

   private final ObjectMapper mapper = new ObjectMapper();
   private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

   private volatile Boolean authEnabled;
   private String token;
   private long expiresAtMillis;

   /**
    * Get the value of the Authorization header to send to Microcks.
    * @return The "Bearer xxx" value or empty if Microcks does not require authentication
    * @throws ConnectorException If Microcks requires authentication and a token cannot be retrieved
    */
   public synchronized Optional<String> authorization() throws ConnectorException {
      try {
         if (authEnabled == null) {
            JsonNode config = mapper.readTree(send(HttpRequest.newBuilder(url("/api/auth/config")).GET()).body());
            authEnabled = config.path("enabled").asBoolean(false);
            logger.infof("Microcks authentication is %s", authEnabled ? "enabled" : "disabled");
         }
         if (!authEnabled) {
            return Optional.empty();
         }
         if (token == null || System.currentTimeMillis() >= expiresAtMillis) {
            fetchToken();
         }
         return Optional.of("Bearer " + token);
      } catch (IOException e) {
         throw new ConnectorException("Cannot reach Microcks to get an access token: " + e.getMessage());
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new ConnectorException("Interrupted while getting an access token");
      }
   }

   /** Forget the current token and the discovered configuration, typically after a 401 or a connection loss. */
   public synchronized void reset() {
      token = null;
      authEnabled = null;
   }

   private void fetchToken() throws IOException, InterruptedException, ConnectorException {
      String basic = Base64.getEncoder()
            .encodeToString((serviceAccount + ":" + serviceAccountCredentials).getBytes(StandardCharsets.UTF_8));
      HttpRequest.Builder request = HttpRequest.newBuilder(url("/api/auth/token"))
            .header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json")
            .header("Authorization", "Basic " + basic).POST(HttpRequest.BodyPublishers
                  .ofString("grant_type=" + URLEncoder.encode("client_credentials", StandardCharsets.UTF_8)));
      HttpResponse<String> response = send(request);
      if (response.statusCode() != 200) {
         logger.errorf("Access token refused by Microcks (HTTP %d), check microcks.serviceaccount configuration",
               response.statusCode());
         throw new ConnectorException("Access token cannot be retrieved from Microcks. Check microcks.serviceaccount.");
      }
      JsonNode json = mapper.readTree(response.body());
      token = json.path("access_token").asText();
      long expiresIn = json.path("expires_in").asLong(300L);
      expiresAtMillis = System.currentTimeMillis() + Math.max(1L, expiresIn - RENEW_MARGIN_SECONDS) * 1000L;
      logger.info("Obtained a Microcks access token for the service account");
   }

   private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
      return httpClient.send(request.timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
   }

   private URI url(String path) {
      String base = microcksUrl.trim();
      while (base.endsWith("/")) {
         base = base.substring(0, base.length() - 1);
      }
      return URI.create(base + path);
   }
}
