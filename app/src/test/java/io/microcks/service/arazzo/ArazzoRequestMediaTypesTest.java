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
package io.microcks.service.arazzo;

import io.microcks.util.ObjectMapperFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Acceptance tests of the media types an operation accepts for its request body, read in the contract of its API.
 * <pre>
 * Feature: The request media types of an operation
 *   Scenario Outline: OpenAPI 3
 *     Given an OpenAPI contract whose operation declares a request body with <media types>
 *     Then the operation accepts <media types>, in the order of the contract
 *   Scenario: a request body that is a reference to the components of the contract
 *   Scenario: an operation without request body, or that the contract does not describe, or no contract
 *   Scenario: Swagger 2, consumes of the operation, else the consumes of the contract
 * </pre>
 * @author SebastienDegodez
 */
class ArazzoRequestMediaTypesTest {

   private static final String OPENAPI = """
         openapi: 3.0.2
         info: {title: Notes, version: 1.0.0}
         paths:
           /notes:
             get:
               responses: {"200": {description: ok}}
             post:
               requestBody:
                 content:
                   application/json: {}
                   application/xml: {}
               responses: {"201": {description: ok}}
             put:
               requestBody:
                 $ref: '#/components/requestBodies/Note'
               responses: {"200": {description: ok}}
             patch:
               requestBody:
                 content:
                   text/plain: {}
               responses: {"200": {description: ok}}
         components:
           requestBodies:
             Note:
               content:
                 application/json: {}
         """;

   private static final String SWAGGER = """
         swagger: "2.0"
         info: {title: Notes, version: 1.0.0}
         consumes:
           - application/json
         paths:
           /notes:
             post:
               responses: {"201": {description: ok}}
             put:
               consumes:
                 - application/xml
                 - text/plain
               responses: {"200": {description: ok}}
         """;

   @ParameterizedTest
   @CsvSource({ "POST, '/notes', 'application/json, application/xml'", "PUT, '/notes', 'application/json'",
         "PATCH, '/notes', 'text/plain'", "GET, '/notes', ''", "DELETE, '/notes', ''", "POST, '/unknown', ''" })
   void anOpenApiOperationAcceptsTheMediaTypesOfItsRequestBody(String method, String path, String expected)
         throws IOException {
      assertEquals(expected.isEmpty() ? List.of() : List.of(expected.split(", ")), mediaTypes(OPENAPI, method, path));
   }

   @ParameterizedTest
   @CsvSource({ "POST, 'application/json'", "PUT, 'application/xml, text/plain'" })
   void aSwaggerOperationAcceptsItsConsumesElseThoseOfTheContract(String method, String expected) throws IOException {
      assertEquals(List.of(expected.split(", ")), mediaTypes(SWAGGER, method, "/notes"));
   }

   @Test
   void anAPIWithoutContractDeclaresNoMediaType() {
      assertEquals(List.of(), ArazzoRequestMediaTypes.of(null, "POST", "/notes"));
   }

   private static List<String> mediaTypes(String contract, String method, String path) throws IOException {
      return ArazzoRequestMediaTypes.of(ObjectMapperFactory.getYamlObjectMapper().readTree(contract), method, path);
   }
}
