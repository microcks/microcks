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

import io.microcks.domain.Header;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.domain.ServiceState;
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.domain.workflow.WorkflowSession;
import io.microcks.domain.workflow.WorkflowSessionRecordedCall;
import io.microcks.repository.RequestRepository;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.ServiceRepository;
import io.microcks.repository.ServiceStateRepository;
import io.microcks.repository.arazzo.ArazzoDocumentRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of the mocks answering an order saga service consistently along the workflow placeOrderSaga of Order
 * saga 1.0.0, in a session: the examples of Order, Stock and Payment API 1.0.0 were written for order o-1, but the mock
 * placed order o-42 in the session. They enter through the use case deciding the answer of the mocks to a call made in
 * a session.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSessionAnswerTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String SAGA = "placeOrderSaga";
   private static final ObjectMapper JSON = new ObjectMapper();

   @Inject
   private ArazzoSessionService arazzoSessionService;

   @Inject
   private ArazzoSessionAnswerService arazzoSessionAnswerService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private ResponseRepository responseRepository;

   @Inject
   private RequestRepository requestRepository;

   @Inject
   private ServiceStateRepository serviceStateRepository;

   @Inject
   private ArazzoSessionRepository arazzoSessionRepository;

   @Inject
   private ArazzoDocumentRepository arazzoDocumentRepository;

   /** Walking skeleton, AC-02: the order reserveStock receives flows into the reservation the mock answers. */
   @Test
   void theValuesAStepReceivesFlowIntoItsAnswer() throws Exception {
      WorkflowSession session = sessionOnTheSaga();

      Response answer = arazzoSessionAnswerService
            .answer(session.getId(), reserveStock("o-42"), dispatched("Stock API", "POST /reservations")).orElseThrow();

      assertJson("{\"id\": \"r-9\", \"orderId\": \"o-42\"}", answer.getContent());
      assertEquals("201", answer.getStatus());
   }

   /**
    * AC-01: createOrder placed o-42 in the session; the mocks have no example for o-42, yet they confirm o-42 with the
    * example of confirmOrder, written for o-1.
    */
   @Test
   void aLaterStepAnswersWithTheOutputOfAnEarlierStep() throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      Response placed = mocksAnswer(session, createOrder(), dispatched("Order API", "POST /orders"));

      Response answer = arazzoSessionAnswerService.answer(session.getId(), confirmOrder("o-42"), null).orElseThrow();

      assertJson("{\"id\": \"o-42\", \"customerId\": \"c-7\", \"amount\": 25.0, \"status\": \"CONFIRMED\"}",
            answer.getContent());
      assertEquals(List.of("o-1", "200", "application/json"),
            List.of(answer.getName(), answer.getStatus(), answer.getMediaType()));
      // The answer keeps the headers of its example.
      assertEquals(List.of("Location=[/orders/o-42]"),
            placed.getHeaders().stream().map(header -> header.getName() + "=" + header.getValues()).toList());
   }

   /** A value a step does not send is taken from the outputs of the earlier steps of the session. */
   @Test
   void aValueAStepDoesNotSendIsTakenFromTheSession() throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      mocksAnswer(session, createOrder(), dispatched("Order API", "POST /orders"));

      Response answer = arazzoSessionAnswerService
            .answer(session.getId(), received("Payment API", "POST /payments", "/payments", "{\"amount\": 25.0}"),
                  dispatched("Payment API", "POST /payments"))
            .orElseThrow();

      assertJson("{\"id\": \"p-3\", \"orderId\": \"o-42\", \"status\": \"CHARGED\"}", answer.getContent());
   }

   /**
    * The inputs a session was opened with are values of the session too: createOrder takes its customer from
    * $inputs.customerId; the service sends none, and the order the mock places is for the customer of the session.
    */
   @Test
   void theInputsOfTheSessionFlowIntoTheAnswers() throws Exception {
      WorkflowDocument saga = orderSagaIsImportedAndLinked();
      WorkflowSession session = arazzoSessionService.openSession(saga.getId(), SAGA, Map.of("customerId", "c-9"))
            .orElseThrow();

      Response placed = mocksAnswer(session,
            received("Order API", "POST /orders", "/orders",
                  "{\"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0, \"currency\": \"EUR\"}"),
            dispatched("Order API", "POST /orders"));

      assertJson("{\"id\": \"o-42\", \"customerId\": \"c-9\", \"amount\": 25.0, \"status\": \"PENDING\"}",
            placed.getContent());
   }

   /**
    * The mocks reflect what the service sends: it reserves the items of o-7 although createOrder placed o-42, and the
    * reservation is for o-7. The verification of the session is the one that reports o-7 is not o-42.
    */
   @Test
   void theValuesTheServiceSendsAreReflectedInTheAnswer() throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      mocksAnswer(session, createOrder(), dispatched("Order API", "POST /orders"));

      Response answer = arazzoSessionAnswerService
            .answer(session.getId(), reserveStock("o-7"), dispatched("Stock API", "POST /reservations")).orElseThrow();

      assertJson("{\"id\": \"r-9\", \"orderId\": \"o-7\"}", answer.getContent());
   }

   /** AC-03: a call naming no open session, or matching no step of its workflow, is answered as it is today. */
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "an unknown session, unknown, POST /orders/{orderId}/confirm",
         "a closed session, closed, POST /orders/{orderId}/confirm",
         "an operation no step calls, open, GET /orders/{orderId}" })
   void aCallOutsideTheWorkflowOfAnOpenSessionIsAnsweredAsToday(String what, String session, String operation)
         throws Exception {
      WorkflowSession opened = sessionOnTheSaga();
      String sessionId = switch (session) {
         case "unknown" -> "s-unknown";
         case "closed" -> {
            arazzoSessionService.closeSession(opened.getId());
            yield opened.getId();
         }
         default -> opened.getId();
      };

      assertEquals(Optional.empty(), arazzoSessionAnswerService.answer(sessionId,
            received("Order API", operation, "/orders/o-42/confirm", null), null));
   }

   /**
    * An operation without example has nothing to answer with, as the response of one of its callbacks is not one: the
    * mocks answer as they do today.
    */
   @Test
   void anOperationWithoutExampleIsAnsweredAsToday() throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      Response example = dispatched("Order API", "POST /orders/{orderId}/confirm");
      responseRepository.deleteAll(responseRepository.findByOperationId(example.getOperationId()));
      Response callback = new Response();
      callback.setOperationId(example.getOperationId());
      callback.setName("confirmed");
      callback.setCallbackName("onConfirmed");
      callback.setStatus("200");
      responseRepository.save(callback);

      assertEquals(Optional.empty(), arazzoSessionAnswerService.answer(session.getId(), confirmOrder("o-42"), null));
   }

   /**
    * A session whose document is gone, or that names none, has no workflow to answer: its calls are still recorded, and
    * the mocks answer them as they do today.
    */
   @ParameterizedTest(name = "{0}")
   @CsvSource({ "a document deleted since the session was opened, deleted", "a session naming no document, none" })
   void aSessionWithoutDocumentRecordsItsCallsAndAnswersAsToday(String what, String document) throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      if ("deleted".equals(document)) {
         arazzoDocumentRepository.deleteById(session.getDocumentId());
      } else {
         session.setDocumentId(null);
         session = arazzoSessionRepository.save(session);
      }

      mocksAnswer(session, createOrder(), dispatched("Order API", "POST /orders"));

      assertEquals(1, arazzoSessionRepository.findByIdOptional(session.getId()).orElseThrow().getCallCount());
      assertEquals(Optional.empty(), arazzoSessionAnswerService.answer(session.getId(), confirmOrder("o-42"), null));
   }

   /** An example Microcks keeps no request with does not tell where the values go: it answers as it is. */
   @Test
   void anExampleWithoutRequestAnswersAsItIs() throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      mocksAnswer(session, createOrder(), dispatched("Order API", "POST /orders"));
      Response example = dispatched("Order API", "POST /orders/{orderId}/confirm");
      requestRepository.deleteAll(requestRepository.findByOperationId(example.getOperationId()));

      Response answer = arazzoSessionAnswerService.answer(session.getId(), confirmOrder("o-42"), null).orElseThrow();

      assertEquals(example.getContent(), answer.getContent());
   }

   /**
    * In reserveInTwoParts, two steps call reserveStock: each call answers the next of them, and a call after both
    * answers the last one again.
    */
   @Test
   void eachCallOfAnOperationSeveralStepsCallAnswersTheNextOfThem() throws Exception {
      WorkflowSession session = arazzoSessionService
            .openSession(orderSagaIsImportedAndLinked().getId(), "reserveInTwoParts").orElseThrow();
      Service stockApi = serviceRepository.findByNameAndVersion("Stock API", "1.0.0");

      List<String> answered = new ArrayList<>();
      for (int call = 0; call < 3; call++) {
         mocksAnswer(session, reserveStock("o-42"), dispatched("Stock API", "POST /reservations"));
         answered.add(String.join(",",
               List.of("reserveFirstPart", "reserveSecondPart").stream().filter(step -> serviceStateRepository
                     .findByServiceIdAndKey(stockApi.getId(), "arazzo-session:" + session.getId() + ":" + step) != null)
                     .toList()));
      }

      assertEquals(
            List.of("reserveFirstPart", "reserveFirstPart,reserveSecondPart", "reserveFirstPart,reserveSecondPart"),
            answered);
   }

   /** A step called again in the session answers again with the values of the session. */
   @Test
   void aStepCalledAgainAnswersTheSameWay() throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      mocksAnswer(session, createOrder(), dispatched("Order API", "POST /orders"));
      mocksAnswer(session, confirmOrder("o-42"), null);

      Response again = arazzoSessionAnswerService.answer(session.getId(), confirmOrder("o-42"), null).orElseThrow();

      assertEquals("o-42", JSON.readTree(again.getContent()).get("id").asText(), again.getContent());
   }

   /** Each session has its own state: the order another session placed does not flow into this one. */
   @Test
   void theStateOfASessionIsItsOwn() throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      WorkflowSession another = arazzoSessionService.openSession(session.getDocumentId(), SAGA).orElseThrow();
      mocksAnswer(another, createOrder(), dispatched("Order API", "POST /orders"));

      Response answer = arazzoSessionAnswerService
            .answer(session.getId(), received("Payment API", "POST /payments", "/payments", "{\"amount\": 25.0}"),
                  dispatched("Payment API", "POST /payments"))
            .orElseThrow();

      assertJson("{\"id\": \"p-3\", \"orderId\": \"o-1\", \"status\": \"CHARGED\"}", answer.getContent());
   }

   /**
    * The outputs of a step the mocks answered are kept in the state store of the API that answered it, under a key of
    * the session, and expire with the session, even once closing it moved its expiry.
    */
   @Test
   void theStateOfASessionIsKeptByTheApiThatAnsweredAndExpiresWithTheSession() throws Exception {
      WorkflowSession session = sessionOnTheSaga();
      mocksAnswer(session, createOrder(), dispatched("Order API", "POST /orders"));
      Service orderApi = serviceRepository.findByNameAndVersion("Order API", "1.0.0");
      String key = "arazzo-session:" + session.getId() + ":createOrder";

      ServiceState state = serviceStateRepository.findByServiceIdAndKey(orderApi.getId(), key);
      assertEquals(Map.of("id", "o-42", "amount", 25.0), JSON.readValue(state.getValue(), Map.class));
      assertExpiresWith(session, state);

      arazzoSessionService.closeSession(session.getId());

      assertExpiresWith(arazzoSessionRepository.findByIdOptional(session.getId()).orElseThrow(),
            serviceStateRepository.findByServiceIdAndKey(orderApi.getId(), key));
   }

   /** The state store keeps a value for a number of seconds: the state expires in the last second of the session. */
   private static void assertExpiresWith(WorkflowSession session, ServiceState state) {
      long before = session.getExpireAt().getTime() - state.getExpireAt().getTime();
      assertTrue(before >= 0 && before <= 1000, () -> "the state expires " + before + " ms before the session");
   }

   private WorkflowSession sessionOnTheSaga() throws Exception {
      WorkflowDocument saga = orderSagaIsImportedAndLinked();
      return arazzoSessionService.openSession(saga.getId(), SAGA).orElseThrow();
   }

   /** The example of an operation, as the mocks of an API 1.0.0 dispatch it. */
   private Response dispatched(String apiName, String operationName) {
      Service api = serviceRepository.findByNameAndVersion(apiName, "1.0.0");
      return responseRepository.findByOperationId(api.getId() + "-" + operationName).stream()
            .filter(response -> !response.isFault()).findFirst().orElseThrow();
   }

   /**
    * The mocks answer a call in a session, then record it there with their answer, as they do for a REST call.
    * @return The answer
    */
   private Response mocksAnswer(WorkflowSession session, WorkflowSessionRecordedCall call, Response dispatched) {
      Response answer = arazzoSessionAnswerService.answer(session.getId(), call, dispatched).orElse(dispatched);
      call.setResponseStatus(Integer.parseInt(answer.getStatus()));
      call.setResponseHeaders(Set.of(new Header("Content-Type", Set.of(answer.getMediaType()))));
      call.setResponseBody(answer.getContent());
      arazzoSessionService.recordCall(session.getId(), call);
      return answer;
   }

   /** The order saga service places an order for customer c-7. */
   private static WorkflowSessionRecordedCall createOrder() {
      return received("Order API", "POST /orders", "/orders",
            "{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0, "
                  + "\"currency\": \"EUR\"}");
   }

   /** The order saga service confirms an order. */
   private static WorkflowSessionRecordedCall confirmOrder(String orderId) {
      return received("Order API", "POST /orders/{orderId}/confirm", "/orders/" + orderId + "/confirm", null);
   }

   /** The order saga service reserves the items of an order. */
   private static WorkflowSessionRecordedCall reserveStock(String orderId) {
      return received("Stock API", "POST /reservations", "/reservations",
            "{\"orderId\": \"" + orderId + "\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}]}");
   }

   /** A call the mocks of an API 1.0.0 receive in a session, before they answer it. */
   private static WorkflowSessionRecordedCall received(String apiName, String operationName, String path, String body) {
      WorkflowSessionRecordedCall call = new WorkflowSessionRecordedCall();
      call.setServiceName(apiName);
      call.setServiceVersion("1.0.0");
      call.setOperationName(operationName);
      call.setMethod(operationName.substring(0, operationName.indexOf(' ')));
      call.setPath(path);
      call.setRequestUrl("http://localhost:8080/rest/" + apiName.replace(' ', '+') + "/1.0.0" + path);
      call.setRequestHeaders(Set.of(new Header("Content-Type", Set.of("application/json"))));
      call.setRequestBody(body);
      return call;
   }

   private static void assertJson(String expected, String actual) throws IOException {
      assertEquals(JSON.readTree(expected), JSON.readTree(actual), actual);
   }

   private WorkflowDocument orderSagaIsImportedAndLinked() throws Exception {
      for (String api : List.of("order-api", "stock-api", "payment-api")) {
         Path contract = Files.createTempFile(api, ".yaml");
         Files.writeString(contract, example("stateful-saga.contracts.yaml", api));
         serviceService.importServiceDefinition(contract.toFile(), null, new ArtifactInfo(api + ".yaml", true));
      }
      arazzoDocumentService.importArazzoMetadata(example("stateful-saga.arazzo.yaml", "order-saga-metadata"));
      return arazzoDocumentService.importArazzoDocument(example("stateful-saga.arazzo.yaml", "order-saga"));
   }

   /** One YAML document per example of a fixture, each announced by a {@code # example: <name> - <what>} comment. */
   private static String example(String fixture, String example) throws IOException {
      String marker = "# example: " + example + " ";
      try (InputStream stream = ArazzoSessionAnswerTest.class.getResourceAsStream(FIXTURES + fixture)) {
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.startsWith(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fixture));
      }
   }
}
