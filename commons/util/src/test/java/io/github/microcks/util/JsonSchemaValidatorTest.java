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
package io.github.microcks.util;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This is a test case for JsonSchemaValidation utility.
 * @author laurent
 */
class JsonSchemaValidatorTest {
   @Test
   void testValidateJsonSuccess() {
      boolean valid = false;
      String schemaText = null;
      String jsonText = "{\"name\": \"307\", \"model\": \"Peugeot 307\", \"year\": 2003}";

      try {
         // Load schema from file.
         schemaText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/car-schema.json"));
         // Validate Json according schema.
         valid = JsonSchemaValidator.isJsonValid(schemaText, jsonText);
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
         schemaText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/car-schema.json"));
         // Validate Json according schema.
         valid = JsonSchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);

      // Now revalidate and check validation messages.
      List<String> errors = null;
      try {
         errors = JsonSchemaValidator.validateJson(schemaText, jsonText);
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
         schemaText = FileUtils
               .readFileToString(new File("target/test-classes/io/github/microcks/util/car-schema-no-addon.json"));
         // Validate Json according schema.
         valid = JsonSchemaValidator.isJsonValid(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }

      // Assert Json object is not valid.
      assertFalse(valid);

      // Now revalidate and check validation messages.
      List<String> errors = null;
      try {
         errors = JsonSchemaValidator.validateJson(schemaText, jsonText);
      } catch (Exception e) {
         fail("Exception should not be thrown");
      }
      assertEquals(1, errors.size());
      assertEquals("property 'energy' is not defined in the schema and the schema does not allow additional properties",
            errors.get(0));
   }
}
