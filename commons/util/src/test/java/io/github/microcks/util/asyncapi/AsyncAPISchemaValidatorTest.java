/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.github.microcks.util.asyncapi;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.microcks.util.asyncapi.AsyncAPISchemaValidator;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.util.List;

import static org.junit.Assert.*;

/**
 * This is a test case for AsyncAPISchemaValidator utility.
 * @author laurent
 */
public class AsyncAPISchemaValidatorTest {

   @Test
   public void testValidateJsonSuccess() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-schema.json"));
         // Validate Json according schema.
         valid = AsyncAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   public void testValidateJsonSuccessFromYaml() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-schema.yaml"));
         // Validate Json according schema.
         valid = AsyncAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is valid.
      assertTrue(valid);
   }

   @Test
   public void testValidateJsonFailure() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-schema.json"));
         // Validate Json according schema.
         valid = AsyncAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);
   }

   @Test
   public void testValidateJsonFailureFromYaml() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";

      try {
         // Load schema from file.
         schemaText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-schema.yaml"));
         // Validate Json according schema.
         valid = AsyncAPISchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);
   }

   @Test
   public void testFullProcedureFromAsyncAPIResource() {
      String asyncAPIText = null;
      String jsonText = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml"));
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message/payload");
      assertTrue(errors.isEmpty());
   }

   @Test
   public void testFullProcedureFromAsyncAPIResourceFailure() {
      String asyncAPIText = null;
      String jsonText = "{\"id\": \"123456\", \"name\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-asyncapi.yaml"));
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message/payload");
      assertFalse(errors.isEmpty());

      assertEquals(2, errors.size());
      // First error is because payload does not have any ref to components.
      assertEquals("the following keywords are unknown and will be ignored: [components]", errors.get(0));
      assertEquals("object instance has properties which are not allowed by the schema: [\"id\",\"name\"]", errors.get(1));
   }

   @Test
   public void testFullProcedureFromAsyncAPIWithRefsResource() {
      String asyncAPIText = null;
      String jsonText = "{\"fullName\": \"Laurent Broudoux\", \"email\": \"laurent@microcks.io\", \"age\": 41}";
      JsonNode asyncAPISpec = null;
      JsonNode contentNode = null;

      try {
         // Load full specification from file.
         asyncAPIText = FileUtils.readFileToString(
               new File("target/test-classes/io/github/microcks/util/asyncapi/user-signedup-out-asyncapi.yaml"));
         // Extract JSON nodes using OpenAPISchemaValidator methods.
         asyncAPISpec = AsyncAPISchemaValidator.getJsonNodeForSchema(asyncAPIText);
         contentNode = AsyncAPISchemaValidator.getJsonNode(jsonText);
      } catch (Exception e) {
         e.printStackTrace();
         fail("Exception should not be thrown");
      }

      // Validate the content of user/signedup subscribe chanel.
      List<String> errors = AsyncAPISchemaValidator.validateJsonMessage(asyncAPISpec, contentNode,
            "/channels/user~1signedup/subscribe/message/payload");
      assertTrue(errors.isEmpty());
   }
}
