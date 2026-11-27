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

import io.microcks.util.ai.AICopilot;
import io.microcks.util.ai.OpenAICopilot;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Application configuration in charge of building AI Copilot instance. A null implementation is produced when the
 * feature is disabled or misconfigured.
 * @author laurent
 */
@ApplicationScoped
public class AICopilotProducer {

   /** A simple logger for diagnostic messages. */
   private static final Logger log = LoggerFactory.getLogger(AICopilotProducer.class);

   private static final String OPENAI_PREFIX = "ai-copilot.openai.";

   @ConfigProperty(name = "ai-copilot.enabled", defaultValue = "false")
   boolean enabled;

   @ConfigProperty(name = "ai-copilot.implementation")
   Optional<String> implementation;

   /**
    * Build and configure a suitable AICopilot based on application properties.
    * @param config The application configuration
    * @return An AICopilot implementation configured as specified, or null if disabled.
    */
   @Produces
   @jakarta.enterprise.context.Dependent
   public AICopilot aiCopilotImplementation(Config config) {
      if (enabled && implementation.isPresent()) {
         log.info("AICopilot is enabled with implementation '{}'", implementation.get());

         if ("openai".equals(implementation.get())) {
            Map<String, String> openai = new HashMap<>();
            for (String name : config.getPropertyNames()) {
               if (name.startsWith(OPENAI_PREFIX)) {
                  config.getOptionalValue(name, String.class)
                        .ifPresent(value -> openai.put(name.substring(OPENAI_PREFIX.length()), value));
               }
            }
            // Check the presence of mandatory keys.
            if (Arrays.stream(OpenAICopilot.getMandatoryConfigKeys()).allMatch(openai::containsKey)) {
               return new OpenAICopilot(openai);
            }
            log.warn("At least one mandatory configuration is missing for OpenAI AICopilot implementation");
            log.warn("Mandatory configuration keys are: {}", Arrays.toString(OpenAICopilot.getMandatoryConfigKeys()));
         }
      }
      log.info("AICopilot is disabled");
      return null;
   }
}
