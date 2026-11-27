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
package io.microcks.service;

import io.microcks.test.http.ApacheClientHttpRequestFactory;
import io.microcks.util.http.ClientHttpRequest;
import io.microcks.util.http.ClientHttpResponse;
import io.microcks.util.http.HttpHeaders;
import io.microcks.util.http.HttpMethod;
import io.microcks.util.http.HttpStatusCode;
import io.microcks.web.ResponseResult;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A service that acts as a simple Http proxy to external URLs or services.
 * @author laurent
 */
@ApplicationScoped
public class ProxyService {

   private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

   /** Headers that must be computed by the HTTP client or the container, never propagated as is. */
   private static final Set<String> SKIPPED_HEADERS = Set.of("host", "content-length", "transfer-encoding",
         "connection", "keep-alive", "upgrade", "te", "trailer", "proxy-authenticate", "proxy-authorization");

   private final CloseableHttpClient httpClient = HttpClients.custom().useSystemProperties().disableContentCompression()
         .disableAutomaticRetries().setDefaultRequestConfig(RequestConfig.custom().setRedirectsEnabled(false).build())
         .build();
   private final ApacheClientHttpRequestFactory requestFactory = new ApacheClientHttpRequestFactory(httpClient);

   /**
    * Call an external Http service url, propagating current method, headers and body.
    * @param externalUrl The target backend service url.
    * @param method      The Http method to propagate
    * @param headers     The Http headers to propagate
    * @param body        The Http body to propagate
    * @return The result returned by external service as is (whatever the status code)
    */
   public ResponseResult callExternal(URI externalUrl, HttpMethod method, Map<String, List<String>> headers,
         String body) {
      if (log.isDebugEnabled()) {
         log.debug("Proxy request url: {}", externalUrl);
         log.debug("Proxy request headers: {}", headers);
         log.debug("Proxy request body: {}", body);
      }

      try {
         ClientHttpRequest request = requestFactory.createRequest(externalUrl, method);
         for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            if (!SKIPPED_HEADERS.contains(header.getKey().toLowerCase())) {
               header.getValue().forEach(v -> request.getHeaders().add(header.getKey(), v));
            }
         }
         request.getHeaders().set(HttpHeaders.HOST, hostHeader(externalUrl));
         if (body != null && !body.isEmpty()) {
            request.getBody().write(body.getBytes(StandardCharsets.UTF_8));
         }

         try (ClientHttpResponse response = request.execute()) {
            byte[] content = response.getBody().readAllBytes();
            if (log.isDebugEnabled()) {
               log.debug("Proxy returned: {}", response.getStatusCode());
               log.debug("Proxy response headers: {}", response.getHeaders());
               log.debug("Proxy response body: {}", new String(content, StandardCharsets.UTF_8));
            }
            return new ResponseResult(response.getStatusCode(), response.getHeaders(), content);
         }
      } catch (IOException ioe) {
         log.error("Proxy call to {} failed", externalUrl, ioe);
         return new ResponseResult(new HttpStatusCode(502), null,
               ("Bad gateway: " + ioe.getMessage()).getBytes(StandardCharsets.UTF_8));
      }
   }

   private String hostHeader(URI uri) {
      return uri.getPort() != -1 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
   }

   @PreDestroy
   void close() throws IOException {
      httpClient.close();
   }
}
