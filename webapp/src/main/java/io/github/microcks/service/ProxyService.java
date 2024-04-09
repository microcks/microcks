package io.github.microcks.service;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;

@org.springframework.stereotype.Service
public class ProxyService {

   private static final RestTemplate restTemplate = new RestTemplate();

   public ResponseEntity<byte[]> callExternal(URI externalUrl, HttpMethod method, HttpHeaders headers, String body) {
      headers.put("Host", List.of(externalUrl.getHost()));
      try {
         return restTemplate.exchange(externalUrl, method, new HttpEntity<>(body, headers), byte[].class);
      } catch (RestClientResponseException ex) {
         return new ResponseEntity<>(ex.getResponseBodyAsByteArray(), ex.getResponseHeaders(), ex.getStatusCode());
      }
   }
}
