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

      ObjectMapper mapper = new ObjectMapper();

      // Check its different mocked operations.
      String subQuery = """
            query allFilms {
              allFilms {
                films {
                  id
                  title
                }
              }
            }""";
      String query = mapper.createObjectNode().put("query", subQuery).toString();
      ResponseEntity<String> response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "allFilms": {
                     "films": [
                       { "id": "ZmlsbXM6MQ==", "title": "A New Hope" },
                       { "id": "ZmlsbXM6Mg==", "title": "The Empire Strikes Back" },
                       { "id": "ZmlsbXM6Mw==", "title": "Return of the Jedi" },
                       { "id": "ZmlsbXM6NA==", "title": "The Phantom Menace" },
                       { "id": "ZmlsbXM6NQ==", "title": "Attack of the Clones" },
                       { "id": "ZmlsbXM6Ng==", "title": "Revenge of the Sith" }
                     ]
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with inlined argument.
      subQuery = """
            query film($id: String) {
              film(id: "ZmlsbXM6MQ==") {
                id
                title
                episodeID
                starCount
              }
            }""";
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "film": {
                     "id": "ZmlsbXM6MQ==",
                     "title": "A New Hope",
                     "episodeID": 4,
                     "starCount": 432
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with variable argument.
      subQuery = """
            query film($id: String) {
              film(id: $id) {
                id
                title
                episodeID
                starCount
              }
            }""";
      var requestBody = mapper.createObjectNode().put("query", subQuery);
      requestBody.putObject("variables").put("id", "ZmlsbXM6MQ==");
      query = requestBody.toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "film": {
                     "id": "ZmlsbXM6MQ==",
                     "title": "A New Hope",
                     "episodeID": 4,
                     "starCount": 432
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with fragment definition.
      subQuery = """
            query film($id: String) {
              film(id: "ZmlsbXM6MQ==") {
                ...filmFields
              }
            }
            fragment filmFields on Film {
              id
              title
              episodeID
              starCount
            }""";
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "film": {
                     "id": "ZmlsbXM6MQ==",
                     "title": "A New Hope",
                     "episodeID": 4,
                     "starCount": 432
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with nested fragment definitions usage.
      subQuery = """
            query film($id: String) {
              film(id: "ZmlsbXM6MQ==") {
                ...FilmCoreFields
              }
            }
            fragment FilmCoreFields on Film {
              ...FilmIdentityFields
              episodeID
            }
            fragment FilmIdentityFields on Film {
              id
              title
            }
            """;
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "film": {
                     "id": "ZmlsbXM6MQ==",
                     "title": "A New Hope",
                     "episodeID": 4
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         e.printStackTrace();
         fail("No Exception should be thrown here");
      }

      // Check query with fragment definitions declared before the operation (Apollo/urql/Relay ordering, #2199).
      subQuery = """
            fragment filmFields on Film {
              id
              title
              episodeID
              starCount
            }
            query film($id: String) {
              film(id: "ZmlsbXM6MQ==") {
                ...filmFields
              }
            }""";
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "film": {
                     "id": "ZmlsbXM6MQ==",
                     "title": "A New Hope",
                     "episodeID": 4,
                     "starCount": 432
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with multiple selection and aliases
      subQuery = """
            {
              film_one: film(id: "ZmlsbXM6MQ==") {
                id
                title
                episodeID
                rating
              }
              film_two: film(id: "ZmlsbXM6Mg==") {
                id
                title
                episodeID
                director
                starCount
              }
            }""";
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "film_one": {
                     "id": "ZmlsbXM6MQ==",
                     "title": "A New Hope",
                     "episodeID": 4,
                     "rating": 4.3
                   },
                   "film_two": {
                     "id": "ZmlsbXM6Mg==",
                     "title": "The Empire Strikes Back",
                     "episodeID": 5,
                     "director": "Irvin Kershner",
                     "starCount": 433
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with multiple selection no aliases
      subQuery = """
            {
              film(id: "ZmlsbXM6MQ==") {
                id
                title
                episodeID
                rating
              }
              allFilms {
                films {
                  id
                  title
                }
              }
            }""";
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "film": {
                     "id": "ZmlsbXM6MQ==",
                     "title": "A New Hope",
                     "episodeID": 4,
                     "rating": 4.3
                   },
                   "allFilms": {
                     "films": [
                       { "id": "ZmlsbXM6MQ==", "title": "A New Hope" },
                       { "id": "ZmlsbXM6Mg==", "title": "The Empire Strikes Back" },
                       { "id": "ZmlsbXM6Mw==", "title": "Return of the Jedi" },
                       { "id": "ZmlsbXM6NA==", "title": "The Phantom Menace" },
                       { "id": "ZmlsbXM6NQ==", "title": "Attack of the Clones" },
                       { "id": "ZmlsbXM6Ng==", "title": "Revenge of the Sith" }
                     ]
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // Check query with __typename and inlined argument.
      subQuery = """
            query film($id: String) {
              __typename
              film(id: "ZmlsbXM6MQ==") {
                id
                title
                episodeID
                starCount
              }
            }""";
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JSONAssert.assertEquals("""
               {
                 "data": {
                   "__typename": "Query",
                   "film": {
                     "id": "ZmlsbXM6MQ==",
                     "title": "A New Hope",
                     "episodeID": 4,
                     "starCount": 432
                   }
                 }
               }""", response.getBody(), JSONCompareMode.LENIENT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }

   @Test
   void testFilmsGraphQLAPIMockingErrorsAndExtensions() {
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films.graphql", true);
      uploadArtifactFile("target/test-classes/io/github/microcks/util/graphql/films-1.0-examples.yml", false);

      ObjectMapper mapper = new ObjectMapper();
      String subQuery = """
            mutation AddStar($filmId: String) {
              addStar(filmId: $filmId) {
                id
                title
                episodeID
                director
                starCount
                rating
              }
            }""";
      var requestBody = mapper.createObjectNode().put("query", subQuery);
      requestBody.putObject("variables").put("filmId", "notavailable");
      String query = requestBody.toString();

      ResponseEntity<String> response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertNotNull(response.getBody(), "Response body should not be null");
      assertTrue(response.getBody().contains("\"errors\""));
      assertTrue(response.getBody().contains("\"extensions\""));
      try {
         JSONAssert.assertEquals("""
               {
                 "errors": [
                   {
                     "message": "The system is not available, due to maintenance, please try again later.",
                     "extensions": {
                       "code": "MAINTENANCE_MODE"
                     }
                   }
                 ],
                 "data": {
                   "addStar": null
                 },
                 "extensions": {
                   "correlationId": {
                     "traceId": "123e4567-e89b-12d3-a456-426614174000"
                   }
                 }
               }""", response.getBody(), new CustomComparator(JSONCompareMode.LENIENT, new Customization(
               "extensions.correlationId.traceId",
               new RegularExpressionValueMatcher<>("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}"))));
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
      String subQuery = """
            query film($id: String) {
              film(id: "ZmlsbXM6OTk=") {
                id
                title
              }
            }""";
      String query = mapper.createObjectNode().put("query", subQuery).toString();
      ResponseEntity<String> response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode responseJson = mapper.readTree(response.getBody());
         assertTrue(responseJson.has("data"), "Response should contain 'data' key");
         assertTrue(responseJson.get("data").isNull(), "Response 'data' should be null");
         assertTrue(responseJson.has("errors"), "Response should contain 'errors' key");

         JSONAssert.assertEquals("""
               {
                 "data": null,
                 "errors": [
                   {
                     "message": "Film not found",
                     "path": ["film"]
                   }
                 ]
               }""", response.getBody(), JSONCompareMode.STRICT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // 2. Check mock response with absent "data" key and "errors" array
      subQuery = """
            query film($id: String) {
              film(id: "ZmlsbXM6OTg=") {
                id
                title
              }
            }""";
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode responseJson = mapper.readTree(response.getBody());
         assertFalse(responseJson.has("data"), "Response should not contain 'data' key");
         assertTrue(responseJson.has("errors"), "Response should contain 'errors' key");

         JSONAssert.assertEquals("""
               {
                 "errors": [
                   {
                     "message": "Access Denied",
                     "path": ["film"]
                   }
                 ]
               }""", response.getBody(), JSONCompareMode.STRICT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }

      // 3. Check mock response with "data": {"film": null} and "errors" array
      subQuery = """
            query film($id: String) {
              film(id: "ZmlsbXM6OTc=") {
                id
                title
              }
            }""";
      query = mapper.createObjectNode().put("query", subQuery).toString();
      response = restTemplate.postForEntity("/graphql/Movie+Graph+API/1.0", query, String.class);
      assertEquals(200, response.getStatusCode().value());
      try {
         JsonNode responseJson = mapper.readTree(response.getBody());
         assertTrue(responseJson.has("data"), "Response should contain 'data' key");
         assertFalse(responseJson.get("data").isNull(), "Response 'data' should not be null");
         assertTrue(responseJson.get("data").has("film"), "Response 'data' should contain 'film' key");
         assertTrue(responseJson.get("data").get("film").isNull(), "Response 'data.film' should be null");
         assertTrue(responseJson.has("errors"), "Response should contain 'errors' key");

         JSONAssert.assertEquals("""
               {
                 "data": {
                   "film": null
                 },
                 "errors": [
                   {
                     "message": "Partial error",
                     "path": ["film"]
                   }
                 ]
               }""", response.getBody(), JSONCompareMode.STRICT);
      } catch (Exception e) {
         fail("No Exception should be thrown here");
      }
   }
}
