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
package io.microcks.service.arazzo;

import io.microcks.artifact.jsonschema.JsonSchemaValidator;
import io.microcks.util.ObjectMapperFactory;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The published JSON schemas of the ArazzoMetadata and ArazzoExamples kinds describe the artifacts Microcks imports.
 *
 * <pre>
 * Feature: Schemas of the ArazzoMetadata and ArazzoExamples kinds
 *   Scenario: The fixtures that Microcks imports are valid against the schema of their kind
 *   Scenario: An artifact that names no document, or expects a range of statuses, is invalid against the schema
 * </pre>
 * @author SebastienDegodez
 */
class ArazzoKindSchemasTest {

   private static final Path SCHEMAS = Path.of("..", "api");
   private static final Path FIXTURES = Path.of("target", "test-classes", "io", "microcks", "service", "arazzo");

   @ParameterizedTest
   @CsvSource({ "ArazzoMetadata, pastry.arazzo-metadata.yaml,",
         "ArazzoMetadata, order-fulfilment.arazzo-metadata.yaml,",
         "ArazzoMetadata, async-steps.arazzo-metadata.yaml,",
         "ArazzoExamples, recorded-examples.arazzo-examples.yaml,",
         "ArazzoExamples, examples.arazzo-examples.yaml, recorded",
         "ArazzoExamples, examples.arazzo-examples.yaml, status-bounds" })
   void theFixturesMicrocksImportsAreValidAgainstTheSchemaOfTheirKind(String kind, String fixture, String example)
         throws Exception {
      for (JsonNode artifact : artifacts(fixture, example)) {
         assertEquals(List.of(), JsonSchemaValidator.validateJson(schema(kind), artifact), fixture);
      }
   }

   @ParameterizedTest
   @CsvSource({ "ArazzoMetadata, invalid.arazzo-metadata.yaml, no-metadata",
         "ArazzoMetadata, invalid.arazzo-metadata.yaml, no-metadata-name",
         "ArazzoExamples, examples.arazzo-examples.yaml, status-range",
         "ArazzoExamples, examples.arazzo-examples.yaml, status-below-range" })
   void anArtifactThatNamesNoDocumentOrExpectsARangeOfStatusesIsInvalid(String kind, String fixture, String example)
         throws Exception {
      JsonNode artifact = artifacts(fixture, example).get(0);

      assertFalse(JsonSchemaValidator.validateJson(schema(kind), artifact).isEmpty());
   }

   private static JsonNode schema(String kind) throws IOException {
      Path schema = SCHEMAS.resolve(kind + "-v1alpha1-schema.json");
      return ObjectMapperFactory.getYamlObjectMapper().readTree(schema.toFile());
   }

   /** The artifacts of a fixture, one per YAML document; only the one marked by the example when there is one. */
   private static List<JsonNode> artifacts(String fixture, String example) throws IOException {
      List<JsonNode> artifacts = new ArrayList<>();
      for (String document : Files.readString(FIXTURES.resolve(fixture)).split("\n---\n")) {
         if (example == null || document.contains("# example: " + example + " ")) {
            artifacts.add(ObjectMapperFactory.getYamlObjectMapper().readTree(document));
         }
      }
      return artifacts;
   }
}
