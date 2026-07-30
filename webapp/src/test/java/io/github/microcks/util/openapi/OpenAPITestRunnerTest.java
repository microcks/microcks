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

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.Resource;
import io.github.microcks.domain.ResourceType;
import io.github.microcks.domain.Response;
import io.github.microcks.domain.ResponseExampleComparisonMode;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestReturn;
import io.github.microcks.repository.ResourceRepository;
import io.github.microcks.repository.ResponseRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OpenAPITestRunnerTest {

   private static final String OPEN_API_SPEC = """
         {
           "openapi": "3.0.3",
           "paths": {
             "/items": {
               "get": {
                 "responses": {
                   "200": {
                     "content": {
                       "application/json": {
                         "schema": {
                           "type": "object",
                           "properties": {
                             "items": {
                               "type": "array",
                               "items": {
                                 "type": "object",
                                 "properties": {
                                   "id": {"type": "number"},
                                   "name": {"type": "string"}
                                 }
                               }
                             }
                           }
                         }
                       }
                     }
                   }
                 }
               }
             }
           },
           "components": {
             "schemas": {}
           }
         }
         """;

   @Mock
   private ResourceRepository resourceRepository;
   @Mock
   private ResponseRepository responseRepository;
   @Mock
   private ClientHttpResponse httpResponse;

   private Service service;
   private Operation operation;
   private Request request;
   private Response expectedResponse;

   @BeforeEach
   void setUp() throws IOException {
      service = new Service();
      service.setId("items-service");
      service.setName("Items service");

      operation = new Operation();
      operation.setName("GET /items");
      operation.setMethod(HttpMethod.GET.name());

      request = new Request();
      request.setResponseId("response-1");

      expectedResponse = new Response();
      expectedResponse.setStatus("200");
      expectedResponse.setMediaType(MediaType.APPLICATION_JSON_VALUE);

      Resource resource = new Resource();
      resource.setType(ResourceType.OPEN_API_SPEC);
      resource.setContent(OPEN_API_SPEC);

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);

      when(resourceRepository.findMainByServiceId(service.getId())).thenReturn(List.of(resource));
      when(responseRepository.findById(request.getResponseId())).thenReturn(Optional.of(expectedResponse));
      when(httpResponse.getStatusCode()).thenReturn(HttpStatus.OK);
      when(httpResponse.getHeaders()).thenReturn(headers);
   }

   @Test
   void shouldFailStrictComparisonWhenActualResponseHasAdditionalContent() {
      expectedResponse.setContent("""
            {"items":[{"id":1}]}
            """);
      OpenAPITestRunner runner = new OpenAPITestRunner(resourceRepository, responseRepository, true,
            ResponseExampleComparisonMode.STRICT);

      int result = runner.extractTestReturnCode(service, operation, request, httpResponse, """
            {"items":[{"id":1}],"traceId":"123"}
            """);

      assertEquals(TestReturn.FAILURE_CODE, result);
      assertEquals("Response body does not match response example using STRICT comparison\n",
            runner.extractTestReturnMessage(service, operation, request, httpResponse));
   }

   @Test
   void shouldPassLenientComparisonWithAdditionalObjectFieldsAndArrayElements() {
      expectedResponse.setContent("""
            {"items":[{"id":1}]}
            """);
      OpenAPITestRunner runner = new OpenAPITestRunner(resourceRepository, responseRepository, true,
            ResponseExampleComparisonMode.LENIENT);

      int result = runner.extractTestReturnCode(service, operation, request, httpResponse, """
            {"items":[{"id":2,"name":"two"},{"id":1,"name":"one"}],"traceId":"123"}
            """);

      assertEquals(TestReturn.SUCCESS_CODE, result);
      assertEquals("", runner.extractTestReturnMessage(service, operation, request, httpResponse));
   }

   @Test
   void shouldPassStrictComparisonForReorderedArrays() {
      expectedResponse.setContent("""
            {"items":[{"id":1,"name":"one"},{"id":2,"name":"two"}]}
            """);
      OpenAPITestRunner runner = new OpenAPITestRunner(resourceRepository, responseRepository, true,
            ResponseExampleComparisonMode.STRICT);

      int result = runner.extractTestReturnCode(service, operation, request, httpResponse, """
            {"items":[{"name":"two","id":2},{"name":"one","id":1}]}
            """);

      assertEquals(TestReturn.SUCCESS_CODE, result);
   }

   @Test
   void shouldPreserveExistingSchemaOnlyBehaviorWhenComparisonIsNotRequested() {
      expectedResponse.setContent("""
            {"items":[{"id":1}]}
            """);
      OpenAPITestRunner runner = new OpenAPITestRunner(resourceRepository, responseRepository, true);

      int result = runner.extractTestReturnCode(service, operation, request, httpResponse, """
            {"items":[{"id":2}],"traceId":"123"}
            """);

      assertEquals(TestReturn.SUCCESS_CODE, result);
   }
}
