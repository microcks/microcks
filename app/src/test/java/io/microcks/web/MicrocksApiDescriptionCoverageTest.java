/*
 * Copyright The Microcks Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.microcks.web;

import io.microcks.artifact.openapi.OpenAPISchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.HttpMethod;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guard rail keeping the description of the Microcks API in <code>api/microcks-openapi-v2.0.yaml</code> complete.
 *
 * <pre>
 * Scenario: Every endpoint under /api is described
 *   Given the JAX-RS resources of the application
 *   When their endpoints under /api are compared with the paths of the description
 *   Then each endpoint (verb and normalized path) is described, unless it is deliberately excluded below
 *
 * Scenario: The exclusions stay honest
 *   Given the list of deliberately undescribed endpoints
 *   When one of them is no longer an endpoint of the application or becomes described
 *   Then the test fails so the list is cleaned
 * </pre>
 * @author SebastienDegodez
 */
class MicrocksApiDescriptionCoverageTest {

   private static final String API_PREFIX = "/api";

   /**
    * Endpoints deliberately left out of the public description, with the reason why. Keys are "VERB /path" with the
    * path as the description writes it (under /api) and path parameters normalized to {}.
    */
   private static final Map<String, String> EXCLUDED = Map.of();

   @Test
   void everyEndpointUnderApiIsDescribedInTheOpenApiDescription() throws Exception {
      Set<String> implemented = implementedEndpoints();
      Set<String> described = describedEndpoints();

      Set<String> undescribed = new TreeSet<>(implemented);
      undescribed.removeAll(described);
      undescribed.removeAll(EXCLUDED.keySet());
      assertEquals(Set.of(), undescribed, "Endpoints missing from api/microcks-openapi-v2.0.yaml");

      Set<String> staleExclusions = new TreeSet<>(EXCLUDED.keySet());
      staleExclusions.removeIf(implemented::contains);
      staleExclusions.addAll(intersection(EXCLUDED.keySet(), described));
      assertEquals(Set.of(), staleExclusions, "Exclusions that are not endpoints anymore or are now described");
   }

   @Test
   void everyDescribedEndpointStillExistsInTheApplication() throws Exception {
      Set<String> obsolete = new TreeSet<>(describedEndpoints());
      obsolete.removeAll(implementedEndpoints());
      assertEquals(Set.of(), obsolete, "Endpoints described in api/microcks-openapi-v2.0.yaml but not implemented");
   }

   private static Set<String> intersection(Set<String> left, Set<String> right) {
      Set<String> result = new TreeSet<>(left);
      result.retainAll(right);
      return result;
   }

   /** The "VERB /path" of the described operations, normalized. */
   private static Set<String> describedEndpoints() throws IOException {
      JsonNode description = OpenAPISchemaValidator
            .getJsonNodeForSchema(Files.readString(Path.of("..", "api", "microcks-openapi-v2.0.yaml")));
      Set<String> endpoints = new TreeSet<>();
      Iterator<Map.Entry<String, JsonNode>> paths = description.path("paths").fields();
      while (paths.hasNext()) {
         Map.Entry<String, JsonNode> path = paths.next();
         Iterator<String> verbs = path.getValue().fieldNames();
         while (verbs.hasNext()) {
            String verb = verbs.next().toUpperCase(Locale.ROOT);
            if (Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS").contains(verb)) {
               endpoints.add(verb + " " + normalize(path.getKey()));
            }
         }
      }
      return endpoints;
   }

   /** The "VERB /path" (path under /api, normalized) of the JAX-RS resource methods serving /api. */
   private static Set<String> implementedEndpoints() throws IOException, URISyntaxException, ClassNotFoundException {
      Set<String> endpoints = new TreeSet<>();
      // Scan the main classes of the web package (not the test classes sharing the package name).
      Path webClasses = Path.of(ServiceController.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            .resolve(ServiceController.class.getPackageName().replace('.', '/'));
      Set<Path> classFiles;
      try (Stream<Path> files = Files.walk(webClasses)) {
         classFiles = new TreeSet<>(files.filter(f -> f.toString().endsWith(".class")).toList());
      }
      for (Path classFile : classFiles) {
         String relative = webClasses.relativize(classFile).toString();
         String name = ServiceController.class.getPackageName() + "."
               + relative.substring(0, relative.length() - ".class".length()).replace('/', '.');
         Class<?> resource = Class.forName(name, false, ServiceController.class.getClassLoader());
         jakarta.ws.rs.Path classPath = resource.getAnnotation(jakarta.ws.rs.Path.class);
         if (classPath == null
               || !(classPath.value().equals(API_PREFIX) || classPath.value().startsWith(API_PREFIX + "/"))) {
            continue;
         }
         for (Method method : resource.getDeclaredMethods()) {
            String verb = httpMethod(method);
            if (verb == null) {
               continue;
            }
            jakarta.ws.rs.Path methodPath = method.getAnnotation(jakarta.ws.rs.Path.class);
            String full = normalize(classPath.value() + "/" + (methodPath != null ? methodPath.value() : ""));
            endpoints.add(verb + " " + full.substring(API_PREFIX.length()));
         }
      }
      return endpoints;
   }

   private static String httpMethod(Method method) {
      for (Annotation annotation : method.getAnnotations()) {
         HttpMethod httpMethod = annotation.annotationType().getAnnotation(HttpMethod.class);
         if (httpMethod != null) {
            return httpMethod.value();
         }
      }
      return null;
   }

   /** Normalize a path: single slashes, no trailing slash, every {parameter[:regex]} written as {}. */
   private static String normalize(String path) {
      String normalized = ("/" + path).replaceAll("\\{[^}]*}", "{}").replaceAll("/+", "/");
      return normalized.length() > 1 && normalized.endsWith("/")
            ? normalized.substring(0, normalized.length() - 1)
            : normalized;
   }
}
