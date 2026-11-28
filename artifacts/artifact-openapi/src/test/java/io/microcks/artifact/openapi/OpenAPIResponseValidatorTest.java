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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The validation of the responses of an operation against the OpenAPI 3 or Swagger 2 contract of its API, as OpenAPI
 * contract tests and Arazzo workflow runs apply it. The contracts are in {@code openapi-response-validation.yaml}.
 * @author SebastienDegodez
 */
class OpenAPIResponseValidatorTest {

   private static final String FIXTURES = "target/test-classes/io/microcks/artifact/openapi/";
   private static final String MESSAGE_NOT_FOUND = "null=" + OpenAPISchemaValidator.MESSAGE_NOT_FOUND;
   private static final String CONTENT_NOT_FOUND = "null=" + OpenAPISchemaValidator.CONTENT_NOT_FOUND;

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "200 | application/json | true", "500 | application/json | true",
         "204 | application/json | false", "200 | application/json;charset=UTF-8 | true",
         "200 | application/json; charset=utf-8 | true", "200 | application/json charset=UTF-8 | false",
         "200 | application/problem+json | true", "200 | application/vnd.pastry.v2+json | true",
         "200 | application/json;version=2 | false", "200 | text/plain | false", "200 | application/xml | false",
         "200 | text/json | false", "200 | | false" })
   void onlyTheResponsesWithContentAndAJsonContentTypeAreValidated(int status, String contentType, boolean validated) {
      assertEquals(validated, OpenAPIResponseValidator.isValidated(status, contentType));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
         "GET | /pastry/{name} | 200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":2.5} | ",
         "get | /pastry/{name} | 200 | application/json | {\"name\":3,\"price\":\"2.5\"} | "
               + "/name=integer found, string expected; /price=string found, number expected",
         "GET | /pastry/{name} | 200 | application/json;charset=UTF-8 | {\"price\":2.5} | "
               + "`=required property 'name' not found`",
         "GET | /pastry/{name} | 201 | application/json;charset=UTF-8 | {\"name\":\"Eclair Cafe\"} | ",
         "GET | /pastry/{name} | 201 | application/json | {\"name\":\"Eclair Cafe\"} | " + CONTENT_NOT_FOUND,
         "GET | /pastry/{name} | 202 | application/json | {\"any\":true} | null="
               + OpenAPISchemaValidator.SCHEMA_NOT_FOUND,
         "GET | /pastry/{name} | 404 | application/json | {} | `=required property 'message' not found`",
         "GET | /pastry/{name} | 500 | application/json | {} | " + MESSAGE_NOT_FOUND,
         "POST | /pastry/{name} | 200 | application/json | {} | " + MESSAGE_NOT_FOUND,
         "GET | /pastry/{name} | 200 | application/problem+json | {} | " + CONTENT_NOT_FOUND,
         "GET | /pastry/{name} | 200 | application/json | `` | =unknown found, object expected" })
   void anOpenApiResponseIsValidatedAgainstTheSchemaOfItsStatusAndContentType(String verb, String path, int status,
         String contentType, String body, String violations) throws IOException {
      OpenAPIResponseValidator validator = new OpenAPIResponseValidator(contractOf("openapi-pastry"), false, null);

      assertEquals(violations, described(validator, verb, path, status, contentType, body));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
         "GET | /pastry/{name} | 200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":2.5} | ",
         "GET | /pastry/{name} | 200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":\"2.5\"} | "
               + "/price=string found, number expected",
         "GET | /pastry/{name} | 200 | application/problem+json | {\"price\":2.5} | "
               + "`=required property 'name' not found`",
         "GET | /pastry/{name} | 404 | application/json | {} | `=required property 'message' not found`",
         "GET | /pastry/{name} | 500 | application/json | {} | " + MESSAGE_NOT_FOUND,
         "GET | /pastry/{name} | 202 | application/json | {} | null=" + OpenAPISchemaValidator.SCHEMA_NOT_FOUND })
   void aSwaggerResponseIsValidatedAgainstTheSchemaOfItsStatus(String verb, String path, int status, String contentType,
         String body, String violations) throws IOException {
      OpenAPIResponseValidator validator = new OpenAPIResponseValidator(contractOf("swagger-pastry"), true, null);

      assertEquals(violations, described(validator, verb, path, status, contentType, body));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = { "openapi-pastry | " + CONTENT_NOT_FOUND,
         "swagger-pastry | `=required property 'name' not found`" })
   void aContractTellsWhetherItIsASwaggerOne(String contract, String violations) throws IOException {
      OpenAPIResponseValidator validator = OpenAPIResponseValidator.of(contractOf(contract), null);

      assertEquals(violations,
            described(validator, "GET", "/pastry/{name}", 200, "application/problem+json", "{\"price\":2.5}"));
   }

   @Test
   void aBodyThatIsNotJsonCannotBeValidated() throws IOException {
      OpenAPIResponseValidator validator = new OpenAPIResponseValidator(contractOf("openapi-pastry"), false, null);

      assertThrows(IOException.class,
            () -> validator.validate("GET", "/pastry/{name}", 200, "application/json", "price is 2.5"));
   }

   @Test
   void theRelativeReferencesOfTheContractAreResolvedFromItsNamespace() throws IOException {
      JsonNode contract = OpenAPISchemaValidator.getJsonNodeForSchema(
            Files.readString(Path.of(FIXTURES, "weather-forecast-openapi-relative-ref.yaml"), StandardCharsets.UTF_8));
      OpenAPIResponseValidator validator = OpenAPIResponseValidator.of(contract, new File(FIXTURES).toURI().toString());

      assertEquals("/temp=string found, number expected", described(validator, "GET", "/forecast/{region}", 200,
            "application/json", "{\"region\":\"north\",\"temp\":\"cold\",\"weather\":\"snowy\",\"visibility\":25}"));
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "openapi-pastry | GET | 200 | application/json | #/components/schemas/Pastry",
         "openapi-pastry | get | 201 | application/json;charset=UTF-8 | #/components/schemas/Pastry",
         "openapi-pastry | GET | 404 | application/json | #/components/schemas/Error",
         "openapi-pastry | GET | 202 | application/json | ", "openapi-pastry | GET | 500 | application/json | ",
         "openapi-pastry | GET | 200 | application/problem+json | ",
         "swagger-pastry | GET | 200 | application/problem+json | #/definitions/Pastry",
         "swagger-pastry | GET | 404 | application/json | #/definitions/Error",
         "swagger-pastry | GET | 202 | application/json | ", "swagger-pastry | GET | 500 | application/json | " })
   void theSchemaOfAResponseIsTheOneItIsValidatedAgainst(String contract, String verb, int status, String contentType,
         String reference) throws IOException {
      OpenAPIResponseValidator validator = OpenAPIResponseValidator.of(contractOf(contract), null);

      JsonNode schema = validator.schemaOf(verb, "/pastry/{name}", status, contentType);

      assertEquals(reference == null, schema.isMissingNode());
      assertEquals(reference == null ? "" : reference, schema.path("$ref").asText());
   }

   /** The violations of a response, as pointer=message, separated by semicolons; null when there is none. */
   private static String described(OpenAPIResponseValidator validator, String verb, String path, int status,
         String contentType, String body) throws IOException {
      String violations = validator.validate(verb, path, status, contentType, body).stream()
            .map(OpenAPIResponseValidatorTest::described).collect(Collectors.joining("; "));
      return violations.isEmpty() ? null : violations;
   }

   private static String described(JsonSchemaViolation violation) {
      return violation.pointer() + "=" + violation.message();
   }

   /** A contract of {@code openapi-response-validation.yaml}, by its name. */
   private static JsonNode contractOf(String example) throws IOException {
      String marker = "# example: " + example + " ";
      return OpenAPISchemaValidator.getJsonNodeForSchema(Arrays
            .stream(Files.readString(Path.of(FIXTURES, "openapi-response-validation.yaml"), StandardCharsets.UTF_8)
                  .split("\n---\n"))
            .filter(document -> document.contains(marker)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(example + " is not in the fixture")));
   }
}
