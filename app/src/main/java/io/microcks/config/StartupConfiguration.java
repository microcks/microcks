/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.microcks.config;

import io.microcks.util.ObjectMapperFactory;
import io.microcks.util.UsernamePasswordProxyAuthenticator;

import graphql.parser.ParserOptions;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.Authenticator;
import java.util.Optional;

/**
 * Applies at startup the global settings that cannot be expressed with Quarkus extensions: maximum parsed file size,
 * GraphQL parser limits and the JVM-wide HTTP(S) proxy.
 * @author laurent
 */
@ApplicationScoped
public class StartupConfiguration {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(StartupConfiguration.class);

   @ConfigProperty(name = "quarkus.http.limits.max-body-size", defaultValue = "2M")
   String maxUploadedFileSize;

   @ConfigProperty(name = "graphql.parser.max-characters")
   Optional<Integer> maxCharacters;

   @ConfigProperty(name = "graphql.parser.max-tokens")
   Optional<Integer> maxTokens;

   @ConfigProperty(name = "network.proxyHost")
   Optional<String> proxyHost;

   @ConfigProperty(name = "network.proxyPort")
   Optional<Integer> proxyPort;

   @ConfigProperty(name = "network.proxyUsername")
   Optional<String> proxyUsername;

   @ConfigProperty(name = "network.proxyPassword")
   Optional<String> proxyPassword;

   @ConfigProperty(name = "network.nonProxyHosts", defaultValue = "localhost|127.0.0.1|*.svc.cluster.local")
   String nonProxyHosts;

   /**
    * Apply the settings when the application starts.
    * @param event The Quarkus startup event
    */
   void onStart(@Observes StartupEvent event) {
      ObjectMapperFactory.configureMaxFileSize(maxUploadedFileSize);
      configureGraphQLParser();
      configureProxy();
   }

   private void configureGraphQLParser() {
      // Override the default ParserOptions with the ones defined in application properties.
      maxCharacters.ifPresent(max -> {
         log.info("Setting GraphQLParser maxChart to: {}", max);
         ParserOptions.setDefaultParserOptions(
               ParserOptions.getDefaultParserOptions().transform(opts -> opts.maxCharacters(max)));
      });
      maxTokens.ifPresent(max -> {
         log.info("Setting GraphQLParser maxTokens to: {}", max);
         ParserOptions
               .setDefaultParserOptions(ParserOptions.getDefaultParserOptions().transform(opts -> opts.maxTokens(max)));
      });
   }

   private void configureProxy() {
      if (proxyHost.isEmpty() || proxyPort.isEmpty()) {
         return;
      }
      String host = proxyHost.get();
      String port = proxyPort.get().toString();
      log.info("Configuring HTTP(S) proxy to {}:{}", host, port);
      System.setProperty("http.proxyHost", host);
      System.setProperty("http.proxyPort", port);
      System.setProperty("http.nonProxyHosts", nonProxyHosts);
      System.setProperty("https.proxyHost", host);
      System.setProperty("https.proxyPort", port);
      System.setProperty("https.nonProxyHosts", nonProxyHosts);

      // Basic authentication over HTTPS tunneling needs the disabled schemes to be cleared.
      if (proxyUsername.isPresent() && proxyPassword.isPresent()) {
         System.setProperty("jdk.http.auth.tunneling.disabledSchemes", "");
         System.setProperty("jdk.http.auth.proxying.disabledSchemes", "");
      }

      // Build settings and set Authenticator for the whole JVM.
      ProxySettings settings = new ProxySettings(host, proxyPort.get(), proxyUsername.orElse(null),
            proxyPassword.orElse(null));
      Authenticator.setDefault(new UsernamePasswordProxyAuthenticator(settings));
   }
}
