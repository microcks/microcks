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
package io.github.microcks.util.openapi;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for OpenAPISchemaValidator utility.
 * @author laurent
 */
class OpenAPISchemaValidatorTest {

   @Test
   void testValidateJsonSuccess() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/car-schema.json"), StandardCharsets.UTF_8);
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   void testValidateJsonSuccessWithNull() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"307\", \"model\": null, \"year\": 2003}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/car-schema.json"), StandardCharsets.UTF_8);
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   void testValidateJsonFailure() {
      boolean valid = true;
      String schemaText = null;
      String jsonText = "{\"id\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/car-schema.json"), StandardCharsets.UTF_8);
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);

      // Now revalidate and check validation messages.
      List<String> errors = null;
      try {
         errors = OpenAPISchemaValidator.validateJson(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, errors.size());
      assertEquals("required property 'name' not found", errors.get(0));
   }

   @Test
   void testValidateJsonUnknownNodeFailure() {
      boolean valid = true;
      String schemaText = null;
      String jsonText = "{\"name\": \"307\", " + "\"model\": \"Peugeot 307\", \"year\": 2003, \"energy\": \"GO\"}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/car-schema-no-addon.json"),
               StandardCharsets.UTF_8);
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);

      // Now revalidate and check validation messages.
      List<String> errors = null;
      try {
         errors = OpenAPISchemaValidator.validateJson(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, errors.size());
      assertEquals("property 'energy' is not defined in the schema and the schema does not allow additional properties",
            errors.get(0));
   }

   @Test
   void testValidateJsonSchemaWithReferenceSuccess() {
      boolean valid = true;
      String schemaText = null;
      String jsonText = "[{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003},"
            + "{\"name\": \"jean-pierre\", \"model\": \"Peugeot Traveler\", \"year\": 2017}]";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/cars-schema.json"),
               StandardCharsets.UTF_8);
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   void testValidateJsonWithExternalReferenceSuccess() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "[{\"region\": \"north\", \"weather\": \"snowy\", \"temp\": -1.5, \"visibility\": 25}, "
            + "{\"region\": \"west\", \"weather\": \"rainy\", \"temp\": 12.2, \"visibility\": 300}]";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/weather-forecasts.json"),
               StandardCharsets.UTF_8);
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText,
               "https://raw.githubusercontent.com/microcks/microcks/1.6.x/commons/util/src/test/resources/io/github/microcks/util/openapi/");
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   void testValidateJsonSchemaWithReferenceFailure() {
      boolean valid = true;
      String schemaText = null;
      String jsonText = "[{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003},"
            + "{\"name\": \"jean-pierre\", \"model\": \"Peugeot Traveler\", \"year\": \"2017\"}]";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/cars-schema.json"),
               StandardCharsets.UTF_8);
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);

      List<String> errors = null;
      try {
         errors = OpenAPISchemaValidator.validateJson(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      // Don't know why but when a failure occurs, validator also complains
      // about components reference not being found.
      assertEquals(1, errors.size());
      assertEquals("string found, integer expected", errors.get(0));
   }

   @Test
   void testFullProcedureFromOpenAPIResource() {
      String openAPIText = null;
      String jsonText = """
            [
              { "resourceId": "396be545-e2d4-4497-a5b5-700e89ab99c0" },
              { "resourceId": "f377afb3-5c62-40cc-8f07-1f4749a780eb" }
            ]
            """;
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/response-refs-openapi.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1accounts/get/responses/200", "application/json");
      assertTrue(errors.isEmpty());

      // Now try with another message.
      jsonText = "{ \"account\": {\"resourceId\": \"396be545-e2d4-4497-a5b5-700e89ab99c0\" } }";

      try {
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts/{accountId} response message.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1accounts~1{accountId}/get/responses/200", "application/json");
      assertTrue(errors.isEmpty());
   }

   @Test
   void testFullProcedureFromOpenAPIResourceWithLooseCharsetContentType() {
      String openAPIText = null;
      String jsonText = """
            [
              { "resourceId": "396be545-e2d4-4497-a5b5-700e89ab99c0" },
              { "resourceId": "f377afb3-5c62-40cc-8f07-1f4749a780eb" }
            ]
            """;
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/response-refs-openapi.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1accounts/get/responses/200", "application/json;charset=UTF-8");
      assertTrue(errors.isEmpty());

      // Now try with another message.
      jsonText = "{ \"account\": {\"resourceId\": \"396be545-e2d4-4497-a5b5-700e89ab99c0\" } }";

      try {
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts/{accountId} response message.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1accounts~1{accountId}/get/responses/200", "application/hal+json; charset=utf-8");
      assertTrue(errors.isEmpty());

      // Now try with failing message.
      jsonText = "{ \"account\": {\"resourceIdentifier\": \"396be545-e2d4-4497-a5b5-700e89ab99c0\" } }";

      try {
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts/{accountId} response message.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1accounts~1{accountId}/get/responses/200", "application/hal+json; charset=UTF-8");
      assertFalse(errors.isEmpty());
      assertEquals("required property 'resourceId' not found", errors.get(1));

      // Validate again the content for Get /accounts/{accountId} response message with no charset.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1accounts~1{accountId}/get/responses/200", "application/hal+json; charset=UTF-8");
      assertFalse(errors.isEmpty());
      assertEquals("required property 'resourceId' not found", errors.get(1));
   }

   @Test
   void testFullProcedureFromOpenAPIResourceWithRef() {
      String openAPIText = null;
      String jsonText = """
            {
               "region": "north",
               "temp": -1.5,
               "weather": "snowy",
               "visibility": 25
            }
            """;
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-local-ref.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /forecast/{region} response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1forecast~1{region}/get/responses/200", "application/json");
      assertTrue(errors.isEmpty());

      // Now try with an external absolute ref.
      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File(
                     "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-absolute-ref.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /forecast/{region} response message.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1forecast~1{region}/get/responses/200", "application/json");
      for (String error : errors) {
         System.out.println("Validation error: " + error);
      }
      assertTrue(errors.isEmpty());

      // Now try with an external relative ref.
      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File(
                     "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /forecast/{region} response message but without specifying a namespace.
      // This should fail as type cannot be resolved.
      try {
         errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
               "/paths/~1forecast~1{region}/get/responses/200", "application/json");
      } catch (Exception e) {
         assertInstanceOf(IllegalArgumentException.class, e);
      }

      // Validate the content for Get /forecast/{region} response message. Now specifying a namespace.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1forecast~1{region}/get/responses/200", "application/json",
            "https://raw.githubusercontent.com/microcks/microcks/1.5.x/commons/util/src/test/resources/io/github/microcks/util/openapi/");

      assertTrue(errors.isEmpty());


      // Now test with a $ref at the items level of an array.
      // We cannot use relative notation here (eg. ./schema.json) but should use direct notation like schema.json
      // Check the com.github.fge.jsonschema.core.keyword.syntax.checkers.helpers.URISyntaxChercker class.
      jsonText = "[{\"region\": \"north\", \"weather\": \"snowy\", \"temp\": -1.5, \"visibility\": 25}, "
            + "{\"region\": \"west\", \"weather\": \"rainy\", \"temp\": 12.2, \"visibility\": 300}]";

      // Now try with an external relative ref.
      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File(
                     "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /forecast response message but without specifying a namespace.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1forecast/get/responses/200", "application/json",
            "https://raw.githubusercontent.com/microcks/microcks/1.5.x/commons/util/src/test/resources/io/github/microcks/util/openapi/");

      assertTrue(errors.isEmpty());
   }

   @Test
   void testFullProcedureFromOpenAPIResourceFailure() {
      String openAPIText = null;
      String jsonText = """
            [
              { "resource": "396be545-e2d4-4497-a5b5-700e89ab99c0", "id": "01" },
              { "resource": "f377afb3-5c62-40cc-8f07-1f4749a780eb", "id": "01" }
            ]
            """;
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/response-refs-openapi.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1accounts/get/responses/200", "application/json");
      assertFalse(errors.isEmpty());
      assertEquals(6, errors.size());

      // Now try with another message.
      jsonText = "{ \"account\": {\"resource\": \"396be545-e2d4-4497-a5b5-700e89ab99c0\" } }";

      try {
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts/{accountId} response message.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1accounts~1{accountId}/get/responses/200", "application/json");
      assertFalse(errors.isEmpty());
   }

   @Test
   void testFullProcedureFromOpenAPIResourceWithStructures() {
      String openAPIText = null;
      String jsonText = """
            {
               "id": "396be545-e2d4-4497-a5b5-700e89ab99c0",
               "realm_id": "f377afb3-5c62-40cc-8f07-1f4749a780eb",
               "slug": "gore",
               "tagline": "Blood! Blood! Blood!",
               "avatar_url": "/gore.png",
               "accent_color": "#f96680",
               "delisted": false,
               "logged_in_only": false,
               "descriptions": []
            }
            """;
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/boba-openapi.json"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1boards~1{slug}/get/responses/200", "application/json");

      assertFalse(errors.isEmpty());
   }

   @ParameterizedTest()
   @CsvSource(value = { "null | {\"seasonData\":null} | true",
         "notNull | {\"seasonData\": {\"seasonType\": \"BratSummer\", \"isTooHot\": false, \"isTooCold\": true}} | true",
         "notNullInvalid | {\"seasonData\": {\"seasonType\": \"365PartyGirl\", \"isTooHot\": false, \"isTooCold\": true}} | false" }, delimiter = '|')
   void testNullableAllOf(String testType, String jsonText, boolean expected) {
      String openAPIText = null;

      JsonNode openAPISpec = null;
      JsonNode contentNode = null;
      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/season-nullable-all-of.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1seasonAllOf/post/responses/201", "application/json");

      assertEquals(expected, errors.isEmpty());
   }


   @ParameterizedTest()
   @CsvSource(value = { "null | {\"seasonData\":null} | true",
         "notNullOne | {\"seasonData\": {\"seasonType\": \"BratSummer\", \"isTooHot\": false, \"isTooCold\": true}} | true",
         "notNullTwo | {\"seasonData\": {\"isUmami\": true, \"isSalty\": true}} | true",
         "notNullInvalidAllMatching | {\"seasonData\": {\"seasonType\": \"BratSummer\", \"isTooHot\": false, \"isTooCold\": true, \"isUmami\": true,  \"isSalty\": true}} | false",
         "notNullInvalidNoneMatching | {\"seasonData\": {\"seasonType\": \"365PartyGirl\", \"isTooHot\": false, \"isTooCold\": true, \"isUmami\": true}} | false", }, delimiter = '|')
   void testNullableOneOf(String testType, String jsonText, boolean expected) {
      String openAPIText = null;

      JsonNode openAPISpec = null;
      JsonNode contentNode = null;
      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/season-nullable-all-of.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1seasonOneOf/post/responses/201", "application/json");

      assertEquals(expected, errors.isEmpty());
   }

   @ParameterizedTest()
   @CsvSource(value = { "null | {\"seasonData\":null} | true",
         "notNullOne | {\"seasonData\": {\"seasonType\": \"BratSummer\", \"isTooHot\": false, \"isTooCold\": true}} | true",
         "notNullTwo | {\"seasonData\": {\"isUmami\": true, \"isSalty\": true}} | true",
         "notNullAllMatch | {\"seasonData\": {\"seasonType\": \"BratSummer\", \"isTooHot\": false, \"isTooCold\": true, \"isUmami\": true, \"isSalty\": true}} | true",
         "notNullInvalid | {\"seasonData\": {\"seasonType\": \"365PartyGirl\", \"isTooHot\": false, \"isTooCold\": true, \"isUmami\": true}} | false", }, delimiter = '|')
   void testNullableAnyOf(String testType, String jsonText, boolean expected) {
      String openAPIText = null;

      JsonNode openAPISpec = null;
      JsonNode contentNode = null;
      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/season-nullable-all-of.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1seasonAnyOf/post/responses/201", "application/json");

      assertEquals(expected, errors.isEmpty());
   }

   @Test
   void testNullableFieldInComponentRef() {
      String openAPIText = null;
      String jsonText = """
             [
               {
                "name": "Brian May",
                "birthDate": "1947-07-19T00:00:00.0Z",
                "deathDate": null
               },
               {
                "name": "Roger Taylor",
                "birthDate": "1949-07-26T00:00:00.0Z",
                "deathDate": null
               },
               {
                "name": "John Deacon",
                "birthDate": "1951-08-19T00:00:00.0Z",
                "deathDate": null
               },
               {
                 "name": "Freddy Mercury",
                 "birthDate": "1946-09-15T00:00:00.0Z",
                 "deathDate": "1946-11-24T00:00:00.0Z"
               }
             ]
            """;
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/nullable-fields-openapi.yaml"),
               StandardCharsets.UTF_8);
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1queen~1members/get/responses/200", "application/json");
      assertTrue(errors.isEmpty());
   }
}
