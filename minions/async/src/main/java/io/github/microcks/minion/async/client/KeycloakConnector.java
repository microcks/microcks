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
package io.github.microcks.minion.async.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Base64;

@ApplicationScoped
/**
 * Connector to the Keycloak server configured for Microcks API server. This connector allows to retrieved valid OAuth /
 * JWT token for configured service account.
 * @author laurent
 */
public class KeycloakConnector {

   private final Logger logger = Logger.getLogger(getClass());

   @ConfigProperty(name = "microcks.serviceaccount")
   String serviceAccount;

   @ConfigProperty(name = "microcks.serviceaccount.credentials")
   String saCredentials;

   /**
    * Connect to the given OIDC endpoint on a Keycloak server, realize a client_credentials grant type with configured
    * account and credentials to finally return the OAuth accesc token.
    * @param tokenEndpoint The OIDC endpoint on which to authenticate
    * @return The OAuth access token after successful authentication
    * @throws ConnectorException If connection faild (bad endpoint) or authentication failed (bad account or
    *                            credentials)
    * @throws IOException        In case of communication exception
    */
   public String connectAndGetOAuthToken(String tokenEndpoint) throws ConnectorException, IOException {
      CloseableHttpClient httpClient = null;

      try {
         // Start creating a SSL Context that accepts all because we may have self-signed certs.
         TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
         SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
         SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);

         // Configuring a httpClient that disables host name validation for certificates.
         httpClient = HttpClients.custom().setSSLHostnameVerifier((String s, SSLSession sslSession) -> true)
               .setSSLSocketFactory(sslsf).build();
      } catch (GeneralSecurityException gse) {
         logger.error("Caught a SecurityException when building the SSL Context", gse);
         throw new ConnectorException("SSLContext cannot be created to reach Keycloak endpoint: " + gse.getMessage());
      }

      try {
         // Prepare a post request with grant_type client credentials flow.
         HttpPost tokenRequest = new HttpPost(tokenEndpoint);
         tokenRequest.addHeader("Content-Type", "application/x-www-form-urlencoded");
         tokenRequest.addHeader("Accept", "application/json");
         tokenRequest.addHeader("Authorization",
               "Basic " + Base64.getEncoder().encodeToString((serviceAccount + ":" + saCredentials).getBytes("UTF-8")));
         tokenRequest.setEntity(new StringEntity("grant_type=client_credentials"));

         // Execute request and retrieve content as string.
         CloseableHttpResponse tokenResponse = httpClient.execute(tokenRequest);

         if (tokenResponse.getStatusLine().getStatusCode() != 200) {
            logger.error(
                  "OAuth token cannot be retrieved for Keycloak server, check microcks.serviceaccount configuration");
            logger.error("  tokenResponse.statusLine: " + tokenResponse.getStatusLine().toString());
            logger.error("  tokenResponse.statusCode: " + tokenResponse.getStatusLine().getStatusCode());
            throw new ConnectorException(
                  "OAuth token cannot be retrieved for Microcks. Check microcks.serviceaccount.");
         }

         String result = EntityUtils.toString(tokenResponse.getEntity());
         logger.debug("Result: " + result);

         // This should be a JSON token so parse it with Jackson.
         ObjectMapper mapper = new ObjectMapper();
         JsonNode jsonToken = mapper.readTree(result);

         // Retrieve and return access_token.
         String accessToken = jsonToken.path("access_token").asText();
         logger.debug("Got an access token: " + accessToken);
         return accessToken;
      } finally {
         httpClient.close();
      }
   }
}
