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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is a test case for SwaggerSchemaValidator utility.
 * @author laurent
 */
public class SwaggerSchemaValidatorTest {

   @Test
   public void testFullProcedureFromSwaggerResource() {
      String openAPIText = null;
      String jsonText = "{\n" + "  \"name\": \"Rodenbach\",\n" + "  \"country\": \"Belgium\",\n"
            + "  \"type\": \"Fruit\",\n" + "  \"rating\": 4.3,\n" + "  \"status\": \"available\"\n" + "}";
      JsonNode openAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         openAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/openapi/beer-catalog-api-swagger.yaml"));
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         openAPISpec = OpenAPISchemaValidator.getJsonNodeForSchema(openAPIText);
         contentNode = OpenAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content for Get /beer/{name} response message.
      List<String> errors = SwaggerSchemaValidator.validateJsonMessage(openAPISpec, contentNode,
            "/paths/~1beer~1{name}/get/responses/200");
      assertTrue(errors.isEmpty());
   }
}
