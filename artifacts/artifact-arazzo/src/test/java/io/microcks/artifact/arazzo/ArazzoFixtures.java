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
package io.microcks.artifact.arazzo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Reads the examples of the Arazzo fixtures: one fixture per category, holding one YAML document per example, each
 * announced by a {@code # example: <name> - <what>} comment.
 * @author SebastienDegodez
 */
final class ArazzoFixtures {

   private ArazzoFixtures() {
   }

   /**
    * @param fixture The fixture of a category
    * @param example The name of the example
    * @return The parsed document of that example
    */
   static JsonNode exampleOf(String fixture, String example) throws IOException {
      try (InputStream stream = ArazzoFixtures.class
            .getResourceAsStream("/io/microcks/artifact/arazzo/" + fixture)) {
         String marker = "# example: " + example + " ";
         String document = Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(d -> d.contains(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fixture));
         return new ObjectMapper(new YAMLFactory()).readTree(document);
      }
   }
}
