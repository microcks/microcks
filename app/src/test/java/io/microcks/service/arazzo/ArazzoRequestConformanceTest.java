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

import io.microcks.artifact.arazzo.ArazzoReceivedRequest;
import io.microcks.artifact.openapi.OpenAPISchemaValidator;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the conformance of a request an application sent to the mocks with the contract of the API it called: its
 * required parameters and its request body.
 * @author SebastienDegodez
 */
class ArazzoRequestConformanceTest {

   private static final String REFUND = "/refunds/{paymentId}";
   private static JsonNode contract;

   @BeforeAll
   static void readTheRefundApi() throws IOException {
      try (InputStream stream = ArazzoRequestConformanceTest.class
            .getResourceAsStream("/io/microcks/service/arazzo/request-conformance.openapi.yaml")) {
         contract = OpenAPISchemaValidator
               .getJsonNodeForSchema(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
   }

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = {
         // A request with everything the contract requires.
         "reason=duplicate | k-1 | application/json | '{\"amount\": 25.0}' | ''",
         "reason=duplicate&notify=true | k-1 | application/json;charset=UTF-8 | '{\"amount\": 25.0}' | ''",
         // A required parameter, of the operation or of its path, is missing.
         "null | k-1 | application/json | '{\"amount\": 25.0}' | required query parameter `reason` is missing",
         "reason=duplicate | null | application/json | '{\"amount\": 25.0}' | "
               + "required header parameter `Idempotency-Key` is missing",
         // The body is required, or does not follow its schema.
         "reason=duplicate | k-1 | application/json | null | the request body is required",
         "reason=duplicate | k-1 | application/json | '  ' | the request body is required",
         "reason=duplicate | k-1 | application/json | '{\"amount\": \"25.0\"}' | "
               + "the request body does not follow its schema at /amount: string found, number expected",
         "reason=duplicate | k-1 | application/json | '{amount' | the request body is not JSON",
         // A body of a content type the contract does not describe as JSON is not validated.
         "reason=duplicate | k-1 | application/xml | '<refund/>' | ''",
         "reason=duplicate | k-1 | null | '<refund/>' | ''" })
   void aRefundRequestIsCheckedAgainstTheContract(String query, String idempotencyKey, String contentType, String body,
         String violations) {
      Map<String, List<String>> headers = idempotencyKey == null ? Map.of()
            : Map.of("idempotency-key", List.of(idempotencyKey));
      ArazzoReceivedRequest request = new ArazzoReceivedRequest(REFUND, "/refunds/p-3", query, headers, body);

      assertEquals(violations, String.join(", ",
            ArazzoRequestConformance.violations(contract, "POST", REFUND, request, contentType, null)));
   }

   @Test
   void anOperationWithoutParametersNorBodyHasNothingToCheck() {
      ArazzoReceivedRequest request = new ArazzoReceivedRequest(REFUND, "/refunds/p-3", null,
            Map.of("Idempotency-Key", List.of("k-1")), null);

      assertEquals(List.of(), ArazzoRequestConformance.violations(contract, "DELETE", REFUND, request, null, null));
   }

   /** A body is validated only against a JSON content with a schema. */
   @ParameterizedTest
   @CsvSource({ "/notes", "/memos" })
   void aBodyNotDescribedAsJsonWithASchemaIsNotValidated(String path) {
      ArazzoReceivedRequest request = new ArazzoReceivedRequest(path, path, null, Map.of(), "{\"note\": 1}");

      assertEquals(List.of(),
            ArazzoRequestConformance.violations(contract, "POST", path, request, "application/json", null));
   }

   @Test
   void anOperationTheContractDoesNotDescribeHasNothingToCheck() {
      ArazzoReceivedRequest request = new ArazzoReceivedRequest("/payments", "/payments", null, Map.of(), null);

      assertEquals(List.of(),
            ArazzoRequestConformance.violations(contract, "POST", "/payments", request, "application/json", null));
      assertEquals(List.of(), ArazzoRequestConformance.violations(null, "POST", "/payments", request, null, null));
   }
}
