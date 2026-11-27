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
 * The violations of a Swagger message, each with the JSON pointer of the value of the message that violates its schema,
 * for a reader that has to tell where the message is wrong.
 * @author SebastienDegodez
 */
class SwaggerSchemaViolationsTest {

   private static final String SPECIFICATION = """
         swagger: "2.0"
         info:
           title: API Pastry - 1.0
           version: 1.0.0
         paths:
           /pastry:
             get:
               produces:
                 - application/json
               responses:
                 "200":
                   description: A pastry
                   schema:
                     $ref: '#/definitions/Pastry'
                 "404":
                   $ref: '#/responses/NotFound'
                 "409":
                   description: Conflict, without schema
                 "410":
                   $ref: '#/responses/Gone'
         responses:
           NotFound:
             description: No pastry
             schema:
               type: object
               required:
                 - message
         definitions:
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
         """;

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
         "{\"name\": \"Eclair Cafe\", \"price\": 2.5} | /paths/~1pastry/get/responses/200 | ",
         "{\"name\": \"Eclair Cafe\", \"price\": \"2.6\"} | /paths/~1pastry/get/responses/200 | "
               + "/price=string found, number expected",
         "{\"name\": \"Eclair Cafe\", \"tags\": [\"sweet\", 3]} | /paths/~1pastry/get/responses/200 | "
               + "/tags/1=integer found, string expected",
         "{\"price\": 2.5} | /paths/~1pastry/get/responses/200 | `=required property 'name' not found`",
         "{} | /paths/~1pastry/get/responses/404 | `=required property 'message' not found`",
         "{} | /paths/~1pastry/get/responses/500 | "
               + "null=messagePathPointer does not represent a valid JSON Pointer in OpenAPI specification",
         "{} | /paths/~1pastry/get/responses/410 | "
               + "null=messagePathPointer does not represent an existing JSON Pointer in OpenAPI specification",
         "{} | /paths/~1pastry/get/responses/409 | "
               + "null=schemaPathPointer does not represent an existing JSON Pointer in OpenAPI specification" })
   void eachViolationTellsTheJsonPointerOfTheValueThatViolatesTheSchema(String message, String messagePathPointer,
         String violations) throws IOException {
      JsonNode specification = OpenAPISchemaValidator.getJsonNodeForSchema(SPECIFICATION);

      List<JsonSchemaViolation> found = SwaggerSchemaValidator.validateJsonMessageWithPointers(specification,
            OpenAPISchemaValidator.getJsonNode(message), messagePathPointer, null);

      assertEquals(violations == null ? List.of() : List.of(violations),
            found.stream().map(violation -> violation.pointer() + "=" + violation.message()).toList());
      assertEquals(found.stream().map(JsonSchemaViolation::message).toList(), SwaggerSchemaValidator
            .validateJsonMessage(specification, OpenAPISchemaValidator.getJsonNode(message), messagePathPointer));
   }
}
