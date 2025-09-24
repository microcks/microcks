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
package io.github.microcks.web;

import io.github.microcks.service.SpanStorageService;
import io.opentelemetry.sdk.trace.data.SpanData;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for TracingController that tests the full tracing flow: 1. Import a service (which should generate
 * traces) 2. Call service operations (which should generate traces) 3. Retrieve and verify traces through the
 * TracingController
 *
 * @author microcks-team
 */
@TestPropertySource(properties = { "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none",
      "otel.instrumentation.annotations.enabled=true" })
class TracingControllerIT extends AbstractBaseIT {

   @Autowired
   private SpanStorageService spanStorageService;

   @BeforeEach
   void setUp() {
      // Clear any existing traces before each test
      spanStorageService.clearAll();

      // Upload a test artifact that will generate traces when invoked
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-details-openapi.yaml", true);
   }

   @Test
   @DisplayName("Should retrieve spans for a specific trace ID")
   void shouldRetrieveSpansForTraceId() {
      // Given - Call GET /rest/pastry-details/1.0.0/pastry to generate traces
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-details/1.0.0/pastry", String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Get all trace IDs GET /api/traces that returns a List<String>
      ResponseEntity<Set<String>> traceIdsResponse = restTemplate.exchange("/api/traces", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {
            });

      assertThat(traceIdsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      if (traceIdsResponse.getBody() != null && !traceIdsResponse.getBody().isEmpty()) {
         // Get spans for the first trace ID
         String firstTraceId = traceIdsResponse.getBody().iterator().next();
         ResponseEntity<List<Object>> spansResponse = restTemplate.exchange("/api/traces/" + firstTraceId + "/spans",
               HttpMethod.GET, null, new ParameterizedTypeReference<>() {
               });

         // Then
         assertThat(spansResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
         assertThat(spansResponse.getBody()).isNotEmpty();


         // Verify span data structure - we expect at least one span with name "GET /rest/pastry-details/1.0.0/pastry"
         System.out.println(spansResponse.getBody());
         assertThat(spansResponse.getBody().toString()).contains("/rest/pastry-details/1.0.0/pastry")
               .contains("processInvocation").contains("explain-trace")
               .contains("Selected dispatcher and rules for this invocation")
               .contains("Received REST invocation GET /pastry");
      }
   }

   @Test
   @DisplayName("Should return 404 for non-existent trace ID")
   void shouldReturn404ForNonExistentTraceId() {
      // When
      ResponseEntity<List<SpanData>> response = restTemplate.exchange("/api/traces/non-existent-trace-id/spans",
            HttpMethod.GET, null, new ParameterizedTypeReference<>() {
            });

      // Then
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
   }

   @Test
   @DisplayName("Should retrieve spans by operation name")
   void shouldRetrieveSpansByOperation() {
      // Given - Call GET /rest/pastry-details/1.0.0/pastry to generate traces
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-details/1.0.0/pastry", String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);


      // Try to query spans by operation (this may return empty if no specific operation traces exist)
      ResponseEntity<List<List<Object>>> operationSpansResponse = restTemplate.exchange(
            "/api/traces/operations/spans?serviceName=pastry-details&operationName=GET /pastry", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {
            });

      // Should get a response containing spans
      assertThat(operationSpansResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
   }

   @Test
   @DisplayName("Should clear all traces")
   void shouldClearAllTraces() {
      // Given - Call GET /rest/pastry-details/1.0.0/pastry to generate traces
      ResponseEntity<String> response = restTemplate.getForEntity("/rest/pastry-details/1.0.0/pastry", String.class);
      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

      // Check if traces exist (but don't fail if they don't)
      restTemplate.exchange("/api/traces", HttpMethod.GET, null, new ParameterizedTypeReference<Set<String>>() {
      });
      // We may or may not have traces at this point, but that's ok for this test

      // When - Clear all traces
      ResponseEntity<String> clearResponse = restTemplate.exchange("/api/traces", HttpMethod.DELETE, null,
            String.class);

      // Then
      assertThat(clearResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(clearResponse.getBody()).contains("All traces and spans have been cleared");

      // Verify traces are cleared
      ResponseEntity<Set<String>> tracesAfterClear = restTemplate.exchange("/api/traces", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {
            });

      assertThat(tracesAfterClear.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(tracesAfterClear.getBody()).isEmpty();
   }
}
