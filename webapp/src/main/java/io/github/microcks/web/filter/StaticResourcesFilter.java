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

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Servlet filter for adapting static resources access path when in production mode (cause everything is now into /dist
 * directory !)
 * @author laurent
 */
public class StaticResourcesFilter implements Filter {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(StaticResourcesFilter.class);

   @Override
   public void init(FilterConfig filterConfig) throws ServletException {
   }

   @Override
   public void destroy() {
   }

   @Override
   public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
         throws IOException, ServletException {
      HttpServletRequest httpRequest = (HttpServletRequest) servletRequest;
      String contextPath = httpRequest.getContextPath();
      String requestURI = httpRequest.getRequestURI();
      requestURI = StringUtils.substringAfter(requestURI, contextPath);
      if (StringUtils.equals("/", requestURI)) {
         requestURI = "/index.html";
      }
      String newURI = "/dist" + requestURI;
      log.debug("Dispatching to URI: {}", newURI);
      servletRequest.getRequestDispatcher(newURI).forward(servletRequest, servletResponse);
   }
}
