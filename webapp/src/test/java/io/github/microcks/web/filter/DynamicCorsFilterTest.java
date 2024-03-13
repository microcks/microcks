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
package io.github.microcks.web.filter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import graphql.language.Argument;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Vector;

public class DynamicCorsFilterTest {

   HttpServletRequest request;
   HttpServletResponse response;
   FilterChain chain;

   @BeforeEach
   void setUp() {
      // mock HttpServletRequest, HttpServletResponse, and FilterChain
      request = mock(HttpServletRequest.class);
      response = mock(HttpServletResponse.class);
      chain = mock(FilterChain.class);
   }

   @Nested
   class WithAllowCredentials {

      private final DynamicCorsFilter filter = new DynamicCorsFilter("http://allowed-origin.com", true);

    @Test
    void shouldSetAllowCredentialsHeader() throws IOException, ServletException {
      when(request.getHeader("Origin")).thenReturn("http://example.com");
      filter.doFilter(request, response, chain);
      verify(response).setHeader("Access-Control-Allow-Credentials", "true");
    }

    @Test
    void shouldSetOriginHeader() throws IOException, ServletException {
      when(request.getHeader("Origin")).thenReturn("http://example.com");
      filter.doFilter(request, response, chain);
      verify(response).setHeader("Access-Control-Allow-Origin", "http://example.com");
    }

      @Test
      void shouldSetAccessControlAllowHeaders() throws IOException, ServletException {
         Vector<String> headerNames = new Vector<>();
         headerNames.add("Content-Type");
         headerNames.add("Authorization");
         Enumeration<String> headerNamesEnum = headerNames.elements();
         when(request.getHeaderNames()).thenReturn(headerNamesEnum);

         filter.doFilter(request, response, chain);
         ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
         verify(response).setHeader(eq("Access-Control-Allow-Headers"), captor.capture());
         String value = captor.getValue();
         assert value.contains("Content-Type");
         assert value.contains("Authorization");
      }

      @Test
      void shouldAddRequestHeadersToAccessControlAllowHeaders() throws IOException, ServletException {
         Vector<String> headerNames = new Vector<>();
         headerNames.add("Content-Type");
         headerNames.add("Authorization");
         headerNames.add("X-Custom-Header");
         headerNames.add("Access-Control-Request-Headers");
         Enumeration<String> headerNamesEnum = headerNames.elements();
         when(request.getHeaderNames()).thenReturn(headerNamesEnum);

         when(request.getHeader("Access-Control-Request-Headers")).thenReturn("X-Custom-Header-1, X-Custom-Header-2");

         filter.doFilter(request, response, chain);

         ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);

         verify(response).setHeader(eq("Access-Control-Allow-Headers"), captor.capture());
         String value = captor.getValue();
         assert value.contains("Content-Type");
         assert value.contains("Authorization");
         assert value.contains("X-Custom-Header");
         assert value.contains("X-Custom-Header-1");
         assert value.contains("X-Custom-Header-2");
         assert value.contains("Access-Control-Request-Headers");
      }
   }

   @Nested
   class WithoutAllowCredentials {

      private final DynamicCorsFilter filter = new DynamicCorsFilter("http://allowed-origin.com", false);

    @Test
    void shouldNotSetAllowCredentialsHeader() throws IOException, ServletException {
      when(request.getHeader("Origin")).thenReturn("http://example.com");
      filter.doFilter(request, response, chain);
      verify(response, never()).setHeader(eq("Access-Control-Allow-Credentials"), anyString());
    }
   }
}
