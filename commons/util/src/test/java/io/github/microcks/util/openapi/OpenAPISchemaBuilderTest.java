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
package io.github.microcks.util.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * This is a test case for OpenAPISchemaBuilder class.
 * @author laurent
 */
public class OpenAPISchemaBuilderTest {

   @Test
   public void testBuildTypeSchemaFromJson() {
      JsonNode jsonNode = null;
      ObjectMapper mapper = new ObjectMapper();

      String jsonText = "{\"foo\": \"bar\", \"flag\": true, " +
            "\"list\": [1, 2], " +
            "\"obj\": {\"fizz\": \"bar\"}, " +
            "\"objList\": [{\"number\": 1}, {\"number\": 2}]}";

      String expectedSchema = "{\n" +
            "  \"type\" : \"object\",\n" +
            "  \"properties\" : {\n" +
            "    \"foo\" : {\n" +
            "      \"type\" : \"string\"\n" +
            "    },\n" +
            "    \"flag\" : {\n" +
            "      \"type\" : \"boolean\"\n" +
            "    },\n" +
            "    \"list\" : {\n" +
            "      \"type\" : \"array\",\n" +
            "      \"items\" : {\n" +
            "        \"type\" : \"number\"\n" +
            "      }\n" +
            "    },\n" +
            "    \"obj\" : {\n" +
            "      \"type\" : \"object\",\n" +
            "      \"properties\" : {\n" +
            "        \"fizz\" : {\n" +
            "          \"type\" : \"string\"\n" +
            "        }\n" +
            "      }\n" +
            "    },\n" +
            "    \"objList\" : {\n" +
            "      \"type\" : \"array\",\n" +
            "      \"items\" : {\n" +
            "        \"type\" : \"object\",\n" +
            "        \"properties\" : {\n" +
            "          \"number\" : {\n" +
            "            \"type\" : \"number\"\n" +
            "          }\n" +
            "        }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";
      try {
         jsonNode = mapper.readTree(jsonText);
      } catch (Exception e) {
         fail("Exception should not occur");
      }

      JsonNode schemaNode = OpenAPISchemaBuilder.buildTypeSchemaFromJson(jsonNode);
      try {
         assertEquals(expectedSchema, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaNode));
      } catch (JsonProcessingException e) {
         fail("No exception should be thrown");
      }
   }
}
