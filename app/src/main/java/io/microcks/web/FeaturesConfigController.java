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

import io.smallrye.common.annotation.RunOnVirtualThread;
import jakarta.ws.rs.GET;
import io.microcks.util.SafeLogger;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

/**
 * A Rest controller for getting optional and additional features configuration as JSON.
 * @author laurent
 */
@Path("/api/features")
@Produces(MediaType.APPLICATION_JSON)
@RunOnVirtualThread
public class FeaturesConfigController {

   private static final SafeLogger log = SafeLogger.getLogger(FeaturesConfigController.class);

   private static final String FEATURES_PREFIX = "features.feature.";

   private final Map<String, Map<String, String>> feature = new LinkedHashMap<>();

   /** Load features from the packaged config/features.properties then the optional external override file. */
   public FeaturesConfigController() {
      Properties props = new Properties();
      try (InputStream is = getClass().getClassLoader().getResourceAsStream("config/features.properties")) {
         if (is != null) {
            props.load(is);
         }
      } catch (IOException ioe) {
         log.warn("Unable to load packaged features.properties", ioe);
      }
      java.nio.file.Path external = Paths.get("/deployments/config/features.properties");
      if (Files.isReadable(external)) {
         try (InputStream is = Files.newInputStream(external)) {
            props.load(is);
         } catch (IOException ioe) {
            log.warn("Unable to load external features.properties", ioe);
         }
      }
      for (String key : new TreeSet<>(props.stringPropertyNames())) {
         if (key.startsWith(FEATURES_PREFIX)) {
            String rest = key.substring(FEATURES_PREFIX.length());
            int dot = rest.indexOf('.');
            if (dot > 0) {
               feature.computeIfAbsent(rest.substring(0, dot), k -> new LinkedHashMap<>()).put(rest.substring(dot + 1),
                     props.getProperty(key));
            }
         }
      }
   }

   public Map<String, Map<String, String>> getFeature() {
      return feature;
   }

   @GET
   @jakarta.ws.rs.Path("/config")
   public Response getConfig() {
      return Response.status(Response.Status.OK).entity(feature).build();
   }
}
