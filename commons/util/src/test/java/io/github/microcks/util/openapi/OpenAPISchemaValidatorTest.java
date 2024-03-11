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
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is a test case for OpenAPISchemaValidator utility.
 * @author laurent
 */
public class OpenAPISchemaValidatorTest {

   @Test
   public void testValidateJsonSuccess() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}";

      try {
         // Load schema from file.
         schemaText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/openapi/car-schema.json"));
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   public void testValidateJsonSuccessWithNull() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"307\", \"model\": null, \"year\": 2003}";

      try {
         // Load schema from file.
         schemaText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/openapi/car-schema.json"));
         // Validate Json according schema.
         valid = OpenAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   public void testValidateJsonFailure() {
      boolean valid = true;
      String schemaText = null;
      String jsonText = "{\"id\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}";

      try {
         // Load schema from file.
         schemaText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/openapi/car-schema.json"));
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
      assertEquals("object has missing required properties ([\"name\"])", errors.get(0));
   }

   @Test
   public void testValidateJsonUnknownNodeFailure() {
      boolean valid = true;
      String schemaText = null;
      String jsonText = "{\"name\": \"307\", " + "\"model\": \"Peugeot 307\", \"year\": 2003, \"energy\": \"GO\"}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/car-schema-no-addon.json"));
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
      assertEquals("object instance has properties which are not allowed by the schema: [\"energy\"]", errors.get(0));
   }

   @Test
   public void testValidateJsonSchemaWithReferenceSuccess() {
      boolean valid = true;
      String schemaText = null;
      String jsonText = "[{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003},"
            + "{\"name\": \"jean-pierre\", \"model\": \"Peugeot Traveler\", \"year\": 2017}]";

      try {
         // Load schema from file.
         schemaText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/openapi/cars-schema.json"));
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
   public void testValidateJsonWithExternalReferenceSuccess() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "[{\"region\": \"north\", \"weather\": \"snowy\", \"temp\": -1.5, \"visibility\": 25}, "
            + "{\"region\": \"west\", \"weather\": \"rainy\", \"temp\": 12.2, \"visibility\": 300}]";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/weather-forecasts.json"));
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
   public void testValidateJsonSchemaWithReferenceFailure() {
      boolean valid = true;
      String schemaText = null;
      String jsonText = "[{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003},"
            + "{\"name\": \"jean-pierre\", \"model\": \"Peugeot Traveler\", \"year\": \"2017\"}]";

      try {
         // Load schema from file.
         schemaText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/openapi/cars-schema.json"));
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
      assertEquals(2, errors.size());
      assertEquals("the following keywords are unknown and will be ignored: [components]", errors.get(0));
      assertEquals("instance type (string) does not match any allowed primitive type (allowed: [\"integer\"])",
            errors.get(1));
   }

   @Test
   public void testFullProcedureFromOpenAPIResource() {
      String openAPIText = null;
      String jsonText = "[\n" + "  { \"resourceId\": \"396be545-e2d4-4497-a5b5-700e89ab99c0\" },\n"
            + "  { \"resourceId\": \"f377afb3-5c62-40cc-8f07-1f4749a780eb\" }\n" + "]";
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/response-refs-openapi.yaml"));
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
   public void testFullProcedureFromOpenAPIResourceWithRef() {
      String openAPIText = null;
      String jsonText = "{\n" + "  \"region\": \"north\",\n" + "  \"temp\": -1.5,\n" + "  \"weather\": \"snowy\",\n"
            + "  \"visibility\": 25\n" + "}";
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-local-ref.yaml"));
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
         openAPIText = FileUtils.readFileToString(new File(
               "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-absolute-ref.yaml"));
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /forecast/{region} response message.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1forecast~1{region}/get/responses/200", "application/json");
      assertTrue(errors.isEmpty());

      // Now try with an external relative ref.
      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(new File(
               "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref.yaml"));
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /forecast/{region} response message but without specifying a namespace.
      // This should fail as type cannot be resolved.
      errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1forecast~1{region}/get/responses/200", "application/json");
      assertFalse(errors.isEmpty());
      assertEquals(2, errors.size());
      assertTrue(errors.get(1).contains("URI \"weather-forecast-schema.json#\" is not absolute"));

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
         openAPIText = FileUtils.readFileToString(new File(
               "target/test-classes/io/github/microcks/util/openapi/weather-forecast-openapi-relative-ref.yaml"));
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
   public void testFullProcedureFromOpenAPIResourceFailure() {
      String openAPIText = null;
      String jsonText = "[\n" + "  { \"resource\": \"396be545-e2d4-4497-a5b5-700e89ab99c0\", \"id\": \"01\" },\n"
            + "  { \"resource\": \"f377afb3-5c62-40cc-8f07-1f4749a780eb\", \"id\": \"01\" }\n" + "]";
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/response-refs-openapi.yaml"));
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
      assertEquals(5, errors.size());

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
   public void testFullProcedureFromOpenAPIResourceWithStructures() {
      String openAPIText = null;
      String jsonText = "{\n" + "          \"id\": \"396be545-e2d4-4497-a5b5-700e89ab99c0\",\n"
            + "          \"realm_id\": \"f377afb3-5c62-40cc-8f07-1f4749a780eb\",\n" + "          \"slug\": \"gore\",\n"
            + "          \"tagline\": \"Blood! Blood! Blood!\",\n" + "          \"avatar_url\": \"/gore.png\",\n"
            + "          \"accent_color\": \"#f96680\",\n" + "          \"delisted\": false,\n"
            + "          \"logged_in_only\": false,\n" + "          \"descriptions\": []\n" + "        }";
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/openapi/boba-openapi.json"));
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /accounts response message.
      List<String> errors = OpenAPISchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1boards~1{slug}/get/responses/200", "application/json");

      assertTrue(errors.isEmpty());
   }

   @Test
   public void testNullableFieldInComponentRef() {
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
               new File("target/test-classes/io/github/microcks/util/openapi/nullable-fields-openapi.yaml"));
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
