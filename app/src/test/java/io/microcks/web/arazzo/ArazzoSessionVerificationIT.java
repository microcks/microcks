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
import com.mongodb.client.MongoCollection;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An integration test of an order saga service opens a verification session on placeOrderSaga of Order saga 1.0.0
 * through the Microcks API, the service calls the mocks of Order, Stock and Payment API 1.0.0 naming the session, then
 * the test asks Microcks to verify the session. Every request and response of the Microcks API is checked against the
 * description Microcks publishes of it.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSessionVerificationIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final String SESSIONS = "/arazzo/sessions";
   private static final String SESSION = "/arazzo/sessions/{sessionId}";
   private static final String VERIFICATION = "/arazzo/sessions/{sessionId}/verification";
   private static final String SESSION_HEADER = "x-microcks-session";
   private static final MicrocksApiDescription MICROCKS_API = MicrocksApiDescription.version("2.0");
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

   @BeforeEach
   void orderSagaIsUploadedAndLinked() throws IOException {
      for (String api : List.of("order-api", "stock-api", "payment-api")) {
         uploadArtifactFile(example("order-saga.contracts.yaml", api), true);
      }
      uploadArtifactFile(example("order-saga.arazzo.yaml", "order-saga"), true);
      uploadArtifactFile(example("order-saga.arazzo.yaml", "order-saga-metadata"), false);
   }

   @AfterEach
   void deleteDocumentsSessionsAndVerifications() {
      workflowDocumentRepository.deleteAll();
      workflowMetadataRepository.deleteAll();
      workflowResultRepository.deleteAll();
      workflowSessionRepository.deleteAll();
      workflowSessionRecordedCallRepository.deleteAll();
   }

   /** Walking skeleton: a call naming the session on the mocks is matched to its step by the verification. */
   @Test
   void aMockCallNamingTheSessionIsMatchedToItsStep() throws Exception {
      String sessionId = openSession();

      ResponseEntity<String> placed = callMocks(sessionId, "Order+API", "/orders",
            "{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0}");

      assertEquals(201, placed.getStatusCode().value(), placed.getBody());
      JsonNode verification = verify(sessionId);
      assertEquals("SESSION", verification.get("origin").asText(), verification.toPrettyString());
      JsonNode createOrder = verification.get("steps").get(0);
      assertEquals("createOrder PASSED 201", createOrder.get("stepId").asText() + " "
            + createOrder.get("status").asText() + " " + createOrder.get("statusCode").asInt());
      JsonNode call = verification.get("session").get("calls").get(0);
      assertEquals("0 Order API POST /orders createOrder",
            call.get("index").asInt() + " " + call.get("apiName").asText() + " " + call.get("method").asText() + " "
                  + call.get("path").asText() + " " + call.get("stepId").asText());
   }

   /** AC-01: the saga service makes the 4 calls of the saga through the mocks; each step is matched in order. */
   @Test
   void theCallsOfTheSagaPassStepByStep() throws Exception {
      String sessionId = openSession();

      placeOrder(sessionId);
      reserveStock(sessionId);
      chargePayment(sessionId);
      confirmOrder(sessionId);

      JsonNode verification = verify(sessionId);
      assertEquals("SUCCESS", verification.get("status").asText(), verification.toPrettyString());
      assertEquals(List.of("createOrder PASSED", "reserveStock PASSED", "chargePayment PASSED", "confirmOrder PASSED"),
            described(verification));
   }

   /** AC-02: the saga service charges the order before reserving its items; the verification fails. */
   @Test
   void aCallOutOfOrderFailsTheVerification() throws Exception {
      String sessionId = openSession();

      placeOrder(sessionId);
      chargePayment(sessionId);
      reserveStock(sessionId);
      confirmOrder(sessionId);

      JsonNode verification = verify(sessionId);
      assertEquals("FAILURE", verification.get("status").asText(), verification.toPrettyString());
      assertEquals(List.of("createOrder PASSED", "reserveStock PASSED", "chargePayment FAILED", "confirmOrder PASSED"),
            described(verification));
      assertEquals("step `reserveStock` expected before `chargePayment`",
            verification.get("steps").get(2).get("message").asText());
   }

   /** AC-03: the saga service reserves the items of order o-1 although the mocks placed o-42. */
   @Test
   void dataNotTakenFromThePreviousStepFailsTheVerification() throws Exception {
      String sessionId = openSession();

      placeOrder(sessionId);
      assertEquals(201, callMocks(sessionId, "Stock+API", "/reservations", "{\"orderId\": \"o-1\", \"items\": []}")
            .getStatusCode().value());
      chargePayment(sessionId);
      confirmOrder(sessionId);

      JsonNode verification = verify(sessionId);
      assertEquals("FAILURE", verification.get("status").asText(), verification.toPrettyString());
      assertEquals("`reserveStock` body `/orderId` should come from `$steps.createOrder.outputs.id` (o-42), got o-1",
            verification.get("steps").get(1).get("message").asText());
   }

   /**
    * AC-05: two tests run the saga at the same time, one naming its session in the header, the other in its W3C
    * baggage; each verification only sees the calls of its own session.
    */
   @Test
   void sessionsAreIsolated() throws Exception {
      String first = openSession();
      String second = openSession();
      String inBaggage = "baggage:tenant=bakery,microcks-session=" + second;

      placeOrder(first);
      placeOrder(inBaggage);
      reserveStock(inBaggage);
      reserveStock(first);
      chargePayment(first);
      chargePayment(inBaggage);
      confirmOrder(inBaggage);
      confirmOrder(first);

      for (String sessionId : List.of(first, second)) {
         JsonNode verification = verify(sessionId);
         assertEquals("SUCCESS", verification.get("status").asText(), verification.toPrettyString());
         assertEquals(4, verification.get("session").get("calls").size(), verification.toPrettyString());
      }
   }

   /** A closed session records no more calls; it can still be verified. */
   @Test
   void aClosedSessionRecordsNoMoreCalls() throws Exception {
      String sessionId = openSession();
      placeOrder(sessionId);

      ResponseEntity<String> closed = restTemplate.exchange("/api" + SESSION.replace("{sessionId}", sessionId),
            HttpMethod.DELETE, new HttpEntity<>(jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("DELETE", SESSION, closed);
      assertEquals(204, closed.getStatusCode().value());
      reserveStock(sessionId);

      JsonNode verification = verify(sessionId);
      assertEquals(1, verification.get("session").get("calls").size(), verification.toPrettyString());
   }

   /** The sessions and their calls expire through TTL indexes, so that their collections stay bounded. */
   @Test
   void theSessionsAndTheirCallsHaveTtlIndexes() {
      assertEquals(List.of("expireAt"), ttlIndexesOf(workflowSessionRepository.mongoCollection()));
      assertEquals(List.of("expireAt"), ttlIndexesOf(workflowSessionRecordedCallRepository.mongoCollection()));
   }

   /** The fields of the indexes of a collection that make its documents expire. */
   private static List<String> ttlIndexesOf(MongoCollection<?> collection) {
      List<String> fields = new ArrayList<>();
      for (Document index : collection.listIndexes()) {
         if (index.containsKey("expireAfterSeconds")) {
            fields.addAll(((Document) index.get("key")).keySet());
         }
      }
      return fields;
   }

   /** dependsOn relaxes the order: in Order saga 1.1.0, charging before reserving the items is in order. */
   @Test
   void dependsOnRelaxesTheOrderOfTheSteps() throws Exception {
      uploadArtifactFile(example("order-saga.arazzo.yaml", "order-saga-parallel"), true);
      uploadArtifactFile(example("order-saga.arazzo.yaml", "order-saga-parallel-metadata"), false);
      String sessionId = openSession("{\"documentId\": \"Order saga:1.1.0\", \"workflowId\": \"placeOrderSaga\"}");

      placeOrder(sessionId);
      chargePayment(sessionId);
      reserveStock(sessionId);
      confirmOrder(sessionId);

      JsonNode verification = verify(sessionId);
      assertEquals("SUCCESS", verification.get("status").asText(), verification.toPrettyString());
   }

   /** A session opened with inputs checks the values coming from them: customer c-9 is not the c-7 given. */
   @Test
   void theValuesComingFromTheInputsOfTheSessionAreChecked() throws Exception {
      String sessionId = openSession("{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"placeOrderSaga\", "
            + "\"inputs\": {\"customerId\": \"c-7\"}}");

      assertEquals(201,
            callMocks(sessionId, "Order+API", "/orders",
                  "{\"customerId\": \"c-9\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0}")
                        .getStatusCode().value());

      JsonNode verification = verify(sessionId);
      assertEquals("{\"customerId\":\"c-7\"}", verification.get("inputs").toString());
      assertEquals("`createOrder` body `/customerId` should come from `$inputs.customerId` (c-7), got c-9",
            verification.get("steps").get(0).get("message").asText());
   }

   /** A session with an input of another type than the workflow declares is refused, telling why in plain text. */
   @Test
   void aSessionWithAnInvalidInputIsRefused() throws Exception {
      String body = "{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"placeOrderSaga\", "
            + "\"inputs\": {\"customerId\": 7}}";
      MICROCKS_API.assertRequestConforms("POST", SESSIONS, body);

      ResponseEntity<String> refused = restTemplate.exchange("/api" + SESSIONS, HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);

      MICROCKS_API.assertResponseConforms("POST", SESSIONS, refused);
      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Invalid input customerId: a string is expected, got 7", refused.getBody());
      assertEquals(0, workflowSessionRepository.count());
   }

   /** A session on an unknown workflow is refused, telling why in plain text. */
   @Test
   void aSessionOnAnUnknownWorkflowIsRefused() throws Exception {
      String body = "{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"cancelSaga\"}";
      MICROCKS_API.assertRequestConforms("POST", SESSIONS, body);

      ResponseEntity<String> refused = restTemplate.exchange("/api" + SESSIONS, HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);

      MICROCKS_API.assertResponseConforms("POST", SESSIONS, refused);
      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Unknown workflow: cancelSaga", refused.getBody());
   }

   /*
    * Feature: reading the sessions
    * Scenario: the calls of an open session are counted as the mocks receive them
    *   Given a session is open on placeOrderSaga
    *   When the saga service places an order through the mocks naming the session
    *   Then reading the session tells it is open with 1 call, and no verification yet
    * Scenario: a verified session tells which run is its verification
    * Scenario: the sessions of a workflow are listed, the latest first, open or closed
    * Scenario: an unknown session is not found; the sessions of another workflow are not listed
    */
   @Test
   void theCallsOfAnOpenSessionAreCountedBeforeItIsVerified() throws Exception {
      String sessionId = openSession();
      assertEquals(0, readSession(sessionId).get("callCount").asInt());

      placeOrder(sessionId);
      reserveStock(sessionId);

      JsonNode session = readSession(sessionId);
      assertEquals("open 2 placeOrderSaga", session.get("state").asText() + " " + session.get("callCount").asInt() + " "
            + session.get("workflowId").asText());
      assertTrue(session.get("openedDate").asLong() > 0 && session.get("expireAt").asLong() > 0);
      assertFalse(session.has("verificationRunId"), session.toPrettyString());
   }

   @Test
   void aVerifiedSessionTellsWhichRunIsItsVerification() throws Exception {
      String sessionId = openSession();
      placeOrder(sessionId);

      JsonNode verification = verify(sessionId);

      assertEquals(verification.get("id").asText(), readSession(sessionId).get("verificationRunId").asText());
   }

   @Test
   void aClosedSessionIsReadAsClosedWithItsCalls() throws Exception {
      String sessionId = openSession();
      placeOrder(sessionId);
      restTemplate.exchange("/api" + SESSION.replace("{sessionId}", sessionId), HttpMethod.DELETE,
            new HttpEntity<>(jsonHeaders()), String.class);

      JsonNode session = readSession(sessionId);

      assertEquals("closed 1", session.get("state").asText() + " " + session.get("callCount").asInt());
      assertTrue(session.get("closedDate").asLong() > 0);
   }

   @Test
   void anUnknownSessionIsNotFound() {
      ResponseEntity<String> read = restTemplate.exchange(
            "/api" + SESSION.replace("{sessionId}", "5f0000000000000000000000"), HttpMethod.GET,
            new HttpEntity<>(jsonHeaders()), String.class);

      assertEquals(404, read.getStatusCode().value());
   }

   @Test
   void theSessionsOfAWorkflowAreListedTheLatestFirstWhetherOpenOrClosed() throws Exception {
      String first = openSession();
      placeOrder(first);
      restTemplate.exchange("/api" + SESSION.replace("{sessionId}", first), HttpMethod.DELETE,
            new HttpEntity<>(jsonHeaders()), String.class);
      String second = openSession();

      JsonNode listed = listSessions("Order saga:1.0.0", "placeOrderSaga");

      assertEquals(List.of(second + " open 0", first + " closed 1"), describedSessions(listed));
      assertEquals(List.of(), describedSessions(listSessions("Order saga:1.0.0", "cancelSaga")));
      assertEquals(List.of(), describedSessions(listSessions("unknown", "placeOrderSaga")));
   }

   private static List<String> describedSessions(JsonNode sessions) {
      List<String> described = new ArrayList<>();
      sessions.forEach(session -> described.add(session.get("id").asText() + " " + session.get("state").asText() + " "
            + session.get("callCount").asInt()));
      return described;
   }

   /** Read a session as a test does, checking the response against the published API. */
   private JsonNode readSession(String sessionId) throws Exception {
      ResponseEntity<String> read = restTemplate.exchange("/api" + SESSION.replace("{sessionId}", sessionId),
            HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("GET", SESSION, read);
      assertEquals(200, read.getStatusCode().value(), read.getBody());
      return JSON.readTree(read.getBody());
   }

   /** List the sessions of a workflow as a test does, checking the response against the published API. */
   private JsonNode listSessions(String documentId, String workflowId) throws Exception {
      ResponseEntity<String> listed = restTemplate.exchange(
            "/api" + SESSIONS + "?documentId=" + URLEncoder.encode(documentId, StandardCharsets.UTF_8) + "&workflowId="
                  + URLEncoder.encode(workflowId, StandardCharsets.UTF_8),
            HttpMethod.GET, new HttpEntity<>(jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("GET", SESSIONS, listed);
      assertEquals(200, listed.getStatusCode().value(), listed.getBody());
      return JSON.readTree(listed.getBody());
   }

   private void placeOrder(String sessionId) {
      assertEquals(201,
            callMocks(sessionId, "Order+API", "/orders",
                  "{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0}")
                        .getStatusCode().value());
   }

   private void reserveStock(String sessionId) {
      assertEquals(201, callMocks(sessionId, "Stock+API", "/reservations", "{\"orderId\": \"o-42\", \"items\": []}")
            .getStatusCode().value());
   }

   private void chargePayment(String sessionId) {
      assertEquals(201, callMocks(sessionId, "Payment+API", "/payments", "{\"orderId\": \"o-42\", \"amount\": 25.0}")
            .getStatusCode().value());
   }

   private void confirmOrder(String sessionId) {
      assertEquals(200, callMocks(sessionId, "Order+API", "/orders/o-42/confirm", null).getStatusCode().value());
   }

   private static List<String> described(JsonNode verification) {
      List<String> steps = new ArrayList<>();
      verification.get("steps")
            .forEach(step -> steps.add(step.get("stepId").asText() + " " + step.get("status").asText()));
      return steps;
   }

   /** Open a session on placeOrderSaga as a test does, checking the exchange against the published API. */
   private String openSession() throws Exception {
      return openSession("{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"placeOrderSaga\"}");
   }

   /** Open a session as a test does, with the given request body. */
   private String openSession(String body) throws Exception {
      MICROCKS_API.assertRequestConforms("POST", SESSIONS, body);
      ResponseEntity<String> opened = restTemplate.exchange("/api" + SESSIONS, HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("POST", SESSIONS, opened);
      assertEquals(201, opened.getStatusCode().value(), opened.getBody());
      String sessionId = JSON.readTree(opened.getBody()).get("id").asText();
      assertTrue(opened.getHeaders().getFirst("Location").endsWith("/api/arazzo/sessions/" + sessionId));
      return sessionId;
   }

   /** Verify a session as a test does, checking the response against the published API. */
   private JsonNode verify(String sessionId) throws Exception {
      ResponseEntity<String> verified = restTemplate.exchange("/api" + VERIFICATION.replace("{sessionId}", sessionId),
            HttpMethod.POST, new HttpEntity<>(jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("POST", VERIFICATION, verified);
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
         headers.set(SESSION_HEADER, session);
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
