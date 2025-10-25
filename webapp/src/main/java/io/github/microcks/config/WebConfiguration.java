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
package io.github.microcks.config;

import io.github.microcks.web.filter.CorsFilter;
import io.github.microcks.web.filter.DynamicCorsFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;

import java.util.Arrays;
import java.util.EnumSet;

/**
 * Spring Web configuration class.
 * @author laurent
 */
@Configuration
public class WebConfiguration implements ServletContextInitializer {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(WebConfiguration.class);

   @Autowired
   private Environment env;

   @Value("${mocks.rest.enable-cors-policy}")
   private Boolean enableCorsPolicy = null;
   @Value("${mocks.rest.cors.allowedOrigins}")
   private String corsAllowedOrigins;
   @Value("${mocks.rest.cors.allowCredentials}")
   private Boolean corsAllowCredentials;


   @Override
   public void onStartup(ServletContext servletContext) throws ServletException {
      log.info("Starting web application configuration, using profiles: {}", Arrays.toString(env.getActiveProfiles()));
      EnumSet<DispatcherType> disps = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC);
      initCORSFilter(servletContext, disps);
      log.info("Web application fully configured");
   }


   /** Configure the CORS filter on API endpoints as well as on Mock endpoints. */
   private void initCORSFilter(ServletContext servletContext, EnumSet<DispatcherType> disps) {
      FilterRegistration.Dynamic corsFilter = servletContext.addFilter("corsFilter", new CorsFilter());
      corsFilter.addMappingForUrlPatterns(disps, true, "/api/*");
      corsFilter.addMappingForUrlPatterns(disps, true, "/dynarest/*");
      corsFilter.setAsyncSupported(true);
      if (Boolean.TRUE.equals(enableCorsPolicy)) {
         FilterRegistration.Dynamic dynamicCorsFilter = servletContext.addFilter("dynamicCorsFilter",
               new DynamicCorsFilter(corsAllowedOrigins, corsAllowCredentials));
         dynamicCorsFilter.addMappingForUrlPatterns(disps, true, "/rest/*");
         dynamicCorsFilter.addMappingForUrlPatterns(disps, true, "/rest-valid/*");
         dynamicCorsFilter.addMappingForUrlPatterns(disps, true, "/soap/*");
         dynamicCorsFilter.addMappingForUrlPatterns(disps, true, "/graphql/*");
      }
   }
}
