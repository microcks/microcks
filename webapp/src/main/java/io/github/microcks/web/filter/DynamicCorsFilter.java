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

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;

/**
 * Servlet filter that set the response CORS headers accordingly the configuration and incoming request.
 */
public class DynamicCorsFilter implements Filter {
   private final String corsAllowedOrigins;
   private final Boolean corsAllowCredentials;

   /**
    * Build a new DynamicCorsFilter.
    *
    * @param corsAllowedOrigins   Allowed origin forced if nothing found in incoming request
    * @param corsAllowCredentials Whether to set allow credentials
    */
   public DynamicCorsFilter(String corsAllowedOrigins, Boolean corsAllowCredentials) {
      this.corsAllowedOrigins = corsAllowedOrigins;
      this.corsAllowCredentials = corsAllowCredentials;
   }

   @Override
   public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
         throws IOException, ServletException {
      HttpServletResponse response = (HttpServletResponse) servletResponse;
      HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
      String origin = httpRequest.getHeader("Origin");
      if (origin == null) {
         origin = corsAllowedOrigins;
      }
      response.setHeader("Access-Control-Allow-Origin", origin);
      response.setHeader("Access-Control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE");
      response.setHeader("Access-Control-Max-Age", "3600");
      response.setHeader("Access-Control-Allow-Headers", getHeadersString(httpRequest));
      if (Boolean.TRUE.equals(corsAllowCredentials)) {
         response.setHeader("Access-Control-Allow-Credentials", "true");
      }
      chain.doFilter(servletRequest, servletResponse);
   }

   /**
    * Build a string with all the headers from the incoming request.
    *
    * This method will also add the headers from the "Access-Control-Request-Headers" header if present.
    *
    * @param httpRequest
    * @return
    */
   private String getHeadersString(HttpServletRequest httpRequest) {
      var headerNamesList = new HashSet<String>();
      Enumeration<String> headerNamesEnumeration = httpRequest.getHeaderNames();

      if (headerNamesEnumeration != null) {
         headerNamesList.addAll(Collections.list(headerNamesEnumeration));
      }

      String accessControlRequestHeaders = httpRequest.getHeader("Access-Control-Request-Headers");
      if (accessControlRequestHeaders != null) {
         List<String> headerNames = Arrays.stream(accessControlRequestHeaders.split(",")).map(String::trim).toList();
         headerNamesList.addAll(headerNames);
      }

      if (headerNamesList.isEmpty()) {
         return "*";
      }
      return String.join(", ", headerNamesList);
   }
}
