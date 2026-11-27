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
package io.microcks.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.smallrye.common.annotation.RunOnVirtualThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * A Rest controller for dispatching Microcks version information to frontend.
 * @author laurent
 */
@Path("/api/version")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class VersionInfoController {

   /** A simple logger for diagnostic messages. */
   private static Logger log = LoggerFactory.getLogger(VersionInfoController.class);

   private String versionId = null;
   private String buildTimestamp = null;

   /** Load version information from the packaged config/version.properties. */
   public VersionInfoController() {
      Properties props = new Properties();
      try (InputStream is = getClass().getClassLoader().getResourceAsStream("config/version.properties")) {
         if (is != null) {
            props.load(is);
         }
      } catch (IOException ioe) {
         log.warn("Unable to load version.properties", ioe);
      }
      versionId = props.getProperty("versionId");
      buildTimestamp = props.getProperty("buildTimestamp");
   }

   @GET
   @Path("/info")
   public Response getConfig() {
      final VersionInfo info = new VersionInfo(versionId, buildTimestamp);

      log.debug("Returning '{}' version information", info.getVersionId());

      return Response.status(Response.Status.OK).entity(info).build();
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
