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
package io.github.microcks.util.graphql;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for GraphQLSchemaValidator utility.
 * @author laurent
 */
class GraphQLSchemaValidatorTest {

   @Test
   void testBuildResponseJsonSchema() {
      String schemaText;
      String queryText = """
            {
               hero {
                  name
                  email
                  family
                  affiliate
                  movies {
                     title
                  }
               }
            }
            """;

      JsonNode responseSchema = null;
      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/graphql/basic-heroes.graphql"),
               StandardCharsets.UTF_8);
         // Build JsonSchema for response.
         responseSchema = GraphQLSchemaValidator.buildResponseJsonSchema(schemaText, queryText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      assertFalse(
            responseSchema.path("properties").path("data").path("properties").path("hero") instanceof MissingNode);

      ObjectNode heroNode = (ObjectNode) responseSchema.path("properties").path("data").path("properties").path("hero");
      assertEquals("object", heroNode.get("type").asText());
      assertEquals(JsonNodeType.OBJECT, heroNode.get("properties").getNodeType());
      assertEquals(JsonNodeType.ARRAY, heroNode.get("required").getNodeType());
      assertEquals(JsonNodeType.BOOLEAN, heroNode.get("additionalProperties").getNodeType());

      ArrayNode requiredHero = (ArrayNode) heroNode.get("required");
      assertEquals(5, requiredHero.size());
      Iterator<JsonNode> requiredHeroElements = requiredHero.elements();
      while (requiredHeroElements.hasNext()) {
         String requiredHeroField = requiredHeroElements.next().asText();
         assertTrue("name".equals(requiredHeroField) || "email".equals(requiredHeroField)
               || "family".equals(requiredHeroField) || "affiliate".equals(requiredHeroField)
               || "movies".equals(requiredHeroField));
      }

      ObjectNode moviesNode = (ObjectNode) heroNode.path("properties").path("movies");
      assertEquals("array", moviesNode.get("type").asText());
      assertEquals(JsonNodeType.OBJECT, moviesNode.get("items").getNodeType());

      ObjectNode movieItemsNode = (ObjectNode) moviesNode.get("items");
      assertEquals("object", movieItemsNode.get("type").asText());
      assertEquals(JsonNodeType.OBJECT, movieItemsNode.get("properties").getNodeType());
      assertEquals(JsonNodeType.ARRAY, movieItemsNode.get("required").getNodeType());
      assertEquals(JsonNodeType.BOOLEAN, movieItemsNode.get("additionalProperties").getNodeType());

      ArrayNode requiredMovie = (ArrayNode) movieItemsNode.get("required");
      assertEquals(1, requiredMovie.size());
   }

   @Test
   void testValidateJson() {
      String schemaText;
      String queryText = """
            {
               hero {
                  name
                  email
                  family
                  affiliate
                  movies {
                     title
                  }
               }
            }
            """;
      String responseText = """
            {
               "data": {
                  "hero": {
                     "name": "Iron Man",
                     "email": "tony@stark.inc",
                     "family": "MARVEL",
                     "affiliate": "DC",
                     "movies": [
                        {"title": "Iron Man 1"},
                        {"title": "Iron Man 2"},
                        {"title": "Iron Man 3"}
                     ]
                  }
               }
            }
            """;
      String badResponseText = """
            {
               "data": {
                  "hero": {
                     "name": "Iron Man",
                     "family": "MARVEL",
                     "affiliate": "DC",
                     "movies": [
                        {"title": "Iron Man 1"},
                        {"title": "Iron Man 2"},
                        {"title": "Iron Man 3"}
                     ]
                  }
               }
            }
            """;

      ObjectMapper mapper = new ObjectMapper();

      JsonNode responseSchema = null;
      List<String> validationErrors = null;
      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/graphql/basic-heroes.graphql"),
               StandardCharsets.UTF_8);
         // Build JsonSchema for response.
         responseSchema = GraphQLSchemaValidator.buildResponseJsonSchema(schemaText, queryText);
         // Validate a correct response.
         validationErrors = GraphQLSchemaValidator.validateJson(responseSchema, mapper.readTree(responseText));
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      assertTrue(validationErrors.isEmpty());

      try {
         // Validate a bad response with missing email.
         validationErrors = GraphQLSchemaValidator.validateJson(responseSchema, mapper.readTree(badResponseText));
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, validationErrors.size());
      assertEquals("required property 'email' not found", validationErrors.get(0));
   }

   @Test
   void testValidateJsonAdvanced() {
      String schemaText;
      String queryText = """
             query allFilms {
               allFilms {
                  films {
                     id
                     title
                     episodeID
                     director
                     starCount
                     rating
                  }
               }
             }
            """;
      String responseText = """
            {
               "data": {
                  "allFilms": {
                     "films": [
                        {
                           "id": "ZmlsbXM6MQ==",
                           "title": "A New Hope",
                           "episodeID": 4,
                           "director": "George Lucas",
                           "starCount": 432,
                           "rating": 4.3
                        },
                        {
                           "id": "ZmlsbXM6Mg==",
                           "title": "The Empire Strikes Back",
                           "episodeID": 5,
                           "director": "Irvin Kershner",
                           "starCount": 433,
                           "rating": 4.3
                        }
                     ]
                  }
               }
            }
            """;

      ObjectMapper mapper = new ObjectMapper();

      JsonNode responseSchema = null;
      List<String> validationErrors = null;
      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/graphql/films.graphql"), StandardCharsets.UTF_8);
         // Build JsonSchema for response.
         responseSchema = GraphQLSchemaValidator.buildResponseJsonSchema(schemaText, queryText);

         mapper.enable(SerializationFeature.INDENT_OUTPUT);
         System.err.println(mapper.writeValueAsString(responseSchema));

         // Validate a correct response.
         validationErrors = GraphQLSchemaValidator.validateJson(responseSchema, mapper.readTree(responseText));
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      assertTrue(validationErrors.isEmpty());
   }
}
