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

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

/**
 * A service that acts as a simple Http proxy to external URLs or services.
 */
@org.springframework.stereotype.Service
public class ProxyService {

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
      try {
         return restTemplate.exchange(externalUrl, method, new HttpEntity<>(body, headers), byte[].class);
      } catch (RestClientResponseException ex) {
         return new ResponseEntity<>(ex.getResponseBodyAsByteArray(), ex.getResponseHeaders(), ex.getStatusCode());
      }
   }
}
