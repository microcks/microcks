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
      headers.put("Host", List.of(externalUrl.getHost()));

      if (log.isDebugEnabled()) {
         log.debug("Proxy request url: {}", externalUrl);
         log.debug("Proxy request headers: {}", headers);
         log.debug("Proxy request body: {}", body);
      }

      try {
         ResponseEntity<byte[]> response = restTemplate.exchange(externalUrl, method, new HttpEntity<>(body, headers),
               byte[].class);

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
}
