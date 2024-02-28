package io.github.microcks.service;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
public class ProxyService {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(ProxyService.class);

   private static final String EXTERNAL_URL_PREFIX = "microcks-calls:";

   private static final RestTemplate restTemplate = new RestTemplate();

   public ResponseEntity<byte[]> callExternal(URI externalUrl, HttpMethod method, HttpHeaders headers, String body) {
      headers.put("Host", List.of(externalUrl.getHost()));
      try {
         return restTemplate.exchange(externalUrl, method, new HttpEntity<>(body, headers), byte[].class);
      } catch (RestClientResponseException ex) {
         return new ResponseEntity<>(ex.getResponseBodyAsByteArray(), ex.getResponseHeaders(), ex.getStatusCode());
      }
   }

   public Optional<URI> extractExternalUrl(String dispatchingCriteria, HttpServletRequest request) {
      if (dispatchingCriteria != null && dispatchingCriteria.startsWith(EXTERNAL_URL_PREFIX)) {
         String externalUrl = dispatchingCriteria.replaceFirst(EXTERNAL_URL_PREFIX, "").trim();
         if (!externalUrl.contentEquals(request.getRequestURL())) {
            try {
               return Optional.of(UriComponentsBuilder.fromHttpUrl(externalUrl).build().toUri());
            } catch (IllegalArgumentException ex) {
               log.warn("Invalid external URL in the dispatcher - {}", externalUrl);
            }
         }
      }
      return Optional.empty();
   }
}
