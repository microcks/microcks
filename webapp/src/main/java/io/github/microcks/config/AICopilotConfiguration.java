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

import io.github.microcks.util.ai.AICopilot;
import io.github.microcks.util.ai.OpenAICopilot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Map;

/**
 * Application configuration in charge of building AI Copilot instance.
 * @author laurent
 */
@Configuration
@ConfigurationProperties("ai-copilot")
public class AICopilotConfiguration {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(AICopilotConfiguration.class);

   private boolean enabled = false;

   private String implementation;

   private Map<String, String> openai;

   public void setEnabled(boolean enabled) {
      this.enabled = enabled;
   }

   public void setImplementation(String implementation) {
      this.implementation = implementation;
   }

   public void setOpenai(Map<String, String> openai) {
      this.openai = openai;
   }

   @Bean
   public AICopilot aiCopilotImplementation() {
      if (enabled && implementation != null) {
         log.info("AICopilot is enabled with implementation '{}'", implementation);

         if ("openai".equals(implementation)) {
            // Check the presence of mandatory keys.
            if (Arrays.stream(OpenAICopilot.getMandatoryConfigKeys()).allMatch(key -> openai.containsKey(key))) {
               return new OpenAICopilot(openai);
            } else {
               log.warn("At least one mandatory configuration is missing for OpenAI AICopilot implementation");
               log.warn("Mandatory configuration keys are: " + OpenAICopilot.getMandatoryConfigKeys());
            }
         }
      }
      log.info("AICopilot is disabled");
      return null;
   }
}
