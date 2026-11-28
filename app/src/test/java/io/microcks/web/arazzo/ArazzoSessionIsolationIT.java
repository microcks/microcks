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

import io.microcks.domain.ServiceState;
import io.microcks.domain.workflow.WorkflowSessionRecordedCall;
import io.microcks.repository.ServiceStateRepository;
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
import org.bson.Document;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two tests of an order saga service run placeOrderSaga at the same time, each in its own session: one for customer
 * c-7, one for customer c-8. Their calls to the mocks of Order, Stock and Payment API 1.0.0 come from two threads, in
 * an interleaving fixed by latches. Each session records, answers, keeps and verifies only its own calls, whether it is
 * named in the x-microcks-session header or in the W3C baggage, and a test naming the wrong session is caught.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSessionIsolationIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final ObjectMapper JSON = new ObjectMapper();
   private static final List<String> STEPS = List.of("createOrder", "reserveStock", "chargePayment", "confirmOrder");

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

   @Inject
   private ServiceStateRepository serviceStateRepository;

   @TempDir
   private Path uploads;

   @BeforeEach
   void orderSagaIsUploadedAndLinked() throws IOException {
      for (String api : List.of("order-api", "stock-api", "payment-api")) {
         uploadArtifactFile(example("stateful-saga.contracts.yaml", api), true);
      }
      uploadArtifactFile(example("stateful-saga.arazzo.yaml", "order-saga"), true);
      uploadArtifactFile(example("stateful-saga.arazzo.yaml", "order-saga-metadata"), false);
   }

   @AfterEach
   void deleteDocumentsSessionsAndState() {
      workflowDocumentRepository.deleteAll();
      workflowMetadataRepository.deleteAll();
      workflowResultRepository.deleteAll();
      workflowSessionRepository.deleteAll();
      workflowSessionRecordedCallRepository.deleteAll();
      serviceStateRepository.deleteAll();
   }

   /**
    * Two sagas at the same time, session A for c-7 named in the header, session B for c-8 named in the baggage: each
    * verification passes on its own 4 calls, each answer carries the customer of its own session, and each session
    * keeps the outputs of its own steps.
    */
   @Test
   void twoSagasRunAtTheSameTimeStayInTheirOwnSessions() throws Exception {
      String a = openSession("c-7");
      String b = openSession("c-8");
      String inA = a;
      String inB = "baggage:tenant=bakery,microcks-session=" + b;

      Map<String, List<String>> answers = interleaved(
            List.of(new Turn("A", inA, 0, "c-7"), new Turn("B", inB, 0, "c-8"), new Turn("B", inB, 1, "c-8"),
                  new Turn("A", inA, 1, "c-7"), new Turn("A", inA, 2, "c-7"), new Turn("B", inB, 2, "c-8"),
                  new Turn("B", inB, 3, "c-8"), new Turn("A", inA, 3, "c-7")));

      for (String session : List.of(a, b)) {
         JsonNode verification = verify(session);
         assertEquals("SUCCESS", verification.get("status").asText(), verification.toPrettyString());
         assertEquals(STEPS, stepsOfTheCalls(verification));
      }
      // Each session recorded the requests of its own saga only.
      assertEquals(List.of("c-7"), customersSentTo(a));
      assertEquals(List.of("c-8"), customersSentTo(b));
      // The mocks answered each saga with the customer of its own session, never with the other one.
      assertEquals("c-7", JSON.readTree(answers.get("A").get(0)).get("customerId").asText());
      assertEquals("c-8", JSON.readTree(answers.get("B").get(0)).get("customerId").asText());
      assertTrue(answers.get("A").stream().noneMatch(answer -> answer.contains("c-8")), answers.get("A")::toString);
      assertFalse(answers.get("B").get(0).contains("c-7"), answers.get("B").get(0));
      // Each session keeps the outputs of its own steps, under its own id.
      assertEquals(STEPS, stateOf(a));
      assertEquals(STEPS, stateOf(b));
      assertEquals(Map.of("id", "o-42", "amount", 25.0), outputsOf(a, "createOrder"));
      assertEquals(Map.of("id", "o-42", "amount", 25.0), outputsOf(b, "createOrder"));
   }

   /**
    * The deliberate mistake: the test of saga B names session A too, so both sagas land in A, each step of both sagas
    * at the same instant. B receives nothing and keeps nothing; A fails, as 4 calls match no step, and keeps one state
    * per step. Its createOrder step takes the createOrder call A counted first: B's fails on its customer c-8, A's own
    * passes.
    */
   @Test
   void twoSagasNamingTheSameSessionAreCaughtByItsVerification() throws Exception {
      String a = openSession("c-7");
      String b = openSession("c-8");

      atTheSameInstant(new Saga("A", a, "c-7"), new Saga("B", a, "c-8"));

      JsonNode verificationOfB = verify(b);
      assertEquals("FAILURE", verificationOfB.get("status").asText(), verificationOfB.toPrettyString());
      assertEquals(STEPS.stream().map(step -> "step `" + step + "` missing").toList(), messagesOf(verificationOfB));
      assertEquals(0, verificationOfB.get("session").get("calls").size());
      assertEquals(List.of(), stateOf(b));

      JsonNode verificationOfA = verify(a);
      assertEquals("FAILURE", verificationOfA.get("status").asText(), verificationOfA.toPrettyString());
      List<String> customers = customersSentTo(a);
      assertEquals(List.of("c-7", "c-8"), customers.stream().sorted().toList());
      JsonNode createOrder = verificationOfA.get("steps").get(0);
      if ("c-8".equals(customers.get(0))) {
         assertEquals("FAILED", createOrder.get("status").asText(), createOrder.toPrettyString());
         assertEquals("`createOrder` body `/customerId` should come from `$inputs.customerId` (c-7), got c-8",
               createOrder.get("message").asText());
      } else {
         assertEquals("PASSED", createOrder.get("status").asText(), createOrder.toPrettyString());
      }
      List<String> steps = stepsOfTheCalls(verificationOfA);
      assertEquals(8, steps.size(), steps::toString);
      assertEquals(4, steps.stream().filter("none"::equals).count(), steps::toString);
      // No remaining step of the workflow expects the second call of each step: each one is unexpected.
      List<String> unexpected = new ArrayList<>();
      verificationOfA.get("session").get("calls").forEach(call -> {
         if (call.hasNonNull("failure")) {
            unexpected.add(call.get("failure").asText());
         }
      });
      assertEquals(List.of("unexpected call `createOrder` after `createOrder`",
            "unexpected call `reserveStock` after `reserveStock`",
            "unexpected call `chargePayment` after `chargePayment`",
            "unexpected call `confirmOrder` after `confirmOrder`"), unexpected);
      // Each step answered twice at the same instant keeps one state.
      assertEquals(STEPS, stateOf(a));
      assertEquals(STEPS.size(), serviceStateRepository
            .count(new Document("key", new Document("$regex", "^arazzo-session:" + a + ":"))));
   }

   /**
    * One call of a saga: a step, in the session the test names, for a customer.
    * @param saga     The saga the call belongs to, A or B
    * @param session  The session the call names: its id for the header, {@code baggage:<baggage>} for the baggage
    * @param step     The index of the step in placeOrderSaga
    * @param customer The customer of the saga
    */
   private record Turn(String saga, String session, int step, String customer) {
   }

   /**
    * Play the turns from two threads, one per saga, each turn waiting for the one before it: the interleaving is real,
    * as the calls come from two threads, and it is the same at every run.
    * @return The bodies the mocks answered, by saga, in the order of its calls
    */
   private Map<String, List<String>> interleaved(List<Turn> turns) throws Exception {
      List<CountDownLatch> done = turns.stream().map(turn -> new CountDownLatch(1)).toList();
      Map<String, List<String>> answers = new ConcurrentHashMap<>();
      ExecutorService threads = Executors.newFixedThreadPool(2);
      try {
         List<Future<?>> sagas = new ArrayList<>();
         for (String saga : List.of("A", "B")) {
            answers.put(saga, new ArrayList<>());
            sagas.add(threads.submit(() -> {
               for (int index = 0; index < turns.size(); index++) {
                  Turn turn = turns.get(index);
                  if (!turn.saga().equals(saga)) {
                     continue;
                  }
                  if (index > 0 && !done.get(index - 1).await(30, TimeUnit.SECONDS)) {
                     throw new IllegalStateException("turn " + (index - 1) + " never came");
                  }
                  answers.get(saga).add(call(turn));
                  done.get(index).countDown();
               }
               return null;
            }));
         }
         // A saga whose call failed stops the other one, waiting for its turn: report the failed call first.
         Throwable failure = null;
         for (Future<?> saga : sagas) {
            try {
               saga.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException ee) {
               failure = failure == null || ee.getCause() instanceof AssertionError ? ee.getCause() : failure;
            }
         }
         if (failure instanceof AssertionError assertion) {
            throw assertion;
         }
         if (failure != null) {
            throw new IllegalStateException(failure);
         }
      } finally {
         threads.shutdownNow();
      }
      return answers;
   }

   /**
    * One saga: its name, the session its test names and its customer.
    * @param name     A or B
    * @param session  The session its calls name
    * @param customer The customer of the saga
    */
   private record Saga(String name, String session, String customer) {
   }

   /**
    * Play two sagas from two threads, step after step: at each step, both wait for each other, then both call the mocks
    * at the same instant, and both wait for the two answers before the next step.
    */
   private void atTheSameInstant(Saga... sagas) throws Exception {
      CyclicBarrier together = new CyclicBarrier(sagas.length);
      ExecutorService threads = Executors.newFixedThreadPool(sagas.length);
      try {
         List<Future<?>> running = new ArrayList<>();
         for (Saga saga : sagas) {
            running.add(threads.submit(() -> {
               for (int step = 0; step < STEPS.size(); step++) {
                  together.await(10, TimeUnit.SECONDS);
                  call(new Turn(saga.name(), saga.session(), step, saga.customer()));
                  together.await(10, TimeUnit.SECONDS);
               }
               return null;
            }));
         }
         // A saga whose call failed leaves the other one waiting at the barrier: report the failed call first.
         Throwable failure = null;
         for (Future<?> saga : running) {
            try {
               saga.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException ee) {
               failure = failure == null || ee.getCause() instanceof AssertionError ? ee.getCause() : failure;
            }
         }
         if (failure instanceof AssertionError assertion) {
            throw assertion;
         }
         if (failure != null) {
            throw new IllegalStateException(failure);
         }
      } finally {
         threads.shutdownNow();
      }
   }

   /** The call of a turn to the mocks; its answer must be the one of the step. */
   private String call(Turn turn) {
      String session = turn.session();
      ResponseEntity<String> answer = switch (turn.step()) {
         case 0 -> callMocks(session, "Order+API", "/orders", "{\"customerId\": \"" + turn.customer()
               + "\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0, \"currency\": \"EUR\"}");
         case 1 -> callMocks(session, "Stock+API", "/reservations",
               "{\"orderId\": \"o-42\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}]}");
         case 2 -> callMocks(session, "Payment+API", "/payments", "{\"orderId\": \"o-42\", \"amount\": 25.0}");
         default -> callMocks(session, "Order+API", "/orders/o-42/confirm", null);
      };
      assertTrue(answer.getStatusCode().value() / 100 == 2,
            () -> "saga " + turn.saga() + ", step " + STEPS.get(turn.step()) + ": " + answer);
      return answer.getBody();
   }

   /** The customers of the createOrder requests a session recorded, in their order of receipt. */
   private List<String> customersSentTo(String sessionId) throws IOException {
      List<String> customers = new ArrayList<>();
      for (WorkflowSessionRecordedCall call : workflowSessionRecordedCallRepository
            .findBySessionIdOrderByIndexAsc(sessionId)) {
         if ("/orders".equals(call.getPath())) {
            customers.add(JSON.readTree(call.getRequestBody()).get("customerId").asText());
         }
      }
      return customers;
   }

   /** The steps whose outputs a session keeps in the state stores of the APIs, in the order of the workflow. */
   private List<String> stateOf(String sessionId) {
      String prefix = "arazzo-session:" + sessionId + ":";
      List<String> steps = serviceStateRepository.list(new Document("key", new Document("$regex", "^" + prefix)))
            .stream().map(state -> state.getKey().substring(prefix.length())).toList();
      return STEPS.stream().filter(steps::contains).toList();
   }

   private Map<?, ?> outputsOf(String sessionId, String step) throws IOException {
      ServiceState state = serviceStateRepository
            .find(new Document("key", "arazzo-session:" + sessionId + ":" + step)).firstResult();
      return JSON.readValue(state.getValue(), Map.class);
   }

   /** The step each call of the session was matched to, in their order of receipt; none for a call no step took. */
   private static List<String> stepsOfTheCalls(JsonNode verification) {
      List<String> steps = new ArrayList<>();
      verification.get("session").get("calls")
            .forEach(call -> steps.add(call.path("stepId").isTextual() ? call.get("stepId").asText() : "none"));
      return steps;
   }

   private static List<String> messagesOf(JsonNode verification) {
      List<String> messages = new ArrayList<>();
      verification.get("steps").forEach(step -> messages.add(step.path("message").asText()));
      return messages;
   }

   private String openSession(String customerId) throws Exception {
      ResponseEntity<String> opened = restTemplate.exchange("/api/workflows/sessions", HttpMethod.POST,
            new HttpEntity<>("{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"placeOrderSaga\", "
                  + "\"inputs\": {\"customerId\": \"" + customerId + "\"}}", jsonHeaders()),
            String.class);
      assertEquals(201, opened.getStatusCode().value(), opened.getBody());
      return JSON.readTree(opened.getBody()).get("id").asText();
   }

   private JsonNode verify(String sessionId) throws Exception {
      ResponseEntity<String> verified = restTemplate.exchange(
            "/api/workflows/sessions/" + sessionId + "/verification", HttpMethod.POST,
            new HttpEntity<>(jsonHeaders()), String.class);
      assertEquals(200, verified.getStatusCode().value(), verified.getBody());
      return JSON.readTree(verified.getBody());
   }

   /**
    * The saga service calls the mocks of an API 1.0.0, naming its session in the x-microcks-session header, or in the
    * baggage header when written as {@code baggage:<baggage>}.
    */
   private ResponseEntity<String> callMocks(String session, String api, String path, String body) {
      HttpHeaders headers = jsonHeaders();
      if (session.startsWith("baggage:")) {
         headers.set("baggage", session.substring("baggage:".length()));
      } else {
         headers.set("x-microcks-session", session);
      }
      return restTemplate.exchange("/rest/" + api + "/1.0.0" + path, HttpMethod.POST, new HttpEntity<>(body, headers),
            String.class);
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
