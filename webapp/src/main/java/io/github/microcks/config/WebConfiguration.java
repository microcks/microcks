/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.config;

import io.github.microcks.web.filter.CorsFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import java.util.Arrays;
import java.util.EnumSet;

/**
 * @author laurent
 */
@Configuration
public class WebConfiguration implements ServletContextInitializer {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(WebConfiguration.class);

   @Autowired
   private Environment env;

   @Value("${mocks.rest.enable-cors-policy}")
   private final Boolean enableCorsPolicy = null;


   @Override
   public void onStartup(ServletContext servletContext) throws ServletException {
      log.info("Starting web application configuration, using profiles: {}", Arrays.toString(env.getActiveProfiles()));
      EnumSet<DispatcherType> disps = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC);
      initCORSFilter(servletContext, disps);
      log.info("Web application fully configured");
   }

   @Bean
   public WebMvcConfigurer corsConfigurer() {
      return new WebMvcConfigurer() {
         @Override
         public void addCorsMappings(CorsRegistry registry) {
            if (enableCorsPolicy) {
               registry.addMapping("/rest/**")
                     .allowedMethods("POST", "PUT", "GET", "OPTIONS", "DELETE", "PATCH")
                     .allowedOrigins("*");
            }
         }
      };
   }

   /** */
   private void initCORSFilter(ServletContext servletContext, EnumSet<DispatcherType> disps) {
      FilterRegistration.Dynamic corsFilter = servletContext.addFilter("corsFilter", new CorsFilter());
      corsFilter.addMappingForUrlPatterns(disps, true, "/api/*");
      corsFilter.addMappingForUrlPatterns(disps, true, "/dynarest/*");
      corsFilter.setAsyncSupported(true);
   }
}
