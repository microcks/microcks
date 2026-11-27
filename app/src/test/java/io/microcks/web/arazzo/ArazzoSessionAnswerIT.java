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

import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSessionCallRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;
import io.microcks.repository.arazzo.ArazzoSourcesRepository;
import io.microcks.repository.arazzo.WorkflowResultRepository;
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
 * An integration test of an order saga service opens a session on placeOrderSaga of Order saga 1.0.0 through the
 * Microcks API, then the service calls the mocks of Order, Stock and Payment API 1.0.0 naming the session. The examples
 * of these APIs were written for order o-1, but the mocks placed order o-42: in the session, they answer each step with
 * o-42.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSessionAnswerIT extends AbstractBaseIT {

   private static final String FIXTURES = "target/test-classes/io/microcks/service/arazzo/";
   private static final String SESSION_HEADER = "x-microcks-session";
   private static final ObjectMapper JSON = new ObjectMapper();
   private static final String PLACE_ORDER = "{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], "
         + "\"amount\": 25.0, \"currency\": \"EUR\"}";

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   @Inject
   private ArazzoSourcesRepository arazzoSourcesRepository;

   @Inject
   private WorkflowResultRepository workflowResultRepository;

   @Inject
   private ArazzoSessionRepository arazzoSessionRepository;

   @Inject
   private ArazzoSessionCallRepository arazzoSessionCallRepository;

   @TempDir
   private Path uploads;

   @BeforeEach
   void orderSagaIsUploadedAndLinked() throws IOException {
      for (String api : List.of("order-api", "stock-api", "payment-api")) {
         uploadArtifactFile(example("stateful-saga.contracts.yaml", api), true);
      }
      uploadArtifactFile(example("stateful-saga.arazzo.yaml", "order-saga"), true);
      uploadArtifactFile(example("stateful-saga.arazzo.yaml", "order-saga-sources"), false);
   }

   @AfterEach
   void deleteDocumentsAndSessions() {
      arazzoDocumentRepository.deleteAll();
      arazzoSourcesRepository.deleteAll();
      workflowResultRepository.deleteAll();
      arazzoSessionRepository.deleteAll();
      arazzoSessionCallRepository.deleteAll();
   }

   /** Walking skeleton, AC-02: the order reserveStock receives flows into the reservation the mock answers. */
   @Test
   void theOrderAReservationReceivesFlowsIntoTheReservation() throws Exception {
      String sessionId = openSession();

      ResponseEntity<String> reserved = callMocks(sessionId, "Stock+API", "/reservations",
            "{\"orderId\": \"o-42\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}]}");

      assertEquals(201, reserved.getStatusCode().value(), reserved.getBody());
      assertEquals(JSON.readTree("{\"id\": \"r-9\", \"orderId\": \"o-42\"}"), JSON.readTree(reserved.getBody()));
   }

   /**
    * AC-01: the mocks placed order o-42 in the session and have no example for it, yet they confirm o-42 with the
    * example of confirmOrder, written for o-1.
    */
   @Test
   void aLaterStepAnswersWithTheOutputOfAnEarlierStep() throws Exception {
      String sessionId = openSession();
      ResponseEntity<String> placed = callMocks(sessionId, "Order+API", "/orders", PLACE_ORDER);
      assertEquals("o-42", JSON.readTree(placed.getBody()).get("id").asText(), placed.getBody());

      ResponseEntity<String> confirmed = callMocks(sessionId, "Order+API", "/orders/o-42/confirm", null);

      assertEquals(200, confirmed.getStatusCode().value(), confirmed.getBody());
      assertEquals(
            JSON.readTree("{\"id\": \"o-42\", \"customerId\": \"c-7\", \"amount\": 25.0, \"status\": \"CONFIRMED\"}"),
            JSON.readTree(confirmed.getBody()));
   }

   /**
    * Along the whole saga, the mocks answer each step with o-42, and the verification of the session passes on the
    * responses they recorded.
    */
   @Test
   void theMocksAnswerTheWholeSagaWithTheOrderOfTheSession() throws Exception {
      String sessionId = openSession();

      List<ResponseEntity<String>> answers = List.of(callMocks(sessionId, "Order+API", "/orders", PLACE_ORDER),
            callMocks(sessionId, "Stock+API", "/reservations",
                  "{\"orderId\": \"o-42\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}]}"),
            callMocks(sessionId, "Payment+API", "/payments", "{\"orderId\": \"o-42\", \"amount\": 25.0}"),
            callMocks(sessionId, "Order+API", "/orders/o-42/confirm", null));

      List<String> orders = new ArrayList<>();
      for (ResponseEntity<String> answer : answers) {
         JsonNode body = JSON.readTree(answer.getBody());
         orders.add(answer.getStatusCode().value() + " " + body.path("orderId").asText(body.path("id").asText()));
      }
      assertEquals(List.of("201 o-42", "201 o-42", "201 o-42", "200 o-42"), orders);
      ResponseEntity<String> verified = restTemplate.exchange(
            "/api/arazzo/sessions/" + sessionId + "/verification", HttpMethod.POST,
            new HttpEntity<>(jsonHeaders()), String.class);
      JsonNode verification = JSON.readTree(verified.getBody());
      assertEquals("SUCCESS", verification.get("status").asText(), verification.toPrettyString());
      assertEquals("{\"orderId\":\"o-42\",\"reservationId\":\"r-9\"}", verification.get("outputs").toString());
   }

   /**
    * The mocks reflect what the service sends: it reserves the items of o-7 although the mocks placed o-42, the
    * reservation is for o-7, and the verification of the session reports that o-7 is not o-42.
    */
   @Test
   void theOrderTheServiceSendsIsReflectedAndTheVerificationReportsIt() throws Exception {
      String sessionId = openSession();
      callMocks(sessionId, "Order+API", "/orders", PLACE_ORDER);

      ResponseEntity<String> reserved = callMocks(sessionId, "Stock+API", "/reservations",
            "{\"orderId\": \"o-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}]}");

      assertEquals(JSON.readTree("{\"id\": \"r-9\", \"orderId\": \"o-7\"}"), JSON.readTree(reserved.getBody()));
      ResponseEntity<String> verified = restTemplate.exchange(
            "/api/arazzo/sessions/" + sessionId + "/verification", HttpMethod.POST,
            new HttpEntity<>(jsonHeaders()), String.class);
      assertEquals("`reserveStock` body `/orderId` should come from `$steps.createOrder.outputs.id` (o-42), got o-7",
            JSON.readTree(verified.getBody()).get("steps").get(1).get("message").asText());
   }

   /**
    * AC-03: outside a session, the mocks keep answering with their examples: they have none for o-42, and the
    * reservation they answer is the one of the example, for o-1. A session placing o-42 at the same time changes
    * nothing.
    */
   @Test
   void outsideASessionTheMocksAnswerAsToday() throws Exception {
      String sessionId = openSession();
      callMocks(sessionId, "Order+API", "/orders", PLACE_ORDER);

      ResponseEntity<String> confirmed = callMocks(null, "Order+API", "/orders/o-42/confirm", null);
      ResponseEntity<String> reserved = callMocks(null, "Stock+API", "/reservations",
            "{\"orderId\": \"o-42\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}]}");

      assertEquals("400 The response /orderId=o-42 does not exist!",
            confirmed.getStatusCode().value() + " " + confirmed.getBody());
      assertEquals(201, reserved.getStatusCode().value(), reserved.getBody());
      assertEquals(JSON.readTree("{\"id\": \"r-9\", \"orderId\": \"o-1\"}"), JSON.readTree(reserved.getBody()));
   }

   private String openSession() throws Exception {
      ResponseEntity<String> opened = restTemplate.exchange("/api/arazzo/sessions", HttpMethod.POST, new HttpEntity<>(
            "{\"documentId\": \"Order saga:1.0.0\", \"workflowId\": \"placeOrderSaga\"}", jsonHeaders()), String.class);
      assertEquals(201, opened.getStatusCode().value(), opened.getBody());
      return JSON.readTree(opened.getBody()).get("id").asText();
   }

   /** The saga service calls the mocks of an API 1.0.0, naming its session when it has one. */
   private ResponseEntity<String> callMocks(String session, String api, String path, String body) {
      HttpHeaders headers = jsonHeaders();
      if (session != null) {
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
