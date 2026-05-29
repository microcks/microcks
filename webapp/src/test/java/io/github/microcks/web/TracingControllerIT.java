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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.EntityExchangeResult;
import org.springframework.test.context.TestPropertySource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for TracingController that tests the full tracing flow: 1. Import a service (which should generate
 * traces) 2. Call service operations (which should generate traces) 3. Retrieve and verify traces through the
 * TracingController
 *
 * @author microcks-team
 */
@TestPropertySource(properties = { "otel.traces.exporter=none", "otel.metrics.exporter=none", "otel.logs.exporter=none",
      "otel.instrumentation.annotations.enabled=true", "otel.sdk.disabled=false",
      "otel.instrumentation.spring-web.enabled=false" })
class TracingControllerIT extends AbstractBaseIT {

   record SseFrame(String key, String value) {
   }

   @Autowired
   private SpanStorageService spanStorageService;

   private List<SseFrame> sseFrames;
   private ExecutorService sseClientExecutor;
   private Thread sseClientThread;

   @BeforeEach
   void setUp() {
      // Clear any existing traces before each test
      spanStorageService.clearAll();

      // Upload a test artifact that will generate traces when invoked
      uploadArtifactFile("target/test-classes/io/github/microcks/util/openapi/pastry-with-details-openapi.yaml", true);

      sseFrames = new ArrayList<>();
      sseClientExecutor = Executors.newSingleThreadExecutor();
   }

   @AfterEach
   void tearDown() {
      if (sseClientExecutor != null) {
         sseClientExecutor.shutdownNow();
      }
   }

   @Test
   @DisplayName("Should retrieve spans for a specific trace ID")
   void shouldRetrieveSpansForTraceId() {
      // Given - Call GET /rest/pastry-details/1.0.0/pastry to generate traces
      EntityExchangeResult<String> response = getForEntity("/rest/pastry-details/1.0.0/pastry", String.class);
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);

      // Get all trace IDs GET /api/traces that returns a List<String>
      EntityExchangeResult<Set<String>> traceIdsResponse = exchange("/api/traces", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {
            });

      assertThat(traceIdsResponse.getStatus()).isEqualTo(HttpStatus.OK);
      if (traceIdsResponse.getResponseBody() != null && !traceIdsResponse.getResponseBody().isEmpty()) {
         // Get spans for the first trace ID
         String firstTraceId = traceIdsResponse.getResponseBody().iterator().next();
         EntityExchangeResult<List<Object>> spansResponse = exchange("/api/traces/" + firstTraceId + "/spans",
               HttpMethod.GET, null, new ParameterizedTypeReference<>() {
               });

         // Then
         assertThat(spansResponse.getStatus()).isEqualTo(HttpStatus.OK);
         assertThat(spansResponse.getResponseBody()).isNotEmpty();


         // Verify span data structure - we expect at least one span with name "GET /rest/pastry-details/1.0.0/pastry"
         System.out.println(spansResponse.getResponseBody());
         assertThat(spansResponse.getResponseBody().toString()).contains("/rest/pastry-details/1.0.0/pastry")
               .contains("processInvocation").contains("explain-trace")
               .contains("Selected dispatcher and rules for this invocation")
               .contains("Received REST invocation GET /rest/pastry-details/1.0.0/pastry");
      }
   }

   @Test
   @DisplayName("Should return 404 for non-existent trace ID")
   void shouldReturn404ForNonExistentTraceId() {
      // When
      EntityExchangeResult<List<SpanData>> response = exchange("/api/traces/non-existent-trace-id/spans",
            HttpMethod.GET, null, new ParameterizedTypeReference<>() {
            });

      // Then
      assertThat(response.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
   }

   @Test
   @DisplayName("Should retrieve spans by operation name")
   void shouldRetrieveSpansByOperation() {
      // Given - Call GET /rest/pastry-details/1.0.0/pastry to generate traces
      EntityExchangeResult<String> response = getForEntity("/rest/pastry-details/1.0.0/pastry", String.class);
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);


      // Try to query spans by operation (this may return empty if no specific operation traces exist)
      EntityExchangeResult<List<List<Object>>> operationSpansResponse = exchange(
            "/api/traces/operations?serviceName=pastry-details&operationName=GET /pastry", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {
            });

      // Should get a response containing spans
      assertThat(operationSpansResponse.getStatus()).isEqualTo(HttpStatus.OK);
   }

   @Test
   @DisplayName("Should clear all traces")
   void shouldClearAllTraces() {
      // Given - Call GET /rest/pastry-details/1.0.0/pastry to generate traces
      EntityExchangeResult<String> response = getForEntity("/rest/pastry-details/1.0.0/pastry", String.class);
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);

      // Check if traces exist (but don't fail if they don't)
      exchange("/api/traces", HttpMethod.GET, null, new ParameterizedTypeReference<Set<String>>() {
      });

      // When - Clear all traces
      EntityExchangeResult<String> clearResponse = exchange("/api/traces", HttpMethod.DELETE, null, String.class);

      // Then
      assertThat(clearResponse.getStatus()).isEqualTo(HttpStatus.OK);
      assertThat(clearResponse.getResponseBody()).contains("All traces and spans have been cleared");

      // Verify traces are cleared
      EntityExchangeResult<Set<String>> tracesAfterClear = exchange("/api/traces", HttpMethod.GET, null,
            new ParameterizedTypeReference<>() {
            });

      assertThat(tracesAfterClear.getStatus()).isEqualTo(HttpStatus.OK);
      assertThat(tracesAfterClear.getResponseBody()).isEmpty();
   }

   @Test
   @DisplayName("Should stream traces via SSE endpoint")
   void shouldStreamTracesViaSseEndpoint() throws Exception {
      // Define the runnable for the SSE client to listen to the stream
      Runnable sseClientRunnable = () -> {
         streamingRestClient.get().uri(
               "/api/traces/operations/stream?serviceName=pastry-details&operationName=GET /pastry&clientAddress=.*")
               .accept(MediaType.TEXT_EVENT_STREAM).exchange((request, response) -> {

                  String line;
                  try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(response.getBody()));) {
                     while ((line = bufferedReader.readLine()) != null) {
                        parseAndStoreSseFrame(line, sseFrames);
                     }
                  } catch (IOException e) {
                     System.err.println("Caught exception while reading SSE response: " + e.getMessage());
                  }
                  return null;
               });
      };

      // Start the SSE client in background
      sseClientExecutor.execute(sseClientRunnable);

      // Wait a bit for the SSE connection to be established
      Thread.sleep(100);

      // Generate traces by calling the REST endpoint
      EntityExchangeResult<String> response = getForEntity("/rest/pastry-details/1.0.0/pastry", String.class);
      assertThat(response.getStatus()).isEqualTo(HttpStatus.OK);

      // Wait for SSE events to be received
      Thread.sleep(500);

      // Verify that we received SSE events
      assertThat(sseFrames).isNotEmpty();

      // Check that the first event is a heartbeat
      SseFrame firstFrame = sseFrames.getFirst();
      assertThat(firstFrame.key).isEqualTo("event");
      assertThat(firstFrame.value).isEqualTo("heartbeat");
      // Check that we have at least one trace event
      assertThat(sseFrames).anyMatch(frame -> "event".equals(frame.key) && "trace".equals(frame.value));
      // Check that at least one trace event contains expected span info
      assertThat(sseFrames).anyMatch(frame -> "data".equals(frame.key) && frame.value.contains("processInvocation"));
   }

   private void parseAndStoreSseFrame(String line, List<SseFrame> sseFrames) {
      if (line.contains(":")) {
         String key = line.substring(0, line.indexOf(':'));
         String value = line.substring(line.indexOf(':') + 1).trim();
         sseFrames.add(new SseFrame(key, value));
      }
   }
}
