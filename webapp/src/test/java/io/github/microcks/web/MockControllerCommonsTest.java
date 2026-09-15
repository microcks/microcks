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
package io.github.microcks.web;

import io.github.microcks.domain.Response;
import io.github.microcks.util.DispatchStyles;
import io.github.microcks.util.delay.DelaySpec;
import io.github.microcks.util.dispatcher.JsonMappingException;
import io.github.microcks.util.dispatcher.ProxyFallbackSpecification;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * This is a test case for the MockControllerCommons class.
 * @author laurent
 */
class MockControllerCommonsTest {

   @Test
   void shouldUseDelayAndStrategyFromHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "500");
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER, "random-10");

      DelaySpec spec = MockControllerCommons.getDelay(headers, null, "fixed");
      assertNotNull(spec);
      assertEquals(500L, spec.baseValue());
      assertEquals("random-10", spec.strategyName());
   }

   @Test
   void shouldBeCaseInsensitiveForStrategyHeader() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "250");
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_STRATEGY_HEADER, "RaNdOm-20");

      DelaySpec spec = MockControllerCommons.getDelay(headers, null, "fixed");
      assertNotNull(spec);
      assertEquals(250L, spec.baseValue());
      assertEquals("RaNdOm-20", spec.strategyName()); // keep header value as provided
   }

   @Test
   void shouldFallBackToParameterWhenHeaderValueInvalid() {
      HttpHeaders headers = new HttpHeaders();
      headers.add(MockControllerCommons.X_MICROCKS_DELAY_HEADER, "not-a-number");

      DelaySpec spec = MockControllerCommons.getDelay(headers, 100L, "random");
      assertNotNull(spec);
      assertEquals(100L, spec.baseValue());
      assertEquals("random", spec.strategyName());
   }

   @Nested
   @DisplayName("Proxy URL computation")
   class ProxyUrlTests {

      private static final String ENCODED_PROXY_URL = "https://backend.example.com/resources/urn%3Aorg%3Aexample%3Aservice%2Fitem-ABC123";
      private static final String ENCODED_PROXY_PATH = "/resources/urn%3Aorg%3Aexample%3Aservice%2Fitem-ABC123";

      private HttpServletRequest mockRequest(String requestUrl, String queryString) {
         HttpServletRequest request = mock(HttpServletRequest.class);
         when(request.getRequestURL()).thenReturn(new StringBuffer(requestUrl));
         when(request.getQueryString()).thenReturn(queryString);
         return request;
      }

      @Test
      @DisplayName("should keep already encoded characters of a PROXY url")
      void shouldKeepEncodedCharactersOfProxyUrl() {
         HttpServletRequest request = mockRequest("http://localhost:8080/rest/encoded-api/1.0.0/resources", null);

         Optional<URI> uri = MockControllerCommons.getProxyUrlIfProxyIsNeeded(DispatchStyles.PROXY, ENCODED_PROXY_URL,
               "", null, request, null);

         assertTrue(uri.isPresent());
         assertEquals(ENCODED_PROXY_PATH, uri.get().getRawPath());
         assertFalse(uri.get().toString().contains("%25"));
      }

      @Test
      @DisplayName("should append resource path to PROXY url without encoding it twice")
      void shouldAppendResourcePathToProxyUrl() {
         HttpServletRequest request = mockRequest("http://localhost:8080/rest/encoded-api/1.0.0/resources", null);

         Optional<URI> uri = MockControllerCommons.getProxyUrlIfProxyIsNeeded(DispatchStyles.PROXY,
               "https://backend.example.com/resources/", "/urn%3Aorg%3Aitem-ABC123", null, request, null);

         assertTrue(uri.isPresent());
         assertEquals("/resources/urn%3Aorg%3Aitem-ABC123", uri.get().getRawPath());
      }

      @Test
      @DisplayName("should keep already encoded characters of the query string")
      void shouldKeepEncodedCharactersOfQueryString() {
         HttpServletRequest request = mockRequest("http://localhost:8080/rest/encoded-api/1.0.0/invocations",
               "qualifier=DEFAULT&name=a%20b");

         Optional<URI> uri = MockControllerCommons.getProxyUrlIfProxyIsNeeded(DispatchStyles.PROXY,
               "https://backend.example.com/invocations", "", null, request, null);

         assertTrue(uri.isPresent());
         assertEquals("qualifier=DEFAULT&name=a%20b", uri.get().getRawQuery());
      }

      @Test
      @DisplayName("should keep already encoded characters of a PROXY_FALLBACK url")
      void shouldKeepEncodedCharactersOfProxyFallbackUrl() throws JsonMappingException {
         HttpServletRequest request = mockRequest("http://localhost:8080/rest/encoded-api/1.0.0/resources", null);
         ProxyFallbackSpecification proxyFallback = ProxyFallbackSpecification
               .buildFromJsonString("{\"dispatcher\": \"URI_PARTS\", \"dispatcherRules\": \"name\", \"proxyUrl\": \""
                     + ENCODED_PROXY_URL + "\"}");

         Optional<URI> uri = MockControllerCommons.getProxyUrlIfProxyIsNeeded(DispatchStyles.PROXY_FALLBACK, "name", "",
               proxyFallback, request, null);

         assertTrue(uri.isPresent());
         assertEquals(ENCODED_PROXY_PATH, uri.get().getRawPath());
      }

      @Test
      @DisplayName("should not proxy when a response was found with PROXY_FALLBACK")
      void shouldNotProxyWhenResponseFoundWithProxyFallback() throws JsonMappingException {
         HttpServletRequest request = mockRequest("http://localhost:8080/rest/encoded-api/1.0.0/resources", null);
         ProxyFallbackSpecification proxyFallback = ProxyFallbackSpecification
               .buildFromJsonString("{\"dispatcher\": \"URI_PARTS\", \"dispatcherRules\": \"name\", \"proxyUrl\": \""
                     + ENCODED_PROXY_URL + "\"}");

         Optional<URI> uri = MockControllerCommons.getProxyUrlIfProxyIsNeeded(DispatchStyles.PROXY_FALLBACK, "name", "",
               proxyFallback, request, new Response());

         assertTrue(uri.isEmpty());
      }

      @Test
      @DisplayName("should not proxy when dispatcher is not a proxy one")
      void shouldNotProxyWhenNoProxyDispatcher() {
         HttpServletRequest request = mockRequest("http://localhost:8080/rest/encoded-api/1.0.0/resources", null);

         Optional<URI> uri = MockControllerCommons.getProxyUrlIfProxyIsNeeded(DispatchStyles.URI_PARTS, "name", "",
               null, request, null);

         assertTrue(uri.isEmpty());
      }

      @Test
      @DisplayName("should not proxy when external url equals incoming request url")
      void shouldNotProxyWhenExternalUrlEqualsRequestUrl() {
         HttpServletRequest request = mockRequest(ENCODED_PROXY_URL, null);

         Optional<URI> uri = MockControllerCommons.getProxyUrlIfProxyIsNeeded(DispatchStyles.PROXY, ENCODED_PROXY_URL,
               "", null, request, null);

         assertTrue(uri.isEmpty());
      }
   }
}
