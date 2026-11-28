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

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An integration test of an order saga service opens a session on compensatePaymentRefused of Order saga 1.0.0 through
 * the Microcks API, setting chargePayment to fail with the example card refused of Payment API 1.0.0; the service calls
 * the mocks of Order, Stock and Payment API 1.0.0 naming the session, then the test asks Microcks to verify the
 * compensation. Every request and response of the Microcks API is checked against the description Microcks publishes.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSagaCompensationIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final String SESSIONS = "/workflows/sessions";
   private static final String VERIFICATION = "/workflows/sessions/{sessionId}/verification";
   private static final String CARD_REFUSED = "{\"chargePayment\": \"card refused\"}";
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
         uploadArtifactFile(example("saga-compensation.contracts.yaml", api), true);
      }
      uploadArtifactFile(example("saga-compensation.arazzo.yaml", "order-saga"), true);
      uploadArtifactFile(example("saga-compensation.arazzo.yaml", "order-saga-metadata"), false);
   }

   @AfterEach
   void deleteDocumentsSessionsAndVerifications() {
      workflowDocumentRepository.deleteAll();
      workflowMetadataRepository.deleteAll();
      workflowResultRepository.deleteAll();
      workflowSessionRepository.deleteAll();
      workflowSessionRecordedCallRepository.deleteAll();
   }

   /**
    * AC-01 and AC-02: chargePayment answers with card refused in the session; the service releases reservation r-9 and
    * cancels order o-42, so the verification of the compensation passes.
    */
   @Test
   void theRefusedPaymentIsCompensatedAndTheVerificationPasses() throws Exception {
      String sessionId = openSession(CARD_REFUSED);

      placeOrderAndReserveStock(sessionId);
      ResponseEntity<String> refused = chargePayment(sessionId);
      assertEquals(402, refused.getStatusCode().value(), refused.getBody());
      assertEquals("insufficient-funds", refused.getHeaders().getFirst("X-Decline-Reason"));
      assertEquals("REFUSED Card refused", JSON.readTree(refused.getBody()).get("status").asText() + " "
            + JSON.readTree(refused.getBody()).get("reason").asText());
      assertEquals(200,
            callMocks(sessionId, HttpMethod.DELETE, "Stock+API", "/reservations/r-9").getStatusCode().value());
      assertEquals(200,
            callMocks(sessionId, HttpMethod.POST, "Order+API", "/orders/o-42/cancel").getStatusCode().value());

      JsonNode verification = verify(sessionId);
      assertEquals("SUCCESS", verification.get("status").asText(), verification.toPrettyString());
      assertEquals(List.of("createOrder PASSED 201", "reserveStock PASSED 201", "chargePayment PASSED 402",
            "releaseStock PASSED 200", "cancelOrder PASSED 200"), described(verification));
   }

   /** AC-04: the service confirms order o-42 after the refused payment; that call was not expected. */
   @Test
   void aConfirmationAfterTheRefusedPaymentFailsTheVerification() throws Exception {
      String sessionId = openSession(CARD_REFUSED);

      placeOrderAndReserveStock(sessionId);
      chargePayment(sessionId);
      callMocks(sessionId, HttpMethod.POST, "Order+API", "/orders/o-42/confirm");
      callMocks(sessionId, HttpMethod.DELETE, "Stock+API", "/reservations/r-9");
      callMocks(sessionId, HttpMethod.POST, "Order+API", "/orders/o-42/cancel");

      JsonNode verification = verify(sessionId);
      assertEquals("FAILURE", verification.get("status").asText(), verification.toPrettyString());
      assertEquals("unexpected call `confirmOrder` after `chargePayment`",
            verification.get("session").get("calls").get(3).get("failure").asText());
   }

   /** AC-05: a session without failure, and a call outside any session, get the payment charged as usual. */
   @Test
   void injectionIsLimitedToItsSession() throws Exception {
      String withFailure = openSession(CARD_REFUSED);
      String withoutFailure = openSession(null);

      assertEquals(402, chargePayment(withFailure).getStatusCode().value());
      assertEquals(201, chargePayment(withoutFailure).getStatusCode().value());
      assertEquals(201,
            restTemplate
                  .exchange("/rest/Payment+API/1.0.0/payments", HttpMethod.POST,
                        new HttpEntity<>("{\"orderId\": \"o-42\", \"amount\": 25.0}", jsonHeaders()), String.class)
                  .getStatusCode().value());
   }

   /** Once the test closes its session, chargePayment is charged as usual again, even when a call names the session. */
   @Test
   void aClosedSessionInjectsNothingAnyMore() throws Exception {
      String sessionId = openSession(CARD_REFUSED);
      assertEquals(402, chargePayment(sessionId).getStatusCode().value());

      ResponseEntity<String> closed = restTemplate.exchange("/api/workflows/sessions/" + sessionId, HttpMethod.DELETE,
            new HttpEntity<>(jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("DELETE", "/workflows/sessions/{sessionId}", closed);
      assertEquals(204, closed.getStatusCode().value());

      assertEquals(201, chargePayment(sessionId).getStatusCode().value());
   }

   /**
    * The injected example wins over the consistent answer of the session: the service charges order o-7, which a
    * session reflects in its answer, but the injected card refused answers as written, for o-42.
    */
   @Test
   void anInjectedExampleIsNeverFilledWithTheValuesOfTheSession() throws Exception {
      String withFailure = openSession(CARD_REFUSED);
      String withoutFailure = openSession(null);
      String chargeO7 = "{\"orderId\": \"o-7\", \"amount\": 25.0}";

      ResponseEntity<String> charged = callMocks(withoutFailure, HttpMethod.POST, "Payment+API", "/payments", chargeO7);
      ResponseEntity<String> refused = callMocks(withFailure, HttpMethod.POST, "Payment+API", "/payments", chargeO7);

      // The control: without failure, the session fills the answer with the order the service sent.
      assertEquals("201 o-7",
            charged.getStatusCode().value() + " " + JSON.readTree(charged.getBody()).get("orderId").asText(),
            charged.getBody());
      assertEquals(402, refused.getStatusCode().value(), refused.getBody());
      assertEquals(JSON.readTree("{\"orderId\": \"o-42\", \"status\": \"REFUSED\", \"reason\": \"Card refused\"}"),
            JSON.readTree(refused.getBody()));
   }

   /** A failure naming an example the operation of its step does not have is refused, and no session is opened. */
   @Test
   void aFailureOnAnUnknownExampleIsRefused() throws Exception {
      String body = "{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"compensatePaymentRefused\", "
            + "\"failures\": {\"chargePayment\": \"card expired\"}}";
      MICROCKS_API.assertRequestConforms("POST", SESSIONS, body);

      ResponseEntity<String> refused = restTemplate.exchange("/api" + SESSIONS, HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);

      MICROCKS_API.assertResponseConforms("POST", SESSIONS, refused);
      assertEquals(400, refused.getStatusCode().value());
      assertEquals("Cannot make step `chargePayment` fail: POST /payments of Payment API 1.0.0 has no example "
            + "`card expired`", refused.getBody());
      assertEquals(0, workflowSessionRepository.count());
   }

   private void placeOrderAndReserveStock(String sessionId) {
      assertEquals(201,
            callMocks(sessionId, HttpMethod.POST, "Order+API", "/orders",
                  "{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0}")
                        .getStatusCode().value());
      assertEquals(201, callMocks(sessionId, HttpMethod.POST, "Stock+API", "/reservations", "{\"orderId\": \"o-42\"}")
            .getStatusCode().value());
   }

   private ResponseEntity<String> chargePayment(String sessionId) {
      return callMocks(sessionId, HttpMethod.POST, "Payment+API", "/payments",
            "{\"orderId\": \"o-42\", \"amount\": 25.0}");
   }

   private static List<String> described(JsonNode verification) {
      List<String> steps = new ArrayList<>();
      verification.get("steps").forEach(step -> steps.add(
            step.get("stepId").asText() + " " + step.get("status").asText() + " " + step.get("statusCode").asInt()));
      return steps;
   }

   /** Open a session on compensatePaymentRefused, with the failures given as JSON, or none when null. */
   private String openSession(String failures) throws Exception {
      String body = "{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"compensatePaymentRefused\""
            + (failures == null ? "" : ", \"failures\": " + failures) + "}";
      MICROCKS_API.assertRequestConforms("POST", SESSIONS, body);
      ResponseEntity<String> opened = restTemplate.exchange("/api" + SESSIONS, HttpMethod.POST,
            new HttpEntity<>(body, jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("POST", SESSIONS, opened);
      assertEquals(201, opened.getStatusCode().value(), opened.getBody());
      JsonNode session = JSON.readTree(opened.getBody());
      assertEquals(failures == null ? "{}" : JSON.readTree(failures).toString(), session.get("failures").toString());
      return session.get("id").asText();
   }

   /** Verify a session as a test does, checking the response against the published API. */
   private JsonNode verify(String sessionId) throws Exception {
      ResponseEntity<String> verified = restTemplate.exchange("/api" + VERIFICATION.replace("{sessionId}", sessionId),
            HttpMethod.POST, new HttpEntity<>(jsonHeaders()), String.class);
      MICROCKS_API.assertResponseConforms("POST", VERIFICATION, verified);
      assertEquals(200, verified.getStatusCode().value(), verified.getBody());
      return JSON.readTree(verified.getBody());
   }

   private ResponseEntity<String> callMocks(String sessionId, HttpMethod method, String api, String path) {
      return callMocks(sessionId, method, api, path, null);
   }

   /** The saga service calls the mocks of an API 1.0.0, naming its session in the x-microcks-session header. */
   private ResponseEntity<String> callMocks(String sessionId, HttpMethod method, String api, String path, String body) {
      HttpHeaders headers = jsonHeaders();
      headers.set("x-microcks-session", sessionId);
      return restTemplate.exchange("/rest/" + api + "/1.0.0" + path, method, new HttpEntity<>(body, headers),
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
