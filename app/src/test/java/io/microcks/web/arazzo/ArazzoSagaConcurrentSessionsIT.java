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

import io.microcks.repository.workflow.WorkflowDocumentRepository;
import io.microcks.repository.workflow.WorkflowSessionRecordedCallRepository;
import io.microcks.repository.workflow.WorkflowSessionRepository;
import io.microcks.repository.workflow.WorkflowMetadataRepository;
import io.microcks.repository.workflow.WorkflowResultRepository;
import io.microcks.testsupport.http.HttpEntity;
import io.microcks.testsupport.http.HttpHeaders;
import io.microcks.testsupport.http.HttpMethod;
import io.microcks.testsupport.http.MediaType;
import io.microcks.testsupport.http.ResponseEntity;
import io.microcks.web.AbstractBaseIT;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two tests run the order saga AT THE SAME TIME against the mocks, each in its own session: session A sets
 * chargePayment to fail with card refused and expects the compensation, session B sets nothing and expects the normal
 * saga. Their calls are interleaved step by step with a barrier. The injection must stay in session A.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSagaConcurrentSessionsIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final String COMPENSATION = "compensatePaymentRefused";
   private static final String NORMAL = "placeOrderSaga";
   private static final String CARD_REFUSED = "{\"chargePayment\": \"card refused\"}";
   private static final int ROUNDS = 5;
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private WorkflowDocumentRepository workflowDocumentRepository;

   @Inject
   private WorkflowMetadataRepository workflowMetadataRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   @Inject
   private WorkflowSessionRepository workflowSessionRepository;

   @Inject
   private WorkflowSessionRecordedCallRepository workflowSessionRecordedCallRepository;

   @TempDir
   private Path uploads;

   private final ExecutorService sagas = Executors.newFixedThreadPool(2);

   @BeforeEach
   void orderSagaIsUploadedAndLinked() throws IOException {
      for (String api : List.of("order-api", "stock-api", "payment-api")) {
         uploadArtifactFile(example("saga-compensation.contracts.yaml", api), true);
      }
      uploadArtifactFile(example("saga-compensation.arazzo.yaml", "order-saga"), true);
      uploadArtifactFile(example("saga-compensation.arazzo.yaml", "order-saga-metadata"), false);
   }

   @AfterEach
   void deleteDocumentsSessionsAndVerifications() {
      sagas.shutdownNow();
      workflowDocumentRepository.deleteAll();
      workflowMetadataRepository.deleteAll();
      workflowResultRepository.deleteAll();
      workflowSessionRepository.deleteAll();
      workflowSessionRecordedCallRepository.deleteAll();
   }

   /**
    * A's chargePayment is refused and B's is charged, every time; A's compensation and B's normal saga both pass.
    */
   @Test
   void injectionStaysInItsSessionWhenTwoSagasRunAtTheSameTime() throws Exception {
      for (int round = 1; round <= ROUNDS; round++) {
         String a = openSession(COMPENSATION, CARD_REFUSED);
         String b = openSession(NORMAL, null);

         Map<String, Integer> charged = runBothSagas(a, b);

         assertEquals(Map.of("A", 402, "B", 201), charged, "round " + round);
         assertEquals("SUCCESS", verify(a).get("status").asText(), "round " + round + ": " + verify(a));
         assertEquals("SUCCESS", verify(b).get("status").asText(), "round " + round + ": " + verify(b));
      }
   }

   /**
    * The deliberate mistake: saga B sends its calls with A's session id. B's chargePayment is then refused, as it is
    * A's session; A's verification fails on the calls of B it did not expect, and B's on the steps it never received.
    * The calls of each step of both sagas land in A at the same instant.
    */
   @Test
   void callsSentWithTheSessionIdOfAnotherTestFailBothVerifications() throws Exception {
      String a = openSession(COMPENSATION, CARD_REFUSED);
      String b = openSession(NORMAL, null);

      Map<String, Integer> charged = runBothSagas(a, a);

      assertEquals(Map.of("A", 402, "B", 402), charged);
      JsonNode verificationOfA = verify(a);
      assertEquals("FAILURE", verificationOfA.get("status").asText(), verificationOfA.toPrettyString());
      List<String> unexpected = new ArrayList<>();
      verificationOfA.get("session").get("calls").forEach(call -> {
         if (call.hasNonNull("failure")) {
            unexpected.add(call.get("failure").asText().replaceAll(" (after|before) .*", ""));
         }
      });
      unexpected.sort(String::compareTo);
      assertEquals(
            List.of("unexpected call `chargePayment`", "unexpected call `confirmOrder`",
                  "unexpected call `createOrder`", "unexpected call `reserveStock`"),
            unexpected, verificationOfA.toPrettyString());
      JsonNode verificationOfB = verify(b);
      assertEquals("FAILURE", verificationOfB.get("status").asText(), verificationOfB.toPrettyString());
      assertEquals(List.of("step `createOrder` missing", "step `reserveStock` missing", "step `chargePayment` missing",
            "step `confirmOrder` missing"), messages(verificationOfB));
   }

   /**
    * Saga A (the compensation) and saga B (the normal saga) run in two threads; a barrier makes them call the mocks
    * step after step together, at the same instant. Each names the session given to it.
    * @return The status chargePayment answered to each saga, by saga
    */
   private Map<String, Integer> runBothSagas(String sessionOfA, String sessionOfB) throws Exception {
      CyclicBarrier together = new CyclicBarrier(2);
      Map<String, Integer> charged = new ConcurrentHashMap<>();
      List<Runnable> sagaA = List.of(() -> placeOrder(sessionOfA), () -> reserveStock(sessionOfA),
            () -> charged.put("A", chargePayment(sessionOfA)),
            () -> assertEquals(200, callMocks(sessionOfA, HttpMethod.DELETE, "Stock+API", "/reservations/r-9", null)),
            () -> assertEquals(200, callMocks(sessionOfA, HttpMethod.POST, "Order+API", "/orders/o-42/cancel", null)));
      List<Runnable> sagaB = List.of(() -> placeOrder(sessionOfB), () -> reserveStock(sessionOfB),
            () -> charged.put("B", chargePayment(sessionOfB)),
            () -> assertEquals(200, callMocks(sessionOfB, HttpMethod.POST, "Order+API", "/orders/o-42/confirm", null)));
      Future<?> a = sagas.submit(() -> play(sagaA, together));
      Future<?> b = sagas.submit(() -> play(sagaB, together));
      a.get(30, TimeUnit.SECONDS);
      b.get(30, TimeUnit.SECONDS);
      return Map.copyOf(charged);
   }

   /**
    * Play the steps of one saga, step after step with the other saga: at each step, both wait for each other, then both
    * call at the same instant, and both wait for the two answers before the next step.
    */
   private static Void play(List<Runnable> steps, CyclicBarrier together) throws Exception {
      for (int step = 0; step < 5; step++) {
         together.await(10, TimeUnit.SECONDS);
         if (step < steps.size()) {
            steps.get(step).run();
         }
         together.await(10, TimeUnit.SECONDS);
      }
      return null;
   }

   private void placeOrder(String sessionId) {
      assertEquals(201, callMocks(sessionId, HttpMethod.POST, "Order+API", "/orders",
            "{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0}"));
   }

   private void reserveStock(String sessionId) {
      assertEquals(201, callMocks(sessionId, HttpMethod.POST, "Stock+API", "/reservations", "{\"orderId\": \"o-42\"}"));
   }

   private int chargePayment(String sessionId) {
      return callMocks(sessionId, HttpMethod.POST, "Payment+API", "/payments",
            "{\"orderId\": \"o-42\", \"amount\": 25.0}");
   }

   /** The failure messages of the steps of a verification, in the order of its workflow. */
   private static List<String> messages(JsonNode verification) {
      List<String> messages = new ArrayList<>();
      verification.get("steps").forEach(step -> messages.add(step.path("message").asText()));
      return messages;
   }

   /** Open a session on a workflow of Order saga 1.0.0, with the failures given as JSON, or none when null. */
   private String openSession(String workflowId, String failures) throws Exception {
      String body = "{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"" + workflowId + "\""
            + (failures == null ? "" : ", \"failures\": " + failures) + "}";
      ResponseEntity<String> opened = restTemplate.exchange("/api/arazzo/sessions", HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);
      assertEquals(201, opened.getStatusCode().value(), opened.getBody());
      return JSON.readTree(opened.getBody()).get("id").asText();
   }

   private JsonNode verify(String sessionId) throws Exception {
      ResponseEntity<String> verified = restTemplate.exchange(
            "/api/arazzo/sessions/" + sessionId + "/verification", HttpMethod.POST,
            new HttpEntity<>(jsonHeaders()), String.class);
      assertEquals(200, verified.getStatusCode().value(), verified.getBody());
      return JSON.readTree(verified.getBody());
   }

   /** The saga service calls the mocks of an API 1.0.0, naming a session in the x-microcks-session header. */
   private int callMocks(String sessionId, HttpMethod method, String api, String path, String body) {
      HttpHeaders headers = jsonHeaders();
      headers.set("x-microcks-session", sessionId);
      return restTemplate
            .exchange("/rest/" + api + "/1.0.0" + path, method, new HttpEntity<>(body, headers), String.class)
            .getStatusCode().value();
   }

   private static HttpHeaders jsonHeaders() {
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setAccept(List.of(MediaType.APPLICATION_JSON));
      return headers;
   }

   /** Writes the example of a multi-document fixture to its own file, as the API developer would upload it. */
   private String example(String fixture, String example) throws IOException {
      String marker = "# example: " + example + " ";
      String document = Arrays.stream(Files.readString(Path.of(FIXTURES + fixture)).split("\n---\n"))
            .filter(candidate -> candidate.contains(marker)).findFirst().orElseThrow();
      Path file = uploads.resolve(example + (fixture.contains("arazzo") ? ".arazzo.yaml" : ".yaml"));
      Files.writeString(file, document, StandardCharsets.UTF_8);
      return file.toString();
   }
}
