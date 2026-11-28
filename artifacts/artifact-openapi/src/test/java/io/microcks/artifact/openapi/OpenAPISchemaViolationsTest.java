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
package io.microcks.artifact.openapi;

import io.microcks.artifact.jsonschema.JsonSchemaViolation;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The violations of an OpenAPI message, each with the JSON pointer of the value of the message that violates its
 * schema, for a reader that has to tell where the message is wrong.
 * @author SebastienDegodez
 */
class OpenAPISchemaViolationsTest {

   private static final String SPECIFICATION = """
         openapi: 3.0.2
         info:
           title: API Pastry - 2.0
           version: 2.0.0
         paths:
           /pastry:
             get:
               responses:
                 "200":
                   description: A pastry
                   content:
                     application/json:
                       schema:
                         $ref: '#/components/schemas/Pastry'
                 "204":
                   description: No pastry
                   content:
                     application/json: {}
         components:
           schemas:
             Pastry:
               type: object
               required:
                 - name
               properties:
                 name:
                   type: string
                 price:
                   type: number
                 tags:
                   type: array
                   items:
                     type: string
                 "a/b~c":
                   type: string
         """;

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
         "{\"name\": \"Eclair Cafe\", \"price\": 2.5} | /paths/~1pastry/get/responses/200 | application/json | ",
         "{\"name\": \"Eclair Cafe\", \"price\": \"2.6\"} | /paths/~1pastry/get/responses/200 | application/json | "
               + "/price=string found, number expected",
         "{\"name\": \"Eclair Cafe\", \"tags\": [\"sweet\", 3]} | /paths/~1pastry/get/responses/200 | "
               + "application/json | /tags/1=integer found, string expected",
         "{\"name\": \"Eclair Cafe\", \"a/b~c\": 3} | /paths/~1pastry/get/responses/200 | application/json | "
               + "/a~1b~0c=integer found, string expected",
         "{\"price\": 2.5} | /paths/~1pastry/get/responses/200 | application/json | "
               + "`=required property 'name' not found`",
         "{} | /paths/~1pastry/get/responses/404 | application/json | "
               + "null=messagePathPointer does not represent a valid JSON Pointer in OpenAPI specification",
         "{} | /paths/~1pastry/get/responses/200 | application/xml | "
               + "null=messagePathPointer does not represent an existing JSON Pointer in OpenAPI specification",
         "{} | /paths/~1pastry/get/responses/204 | application/json | "
               + "null=schemaPathPointer does not represent an existing JSON Pointer in OpenAPI specification" })
   void eachViolationTellsTheJsonPointerOfTheValueThatViolatesTheSchema(String message, String messagePathPointer,
         String contentType, String violations) throws IOException {
      JsonNode specification = OpenAPISchemaValidator.getJsonNodeForSchema(SPECIFICATION);

      List<JsonSchemaViolation> found = OpenAPISchemaValidator.validateJsonMessageWithPointers(specification,
            OpenAPISchemaValidator.getJsonNode(message), messagePathPointer, contentType);

      assertEquals(violations == null ? List.of() : List.of(violations),
            found.stream().map(violation -> violation.pointer() + "=" + violation.message()).toList());
      assertEquals(found.stream().map(JsonSchemaViolation::message).toList(),
            OpenAPISchemaValidator.validateJsonMessage(specification, OpenAPISchemaValidator.getJsonNode(message),
                  messagePathPointer, contentType));
   }
}
