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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * A Rest controller for getting optional and additional features configuration as JSON.
 * @author laurent
 */
@RestController
@RequestMapping("/api/features")
@PropertySources({ @PropertySource("features.properties"),
      @PropertySource(value = "file:/deployments/config/features.properties", ignoreResourceNotFound = true) })
@ConfigurationProperties("features")
public class FeaturesConfigController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(FeaturesConfigController.class);


   private Map<String, Map<String, String>> feature;

   public Map<String, Map<String, String>> getFeature() {
      return feature;
   }

   public void setFeature(Map<String, Map<String, String>> feature) {
      this.feature = feature;
   }

   @RequestMapping(value = "/config", method = RequestMethod.GET)
   public ResponseEntity<?> getConfig() {
      return new ResponseEntity<>(feature, HttpStatus.OK);
   }
}
