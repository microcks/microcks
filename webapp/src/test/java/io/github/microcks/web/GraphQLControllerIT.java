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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.Customization;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.skyscreamer.jsonassert.RegularExpressionValueMatcher;
import org.skyscreamer.jsonassert.comparator.CustomComparator;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test case for all the GraphQL mock controller.
 * @author laurent
 */
class GraphQLControllerIT extends AbstractBaseIT {

   @Test
   void testFilmsGraphQLAPIMocking() {
      // Upload the 2 required reference artifacts.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-postman.json", false);
      // Adding some more metadata for mutation dispatching.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-metadata.yml", false);

      // Check its different mocked operations.
      String query = "{\"query\": \"query allFilms {\\n" + "  allFilms {\\n" + "    films {\\n" + "      id\\n"
            + "      title\\n" + "    }\\n" + "  }\\n" + "}\"}";
      ResponseEntity<String> response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\n" + "  \"data\": {\n" + "    \"allFilms\": {\n" + "      \"films\": [\n"
               + "        {\n" + "          \"id\": \"ZmlsbXM6MQ==\",\n" + "          \"title\": \"A New Hope\"\n"
               + "        },\n" + "        {\n" + "          \"id\": \"ZmlsbXM6Mg==\",\n"
               + "          \"title\": \"The Empire Strikes Back\"\n" + "        },\n" + "        {\n"
               + "          \"id\": \"ZmlsbXM6Mw==\",\n" + "          \"title\": \"Return of the Jedi\"\n"
               + "        },\n" + "        {\n" + "          \"id\": \"ZmlsbXM6NA==\",\n"
               + "          \"title\": \"The Phantom Menace\"\n" + "        },\n" + "        {\n"
               + "          \"id\": \"ZmlsbXM6NQ==\",\n" + "          \"title\": \"Attack of the Clones\"\n"
               + "        },\n" + "        {\n" + "          \"id\": \"ZmlsbXM6Ng==\",\n"
               + "          \"title\": \"Revenge of the Sith\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  }\n"
               + "}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with inlined argument.
      query = "{\"query\": \"query film($id: String) {\\n" + "  film(id: \\\"ZmlsbXM6MQ==\\\") {\\n" + "    id\\n"
            + "    title\\n" + "    episodeID\\n" + "    starCount\\n" + "  }\\n" + "}\"}";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\n" + "  \"data\": {\n" + "    \"film\": {\n" + "      \"id\": \"ZmlsbXM6MQ==\",\n"
               + "      \"title\": \"A New Hope\",\n" + "      \"episodeID\": 4,\n" + "      \"starCount\": 432\n"
               + "    }\n" + "  }\n" + "}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with variable argument.
      query = "{\"query\": \"query film($id: String) {\\n" + "  film(id: $id) {\\n" + "    id\\n" + "    title\\n"
            + "    episodeID\\n" + "    starCount\\n" + "  }\\n" + "}\", \"variables\": {\"id\": \"ZmlsbXM6MQ==\"}"
            + "}";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\n" + "  \"data\": {\n" + "    \"film\": {\n" + "      \"id\": \"ZmlsbXM6MQ==\",\n"
               + "      \"title\": \"A New Hope\",\n" + "      \"episodeID\": 4,\n" + "      \"starCount\": 432\n"
               + "    }\n" + "  }\n" + "}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with fragment definition.
      query = "{\"query\": \"query film($id: String) {\\n" + "  film(id: \\\"ZmlsbXM6MQ==\\\") {\\n"
            + "    ...filmFields\\n" + "  }\\n" + "  }\\n" + "  fragment filmFields on Film {\\n" + "    id\\n"
            + "    title\\n" + "    episodeID\\n" + "    starCount\\n" + "  }\\n" + "\"}";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\n" + "  \"data\": {\n" + "    \"film\": {\n" + "      \"id\": \"ZmlsbXM6MQ==\",\n"
               + "      \"title\": \"A New Hope\",\n" + "      \"episodeID\": 4,\n" + "      \"starCount\": 432\n"
               + "    }\n" + "  }\n" + "}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with multiple selection and aliases
      query = "{\"query\": \"{\\n" + "  film_one: film(id: \\\"ZmlsbXM6MQ==\\\") {\\n" + "    id\\n" + "    title\\n"
            + "    episodeID\\n" + "    rating\\n" + "  }\\n" + "  film_two: film(id: \\\"ZmlsbXM6Mg==\\\") {\\n"
            + "    id\\n" + "    title\\n" + "    episodeID\\n" + "    director\\n" + "    starCount\\n" + "  }\\n"
            + "}\"}";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\n" + "  \"data\": {\n" + "    \"film_one\": {\n"
               + "      \"id\": \"ZmlsbXM6MQ==\",\n" + "      \"title\": \"A New Hope\",\n"
               + "      \"episodeID\": 4,\n" + "      \"rating\": 4.3\n" + "    },\n" + "    \"film_two\": {\n"
               + "      \"id\": \"ZmlsbXM6Mg==\",\n" + "      \"title\": \"The Empire Strikes Back\",\n"
               + "      \"episodeID\": 5,\n" + "      \"director\": \"Irvin Kershner\",\n"
               + "      \"starCount\": 433\n" + "    }\n" + "  }\n" + "}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with multiple selection no aliases
      query = "{\"query\": \"{\\n" + "  film(id: \\\"ZmlsbXM6MQ==\\\") {\\n" + "    id\\n" + "    title\\n"
            + "    episodeID\\n" + "    rating\\n" + "  }\\n" + "  allFilms {\\n" + "    films {\\n" + "      id\\n"
            + "      title\\n" + "    }\\n" + "  }\\n" + "}\"}";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("{\n" + "  \"data\": {\n" + "    \"film\": {\n" + "      \"id\": \"ZmlsbXM6MQ==\",\n"
               + "      \"title\": \"A New Hope\",\n" + "      \"episodeID\": 4,\n" + "      \"rating\": 4.3\n"
               + "    },\n" + "    \"allFilms\": {\n" + "      \"films\": [\n" + "        {\n"
               + "          \"id\": \"ZmlsbXM6MQ==\",\n" + "          \"title\": \"A New Hope\"\n" + "        },\n"
               + "        {\n" + "          \"id\": \"ZmlsbXM6Mg==\",\n"
               + "          \"title\": \"The Empire Strikes Back\"\n" + "        },\n" + "        {\n"
               + "          \"id\": \"ZmlsbXM6Mw==\",\n" + "          \"title\": \"Return of the Jedi\"\n"
               + "        },\n" + "        {\n" + "          \"id\": \"ZmlsbXM6NA==\",\n"
               + "          \"title\": \"The Phantom Menace\"\n" + "        },\n" + "        {\n"
               + "          \"id\": \"ZmlsbXM6NQ==\",\n" + "          \"title\": \"Attack of the Clones\"\n"
               + "        },\n" + "        {\n" + "          \"id\": \"ZmlsbXM6Ng==\",\n"
               + "          \"title\": \"Revenge of the Sith\"\n" + "        }\n" + "      ]\n" + "    }\n" + "  }\n"
               + "}", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with __typename and inlined argument.
      query = "{\"query\": \"query film($id: String) {\\n" + "  __typename\\n  film(id: \\\"ZmlsbXM6MQ==\\\") {\\n"
            + "    id\\n" + "    title\\n" + "    episodeID\\n" + "    starCount\\n" + "  }\\n" + "}\"}";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals(
               "{\n" + "  \"data\": {\n" + "  \"__typename\":\"Query\", \"film\": {\n"
                     + "      \"id\": \"ZmlsbXM6MQ==\",\n" + "      \"title\": \"A New Hope\",\n"
                     + "      \"episodeID\": 4,\n" + "      \"starCount\": 432\n" + "    }\n" + "  }\n" + "}",
               response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   void testFilmsGraphQLAPIMockingErrorsAndExtensions() {
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-1.0-examples.yml", false);

      String query = "{\"query\": \"mutation AddStar($filmId: String) {\\n" + "  addStar(filmId: $filmId) {\\n"
            + "    id\\n" + "    title\\n" + "    episodeID\\n" + "    director\\n" + "    starCount\\n"
            + "    rating\\n" + "  }\\n" + "}\", \"variables\": {\"filmId\": \"notavailable\"}" + "}";

      ResponseEntity<String> response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertNotNull(response.getBody(), "Response body should not be null");
      assertTrue(response.getBody().contains("\"errors\""));
      assertTrue(response.getBody().contains("\"extensions\""));
      try {
         JSONAssert.assertEquals("{\n" + "  \"errors\": [\n" + "    {\n"
               + "      \"message\": \"The system is not available, due to maintenance, please try again later.\",\n"
               + "      \"extensions\": {\n" + "        \"code\": \"MAINTENANCE_MODE\"\n" + "      }\n" + "    }\n"
               + "  ],\n" + "  \"data\": {\n" + "    \"addStar\": null\n" + "  },\n" + "  \"extensions\": {\n"
               + "    \"correlationId\": {\n" + "      \"traceId\": \"123e4567-e89b-12d3-a456-426614174000\"\n"
               + "    }\n" + "  }\n" + "}", response.getBody(),
               new CustomComparator(JSONCompareMode.LENIENT,
                     new Customization("extensions.correlationId.traceId", new RegularExpressionValueMatcher<>(
                           "[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))));
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   void testProxy() {
      // Upload the required reference artifacts.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-postman.json", false);

      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-to-test-proxy.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-to-test-proxy-postman.json", false);

      // Override the dispatcher to PROXY
      Service service = serviceRepository.findByNameAndVersion("Movie Graph API", "1.0");
      Operation operation = service.getOperations().stream().filter((o) -> "film".equals(o.getName())).findFirst()
            .orElseThrow();
      operation.setDispatcher("PROXY");
      operation.setDispatcherRules(getServerUrl() + "/graphql/Movie+Graph+Original+API/1.0");
      serviceRepository.save(service);

      // Execute and assert that it was proxy.
      String query = """
            {"query": "query film($id: String) {film(id: \\"ZmlsbXM6Mg==\\") {id title episodeID starCount comment}}"}""";
      ResponseEntity<String> response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertResponseIsOkAndContains(response, "\"comment\":\"Original!!!\"");
   }

   @Test
   void testProxyFallback() {
      // Upload the required reference artifacts.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-postman.json", false);

      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-to-test-proxy.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-to-test-proxy-postman.json", false);

      // Override the dispatcher to PROXY
      Service service = serviceRepository.findByNameAndVersion("Movie Graph API", "1.0");
      Operation operation = service.getOperations().stream().filter((o) -> "film".equals(o.getName())).findFirst()
            .orElseThrow();
      operation.setDispatcher("PROXY_FALLBACK");
      operation.setDispatcherRules(String.format("""
            {"dispatcher": "QUERY_ARGS",
            "dispatcherRules": "id",
            "proxyUrl": "%s/graphql/Movie+Graph+Original+API/1.0"}""", getServerUrl()));
      serviceRepository.save(service);

      // Execute and assert that it wasn't proxy.
      String query = """
            {"query": "query film($id: String) {film(id: \\"ZmlsbXM6Mg==\\") {id title episodeID starCount comment}}"}""";
      ResponseEntity<String> response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      assertNotNull(response.getBody());
      assertFalse(response.getBody().contains("\"comment\":\"Original!!!\""));

      // Execute and assert that it was proxy.
      query = """
            {"query": "query film($id: String) {film(id: \\"ZmlsbXM6MA==\\") {id title episodeID starCount comment}}"}""";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertResponseIsOkAndContains(response, "\"comment\":\"Original!!!\"");
   }

   @Test
   void testGraphQLErrorAPIMocking() {
      // Upload the 2 required reference artifacts.
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-postman.json", false);

      ObjectMapper mapper = new ObjectMapper();

      // 1. Check mock response with "data": null and "errors" array
      String query = "{\"query\": \"query film($id: String) {\\n  film(id: \\\"ZmlsbXM6OTk=\\\") {\\n    id\\n    title\\n  }\\n}\"}";
      ResponseEntity<String> response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode responseJson = mapper.readTree(response.getBody());
         assertTrue(responseJson.has("data"), "Response should contain 'data' key");
         assertTrue(responseJson.get("data").isNull(), "Response 'data' should be null");
         assertTrue(responseJson.has("errors"), "Response should contain 'errors' key");

         JSONAssert.assertEquals("{\n" + "  \"data\": null,\n" + "  \"errors\": [\n" + "    {\n"
               + "      \"message\": \"Film not found\",\n" + "      \"path\": [\"film\"]\n" + "    }\n" + "  ]\n"
               + "}", response.getBody(), JSONCompareMode.STRICT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // 2. Check mock response with absent "data" key and "errors" array
      query = "{\"query\": \"query film($id: String) {\\n  film(id: \\\"ZmlsbXM6OTg=\\\") {\\n    id\\n    title\\n  }\\n}\"}";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode responseJson = mapper.readTree(response.getBody());
         assertFalse(responseJson.has("data"), "Response should not contain 'data' key");
         assertTrue(responseJson.has("errors"), "Response should contain 'errors' key");

         JSONAssert.assertEquals(
               "{\n" + "  \"errors\": [\n" + "    {\n" + "      \"message\": \"Access Denied\",\n"
                     + "      \"path\": [\"film\"]\n" + "    }\n" + "  ]\n" + "}",
               response.getBody(), JSONCompareMode.STRICT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // 3. Check mock response with "data": {"film": null} and "errors" array
      query = "{\"query\": \"query film($id: String) {\\n  film(id: \\\"ZmlsbXM6OTc=\\\") {\\n    id\\n    title\\n  }\\n}\"}";
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode responseJson = mapper.readTree(response.getBody());
         assertTrue(responseJson.has("data"), "Response should contain 'data' key");
         assertFalse(responseJson.get("data").isNull(), "Response 'data' should not be null");
         assertTrue(responseJson.get("data").has("film"), "Response 'data' should contain 'film' key");
         assertTrue(responseJson.get("data").get("film").isNull(), "Response 'data.film' should be null");
         assertTrue(responseJson.has("errors"), "Response should contain 'errors' key");

         JSONAssert.assertEquals("{\n" + "  \"data\": {\n" + "    \"film\": null\n" + "  },\n" + "  \"errors\": [\n"
               + "    {\n" + "      \"message\": \"Partial error\",\n" + "      \"path\": [\"film\"]\n" + "    }\n"
               + "  ]\n" + "}", response.getBody(), JSONCompareMode.STRICT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }
}
