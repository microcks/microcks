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
package io.github.microcks.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * A service that acts as a simple Http proxy to external URLs or services.
 */
@org.springframework.stereotype.Service
public class ProxyService {

   private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

   private static final RestTemplate restTemplate = new RestTemplate();

   /**
    * Call an external Http service url, propagating current method, headers and body.
    * @param externalUrl The target backend service url.
    * @param method      The Http method to propagate
    * @param headers     The Http headers to propagate
    * @param body        The Http body to propagate
    * @return The response entity returned by external service as is
    */
   public ResponseEntity<byte[]> callExternal(URI externalUrl, HttpMethod method, HttpHeaders headers, String body) {
      URI safeExternalUrl = validateExternalUrl(externalUrl);
      headers.put("Host", List.of(safeExternalUrl.getHost()));

      if (log.isDebugEnabled()) {
         log.debug("Proxy request url: {}", safeExternalUrl);
         log.debug("Proxy request headers: {}", headers);
         log.debug("Proxy request body: {}", body);
      }

      try {
         ResponseEntity<byte[]> response = restTemplate.exchange(safeExternalUrl, method,
               new HttpEntity<>(body, headers), byte[].class);

         if (log.isDebugEnabled()) {
            log.debug("Proxy returned: {}", response.getStatusCode());
            log.debug("Proxy response headers: {}", response.getHeaders());
            log.debug("Proxy response body: {}", new String(response.getBody(), StandardCharsets.UTF_8));
         }
         return response;
      } catch (RestClientResponseException ex) {
         if (log.isDebugEnabled()) {
            log.debug("Proxy raised: {}", ex.getStatusCode());
            log.debug("Proxy exception body: {}", ex.getResponseBodyAsString());
         }
         return new ResponseEntity<>(ex.getResponseBodyAsByteArray(), ex.getResponseHeaders(), ex.getStatusCode());
      }
   }

   private static URI validateExternalUrl(URI externalUrl) {
      if (externalUrl == null) {
         throw new IllegalArgumentException("External URL cannot be null");
      }
      String scheme = externalUrl.getScheme();
      if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
         throw new IllegalArgumentException("Only HTTP(S) URLs are allowed");
      }
      if (externalUrl.getHost() == null || externalUrl.getHost().isBlank()) {
         throw new IllegalArgumentException("External URL host is required");
      }
      if (externalUrl.getUserInfo() != null || externalUrl.getFragment() != null) {
         throw new IllegalArgumentException("External URL contains unsupported components");
      }
      String rawPath = externalUrl.getRawPath();
      String normalizedPath = rawPath == null || rawPath.isBlank() ? "/" : URI.create(rawPath).normalize().getPath();
      if (!normalizedPath.startsWith("/") || normalizedPath.contains("..")) {
         throw new IllegalArgumentException("External URL path is invalid");
      }
      try {
         return new URI(scheme, null, externalUrl.getHost(), externalUrl.getPort(), normalizedPath,
               externalUrl.getRawQuery(), null);
      } catch (URISyntaxException e) {
         throw new IllegalArgumentException("External URL is invalid", e);
      }
   }
}
