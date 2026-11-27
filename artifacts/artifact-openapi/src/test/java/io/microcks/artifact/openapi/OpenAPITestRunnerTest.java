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

import io.microcks.domain.Operation;
import io.microcks.domain.Request;
import io.microcks.domain.Resource;
import io.microcks.domain.ResourceType;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.domain.TestReturn;
import io.microcks.test.ResourceReader;
import io.microcks.test.ResponseReader;
import io.microcks.util.http.ClientHttpResponse;
import io.microcks.util.http.HttpHeaders;
import io.microcks.util.http.HttpStatusCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How the OpenAPI contract tests decide whether a response of an implementation conforms to the contract of its
 * service: the code and the message of the test of one request. The contracts are in
 * {@code openapi-test-runner-validation.yaml}.
 * @author SebastienDegodez
 */
class OpenAPITestRunnerTest {

   private static final String FIXTURES = "target/test-classes/io/microcks/artifact/openapi/";
   private static final String PASTRY = "{\"name\":\"Eclair Cafe\",\"price\":2.5}";
   private static final int FAILURE = TestReturn.FAILURE_CODE;

   private final FakeResources resources = new FakeResources();
   private final FakeResponses responses = new FakeResponses();
   private final Service service = service();
   private final Request request = request();

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
         "200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":2.5} | 0 | ",
         "200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":\"2.5\"} | 1 | "
               + "string found, number expected",
         "200 | application/json | {\"name\":3,\"price\":\"2.5\"} | 1 | "
               + "integer found, string expected\\nstring found, number expected",
         "200 | application/json;charset=UTF-8 | {\"price\":2.5} | 1 | `required property 'name' not found`",
         "201 | application/json;charset=UTF-8 | {\"name\":\"Eclair Cafe\"} | 0 | ",
         "201 | application/json | {\"name\":\"Eclair Cafe\"} | 1 | "
               + "messagePathPointer does not represent an existing JSON Pointer in OpenAPI specification",
         "202 | application/json | {\"any\":true} | 1 | "
               + "schemaPathPointer does not represent an existing JSON Pointer in OpenAPI specification",
         "404 | application/json | {} | 1 | `required property 'message' not found`",
         "500 | application/json | {} | 1 | "
               + "messagePathPointer does not represent a valid JSON Pointer in OpenAPI specification",
         "200 | application/problem+json | {} | 1 | "
               + "messagePathPointer does not represent an existing JSON Pointer in OpenAPI specification",
         "200 | application/json | price is 2.5 | 1 | ",
         "200 | application/json | `` | 1 | unknown found, object expected", "200 | text/plain | price is 2.5 | 0 | ",
         "200 | application/xml | <pastry/> | 0 | ", "200 | application/json;version=2 | price is 2.5 | 0 | ",
         "204 | application/json | | 0 | ", "200 | | price is 2.5 | 0 | " })
   void eachResponseIsValidatedAgainstItsOpenApiSchema(int status, String contentType, String body, int code,
         String message) throws IOException {
      given(ResourceType.OPEN_API_SPEC, contractOf("openapi-pastry"));

      assertTest(new OpenAPITestRunner(resources, responses, false), "GET /pastry/{name}", status, contentType, body,
            code, message);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
         "200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":2.5} | 0 | ",
         "200 | application/json | {\"name\":\"Eclair Cafe\",\"price\":\"2.5\"} | 1 | "
               + "string found, number expected",
         "200 | application/problem+json | {\"price\":2.5} | 1 | `required property 'name' not found`",
         "404 | application/json | {} | 1 | `required property 'message' not found`",
         "500 | application/json | {} | 1 | "
               + "messagePathPointer does not represent a valid JSON Pointer in OpenAPI specification",
         "200 | application/json | price is 2.5 | 1 | ", "200 | text/plain | price is 2.5 | 0 | ",
         "204 | application/json | | 0 | ", "202 | application/json | {} | 1 | "
               + "schemaPathPointer does not represent an existing JSON Pointer in OpenAPI specification" })
   void eachResponseIsValidatedAgainstItsSwaggerSchema(int status, String contentType, String body, int code,
         String message) throws IOException {
      given(ResourceType.SWAGGER, contractOf("swagger-pastry"));

      assertTest(new OpenAPITestRunner(resources, responses, false), "GET /pastry/{name}", status, contentType, body,
            code, message);
   }

   @Test
   void theContractIsTheFirstOpenApiOrSwaggerResourceOfAllTheArtifactsWhenTheMainOnesHaveNone() throws IOException {
      resources.main = List.of(resource(ResourceType.JSON_SCHEMA, "{}"), resource(ResourceType.ASYNC_API_SPEC, "{}"));
      resources.all = List.of(resource(ResourceType.WSDL, "<definitions/>"),
            resource(ResourceType.SWAGGER, contractOf("swagger-pastry")),
            resource(ResourceType.OPEN_API_SPEC, contractOf("openapi-pastry")));

      // Validated as Swagger, whose responses have no content type.
      assertTest(new OpenAPITestRunner(resources, responses, false), "GET /pastry/{name}", 200,
            "application/problem+json", "{\"price\":2.5}", FAILURE, "required property 'name' not found");
   }

   @Test
   void withoutContractTheTestFailsWithoutMessage() throws IOException {
      resources.main = List.of();
      resources.all = List.of(resource(ResourceType.JSON_SCHEMA, "{}"));

      assertTest(new OpenAPITestRunner(resources, responses, false), "GET /pastry/{name}", 200, "application/json",
            PASTRY, FAILURE, null);
   }

   @Test
   void theContractOfTheMainArtifactComesFirst() throws IOException {
      given(ResourceType.OPEN_API_SPEC, contractOf("openapi-pastry"));
      resources.all = List.of(resource(ResourceType.SWAGGER, contractOf("swagger-pastry")));

      assertTest(new OpenAPITestRunner(resources, responses, false), "GET /pastry/{name}", 202, "application/json",
            "{}", FAILURE, "schemaPathPointer does not represent an existing JSON Pointer in OpenAPI specification");
   }

   @Test
   void anUnreadableContractFailsTheTestWithoutMessage() throws IOException {
      given(ResourceType.OPEN_API_SPEC, contractOf("unreadable"));

      assertTest(new OpenAPITestRunner(resources, responses, false), "GET /pastry/{name}", 200, "application/json",
            PASTRY, FAILURE, null);
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "200 | application/json | 200 | application/json;charset=UTF-8 | 0 | ",
         "200 | application/json;charset=UTF-8 | 200 | application/json;charset=utf-8 | 0 | ",
         "200 | application/json;charset=UTF-16 | 200 | application/json;charset=utf-8 | 1 | "
               + "Response Content-Type does not match expected one. "
               + "Expecting application/json;charset=UTF-16 but got application/json;charset=utf-8",
         "200 | application/json | 200 | text/plain | 1 | "
               + "Response Content-Type does not match expected one. Expecting application/json but got text/plain",
         "200 | application/json | 200 | | 1 | "
               + "Response Content-Type does not match expected one. Expecting application/json but got null",
         "200 | | 200 | text/plain | 0 | ", "404 | application/json | 200 | application/json | 1 | "
               + "Response HttpStatus does not match expected one. Expecting 404 but got 200" })
   void theExpectedResponseIsCheckedBeforeTheSchemaWhenAsked(String expectedStatus, String expectedType, int status,
         String contentType, int code, String message) throws IOException {
      given(ResourceType.OPEN_API_SPEC, contractOf("openapi-pastry"));
      Response expected = new Response();
      expected.setStatus(expectedStatus);
      expected.setMediaType(expectedType);
      responses.expected = expected;

      assertTest(new OpenAPITestRunner(resources, responses, true), "GET /pastry/{name}", status, contentType, PASTRY,
            code, message);
   }

   @Test
   void withoutExpectedResponseOnlyTheSchemaIsChecked() throws IOException {
      given(ResourceType.OPEN_API_SPEC, contractOf("openapi-pastry"));

      assertTest(new OpenAPITestRunner(resources, responses, true), "GET /pastry/{name}", 200, "application/json",
            "{\"name\":3}", FAILURE, "integer found, string expected");
   }

   @Test
   void theExternalReferencesOfTheContractAreResolvedFromTheResourceUrl() throws IOException {
      given(ResourceType.OPEN_API_SPEC,
            Files.readString(Path.of(FIXTURES, "weather-forecast-openapi-relative-ref.yaml"), StandardCharsets.UTF_8));
      OpenAPITestRunner runner = new OpenAPITestRunner(resources, responses, false);
      runner.setResourceUrl(new File(FIXTURES).toURI().toString());

      assertTest(runner, "GET /forecast/{region}", 200, "application/json",
            "{\"region\":\"north\",\"temp\":\"cold\",\"weather\":\"snowy\",\"visibility\":25}", FAILURE,
            "string found, number expected");
   }

   private void assertTest(OpenAPITestRunner runner, String operationName, int status, String contentType, String body,
         int code, String message) throws IOException {
      FakeResponse response = response(status, contentType, body);

      assertEquals(code, runner.extractTestReturnCode(service, operation(operationName), request, response, body));
      assertEquals(message == null ? "" : message.replace("\\n", "\n") + "\n",
            runner.extractTestReturnMessage(service, operation(operationName), request, response));
   }

   private void given(ResourceType type, String contract) {
      resources.main = List.of(resource(type, contract));
   }

   private static FakeResponse response(int status, String contentType, String body) {
      HttpHeaders headers = new HttpHeaders();
      if (contentType != null) {
         headers.setContentType(contentType);
      }
      return new FakeResponse(new HttpStatusCode(status), headers,
            (body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
   }

   private static Resource resource(ResourceType type, String content) {
      Resource resource = new Resource();
      resource.setType(type);
      resource.setContent(content);
      return resource;
   }

   private static Service service() {
      Service service = new Service();
      service.setId("pastry-id");
      service.setName("API Pastry");
      return service;
   }

   private static Operation operation(String name) {
      Operation operation = new Operation();
      operation.setName(name);
      return operation;
   }

   private static Request request() {
      Request request = new Request();
      request.setResponseId("eclair-cafe-response");
      return request;
   }

   /** A contract of {@code openapi-test-runner-validation.yaml}, by its name. */
   private static String contractOf(String example) throws IOException {
      String marker = "# example: " + example + " ";
      return Arrays
            .stream(Files.readString(Path.of(FIXTURES, "openapi-test-runner-validation.yaml"), StandardCharsets.UTF_8)
                  .split("\n---\n"))
            .filter(document -> document.contains(marker)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException(example + " is not in the fixture"));
   }

   /** The resources of the service, the main ones apart from all of them. */
   private static class FakeResources implements ResourceReader {
      List<Resource> main = List.of();
      List<Resource> all = List.of();

      @Override
      public List<Resource> findByServiceId(String serviceId) {
         return all;
      }

      @Override
      public List<Resource> findByServiceIdAndType(String serviceId, ResourceType type) {
         return all.stream().filter(r -> r.getType() == type).toList();
      }

      @Override
      public List<Resource> findMainByServiceId(String serviceId) {
         return main;
      }
   }

   /** The response the request expects, if any. */
   private class FakeResponses implements ResponseReader {
      Response expected;

      @Override
      public Response findById(String id) {
         return request.getResponseId().equals(id) ? expected : null;
      }
   }

   /** A response of an implementation under test. */
   private record FakeResponse(HttpStatusCode status, HttpHeaders headers, byte[] body) implements ClientHttpResponse {

      @Override
      public HttpStatusCode getStatusCode() {
         return status;
      }

      @Override
      public HttpHeaders getHeaders() {
         return headers;
      }

      @Override
      public InputStream getBody() {
         return new ByteArrayInputStream(body);
      }

      @Override
      public void close() {
         // Nothing to release.
      }
   }
}
