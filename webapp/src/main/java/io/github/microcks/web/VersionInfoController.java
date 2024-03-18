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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A Rest controller for dispatching Microcks version information to frontend.
 * @author laurent
 */
@RestController
@RequestMapping("/api/version")
@PropertySource("classpath:version.properties")
public class VersionInfoController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(VersionInfoController.class);

   @Value("${versionId}")
   private String versionId = null;

   @Value("${buildTimestamp}")
   private String buildTimestamp = null;

   @GetMapping(value = "/info")
   public ResponseEntity<VersionInfo> getConfig() {
      final VersionInfo info = new VersionInfo(versionId, buildTimestamp);

      log.debug("Returning '{}' version information", info.getVersionId());

      return new ResponseEntity<>(info, HttpStatus.OK);
   }

   private class VersionInfo {
      @JsonProperty("versionId")
      private String versionId;

      @JsonProperty("buildTimestamp")
      private String buildTimestamp;

      public VersionInfo(String versionId, String buildTimestamp) {
         this.versionId = versionId;
         this.buildTimestamp = buildTimestamp;
      }

      public String getVersionId() {
         return versionId;
      }

      public String getBuildTimestamp() {
         return buildTimestamp;
      }
   }
}
