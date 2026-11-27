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
package io.microcks.web.arazzo;

import io.microcks.artifact.jsonschema.JsonSchemaViolation;
import io.microcks.artifact.openapi.OpenAPIResponseValidator;
import io.microcks.artifact.openapi.OpenAPISchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The OpenAPI description Microcks publishes of its own API, in the api folder of the repository, that the requests a
 * client sends and the responses it receives are checked against.
 * @author SebastienDegodez
 */
final class MicrocksApiDescription {

   private final JsonNode description;
   private final OpenAPIResponseValidator responses;

   private MicrocksApiDescription(JsonNode description) {
      this.description = description;
      this.responses = OpenAPIResponseValidator.of(description, null);
   }

   /**
    * @param version The version of the description, such as 1.16
    * @return The published description of this version
    */
   static MicrocksApiDescription version(String version) {
      try {
         return new MicrocksApiDescription(OpenAPISchemaValidator
               .getJsonNodeForSchema(Files.readString(Path.of("..", "api", "microcks-openapi-v" + version + ".yaml"))));
      } catch (IOException ioe) {
         throw new UncheckedIOException(ioe);
      }
   }

   /**
    * Check that a JSON request body is the one the description gives for an operation.
    * @param verb The verb of the operation, such as POST
    * @param path The path of the operation under /api, as the description writes it, such as /arazzo/runs/{runId}
    * @param body The JSON body of the request
    */
   void assertRequestConforms(String verb, String path, String body) throws IOException {
      List<JsonSchemaViolation> violations = OpenAPISchemaValidator.validateJsonMessageWithPointers(description,
            OpenAPISchemaValidator.getJsonNode(body), operationPointer(verb, path) + "/requestBody",
            MediaType.APPLICATION_JSON_VALUE);
      assertEquals(List.of(), violations, () -> verb + " " + path + " request " + body);
   }

   /**
    * Check that a response is one the description gives for an operation: its status is described, a JSON body conforms
    * to the schema of its status and content type, and any other body has its content type described.
    * @param verb     The verb of the operation, such as GET
    * @param path     The path of the operation under /api, as the description writes it, such as /arazzo/runs/{runId}
    * @param response The response received
    */
   void assertResponseConforms(String verb, String path, ResponseEntity<String> response) throws IOException {
      int status = response.getStatusCode().value();
      JsonNode described = description.at(operationPointer(verb, path) + "/responses/" + status);
      assertFalse(described.isMissingNode(), () -> verb + " " + path + " does not describe a " + status + " response");
      if (response.getBody() == null) {
         return;
      }
      MediaType contentType = response.getHeaders().getContentType();
      assertFalse(
            described.path("content").path(contentType.value().split(";")[0].trim()).isMissingNode(),
            () -> verb + " " + path + " does not describe a " + status + " response of type " + contentType);
      if (OpenAPIResponseValidator.isValidated(status, contentType.toString())) {
         List<JsonSchemaViolation> violations = responses.validate(verb, path, status, contentType.toString(),
               response.getBody());
         assertEquals(List.of(), violations,
               () -> verb + " " + path + " " + status + " response " + response.getBody());
      }
   }

   private static String operationPointer(String verb, String path) {
      return "/paths/" + path.replace("/", "~1") + "/" + verb.toLowerCase(Locale.ROOT);
   }
}
