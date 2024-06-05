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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This is a test case for OpenAPISchemaBuilder class.
 * @author laurent
 */
class OpenAPISchemaBuilderTest {

   @Test
   void testBuildTypeSchemaFromJson() {
      JsonNode jsonNode = null;
      ObjectMapper mapper = new ObjectMapper();

      String jsonText = "{\"foo\": \"bar\", \"flag\": true, " + "\"list\": [1, 2], " + "\"obj\": {\"fizz\": \"bar\"}, "
            + "\"objList\": [{\"number\": 1}, {\"number\": 2}]}";

      String expectedSchema = "{\"type\":\"object\",\"properties\":{\"foo\":{\"type\":\"string\"},\"flag\":{\"type\":\"boolean\"},\"list\":{\"type\":\"array\",\"items\":{\"type\":\"number\"}},\"obj\":{\"type\":\"object\",\"properties\":{\"fizz\":{\"type\":\"string\"}}},\"objList\":{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"number\":{\"type\":\"number\"}}}}}}";
      try {
         jsonNode = mapper.readTree(jsonText);
      } catch (Exception e) {
         fail("Exception should not occur");
      }

      JsonNode schemaNode = OpenAPISchemaBuilder.buildTypeSchemaFromJson(jsonNode);
      try {
         String actual = mapper.writeValueAsString(schemaNode);
         assertEquals(expectedSchema, actual);
      } catch (JsonProcessingException e) {
         fail("No exception should be thrown");
      }
   }
}
