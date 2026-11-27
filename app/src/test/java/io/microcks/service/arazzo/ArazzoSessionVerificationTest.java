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
import io.microcks.domain.Request;
import io.microcks.domain.Response;
import io.microcks.domain.arazzo.ArazzoDocument;
import io.microcks.domain.arazzo.ArazzoSession;
import io.microcks.domain.arazzo.ArazzoSessionCall;
import io.microcks.domain.arazzo.WorkflowParameterValue;
import io.microcks.domain.arazzo.WorkflowResult;
import io.microcks.domain.arazzo.WorkflowRunOrigin;
import io.microcks.domain.arazzo.WorkflowRunStatus;
import io.microcks.domain.arazzo.WorkflowRunSummary;
import io.microcks.domain.arazzo.WorkflowSessionCall;
import io.microcks.domain.arazzo.WorkflowStepResult;
import io.microcks.repository.RequestRepository;
import io.microcks.repository.ResponseRepository;
import io.microcks.repository.arazzo.ArazzoSessionRepository;
import io.microcks.repository.arazzo.WorkflowRunFilter;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance tests of verifying that an application, an order saga service, followed the workflow placeOrderSaga of
 * Order saga 1.0.0 against the mocks of Order, Stock and Payment API 1.0.0. They enter through the session use case:
 * the test opens a session, the mocks record the calls the service makes in that session, then the test asks for the
 * verification of the session.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSessionVerificationTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String SAGA = "placeOrderSaga";

   @Inject
   private ArazzoSessionService arazzoSessionService;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ArazzoRunHistoryService arazzoRunHistoryService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private RequestRepository requestRepository;

   @Inject
   private ArazzoSessionRepository arazzoSessionRepository;

   @Inject
   private ResponseRepository responseRepository;

   /** Walking skeleton: a call the saga service makes in its session is matched to its step, in the history. */
   @Test
   void aCallMadeInTheSessionIsMatchedToItsStepAndTheVerificationJoinsTheHistory() throws Exception {
      ArazzoDocument saga = orderSagaIsImportedAndLinked();
      ArazzoSession session = arazzoSessionService.openSession(saga.getId(), SAGA).orElseThrow();

      arazzoSessionService.recordCall(session.getId(), createOrder());

      Date before = new Date();
      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunOrigin.SESSION, verification.getOrigin());
      assertEquals(session.getId(), verification.getSession().sessionId());
      assertEquals(List.of(saga.getId(), "Order saga", "1.0.0", SAGA, "MOCKS", "{}", "1"),
            List.of(verification.getDocumentId(), verification.getDocumentTitle(), verification.getDocumentVersion(),
                  verification.getWorkflowId(), String.valueOf(verification.getTarget()),
                  String.valueOf(verification.getInputs()), String.valueOf(verification.getNumber())));
      assertTrue(!verification.getStartedDate().before(before), "started before the verification");
      WorkflowStepResult createOrder = verification.getSteps().get(0);
      assertEquals("0 createOrder PASSED 201", createOrder.index() + " " + createOrder.stepId() + " "
            + createOrder.status() + " " + createOrder.statusCode());
      assertEquals(List.of(new WorkflowSessionCall(0, "Order API", "1.0.0", "POST", "/orders", "createOrder")),
            verification.getSession().calls());
      List<WorkflowRunSummary> history = arazzoRunHistoryService.getRuns(saga.getId(),
            new WorkflowRunFilter(null, null, null), 0, 20);
      assertEquals(List.of(verification.getId() + " " + WorkflowRunOrigin.SESSION),
            history.stream().map(run -> run.id() + " " + run.origin()).toList());
   }

   /** AC-01: the 4 calls of the saga, in the order of the workflow, pass step by step. */
   @Test
   void callsMatchingTheWorkflowPassStepByStep() throws Exception {
      ArazzoSession session = sessionOnTheSaga();

      record(session, createOrder(), reserveStock("o-42"), chargePayment("o-42"), confirmOrder("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.SUCCESS, verification.getStatus());
      assertEquals(
            List.of("0 createOrder PASSED", "1 reserveStock PASSED", "2 chargePayment PASSED", "3 confirmOrder PASSED"),
            described(verification));
      assertEquals(List.of("0 createOrder", "1 reserveStock", "2 chargePayment", "3 confirmOrder"),
            verification.getSession().calls().stream().map(call -> call.index() + " " + call.stepId()).toList());
   }

   /** AC-02: a call out of order fails the verification, naming the step expected before it. */
   @Test
   void aCallOutOfOrderFailsTheVerification() throws Exception {
      ArazzoSession session = sessionOnTheSaga();

      record(session, createOrder(), chargePayment("o-42"), reserveStock("o-42"), confirmOrder("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, verification.getStatus());
      assertEquals(List.of("0 createOrder PASSED", "1 reserveStock PASSED",
            "2 chargePayment FAILED step `reserveStock` expected before `chargePayment`", "3 confirmOrder PASSED"),
            described(verification));
   }

   /** AC-03: data not taken from the previous step fails the verification, naming its source and both values. */
   @Test
   void dataNotTakenFromThePreviousStepFailsTheVerification() throws Exception {
      ArazzoSession session = sessionOnTheSaga();

      record(session, createOrder(), reserveStock("o-1"), chargePayment("o-42"), confirmOrder("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, verification.getStatus());
      assertEquals(List.of("0 createOrder PASSED",
            "1 reserveStock FAILED `reserveStock` body `/orderId` should come from `$steps.createOrder.outputs.id` "
                  + "(o-42), got o-1",
            "2 chargePayment PASSED", "3 confirmOrder PASSED"), described(verification));
   }

   /** A value of a path parameter not taken from the previous step fails its step the same way. */
   @Test
   void aPathParameterNotTakenFromThePreviousStepFailsTheVerification() throws Exception {
      ArazzoSession session = sessionOnTheSaga();
      ArazzoSessionCall confirmAnotherOrder = confirmOrder("o-1");
      confirmAnotherOrder.setResponseStatus(404);

      record(session, createOrder(), reserveStock("o-42"), chargePayment("o-42"), confirmAnotherOrder);

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals("3 confirmOrder FAILED `confirmOrder` path `orderId` should come from "
            + "`$steps.createOrder.outputs.id` (o-42), got o-1", described(verification).get(3));
   }

   /** The steps show the outputs read from the mock responses, and the values received with their source. */
   @Test
   void theStepsShowTheirOutputsAndTheValuesTheyReceived() throws Exception {
      ArazzoSession session = sessionOnTheSaga();

      record(session, createOrder(), reserveStock("o-42"), chargePayment("o-42"), confirmOrder("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      WorkflowStepResult createOrder = verification.getSteps().get(0);
      assertEquals(Map.of("id", "o-42", "amount", 25.0), createOrder.outputs());
      assertEquals(Map.of("id", "$response.body#/id", "amount", "$response.body#/amount"), createOrder.outputOrigins());
      assertEquals(List.of(new WorkflowParameterValue("orderId", "path", "o-42", "$steps.createOrder.outputs.id")),
            verification.getSteps().get(3).parameters());
      assertEquals(Map.of("orderId", "o-42", "reservationId", "r-9"), verification.getOutputs());
   }

   /** Each call is checked against the contract of the API it called: a request it does not allow fails its step. */
   @Test
   void aRequestTheContractOfItsApiDoesNotAllowFailsItsStep() throws Exception {
      ArazzoSession session = sessionOnTheSaga();
      ArazzoSessionCall chargeAmountAsText = chargePayment("o-42");
      chargeAmountAsText.setRequestBody("{\"orderId\": \"o-42\", \"amount\": \"25.0\"}");

      record(session, createOrder(), reserveStock("o-42"), chargeAmountAsText, confirmOrder("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, verification.getStatus());
      assertEquals(
            "2 chargePayment FAILED `chargePayment` request does not conform to Payment API 1.0.0: "
                  + "the request body does not follow its schema at /amount: string found, number expected",
            described(verification).get(2));
   }

   /** A matched step keeps the exchange of its call, as the step of a run keeps its own, for the run page. */
   @Test
   void aMatchedStepKeepsTheExchangeOfItsCall() throws Exception {
      ArazzoSession session = sessionOnTheSaga();
      record(session, createOrder());

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();

      String testCaseId = verification.getSteps().get(0).testCaseId();
      assertEquals(verification.getId() + "-0", testCaseId);
      Request request = requestRepository.findByTestCaseId(testCaseId).get(0);
      Response response = responseRepository.findByIdOptional(request.getResponseId()).orElseThrow();
      assertEquals(List.of("createOrder", createOrder().getRequestBody(), "[Content-Type]"), List.of(request.getName(),
            request.getContent(), request.getHeaders().stream().map(Header::getName).toList().toString()));
      assertEquals(List.of("createOrder", "201", createOrder().getResponseBody(), "[Content-Type]"),
            List.of(response.getName(), response.getStatus(), response.getContent(),
                  response.getHeaders().stream().map(Header::getName).toList().toString()));
   }

   /** A number sent from an output is compared by value, whatever its type: 25 is 25.0, 30.0 is not. */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "25 | 2 chargePayment PASSED",
         "30.0 | 2 chargePayment FAILED `chargePayment` body `/amount` should come from "
               + "`$steps.createOrder.outputs.amount` (25.0), got 30.0" })
   void aNumberSentFromAnOutputIsComparedByValue(String amount, String verdict) throws Exception {
      ArazzoSession session = sessionOnTheSaga();
      ArazzoSessionCall charge = chargePayment("o-42");
      charge.setRequestBody("{\"orderId\": \"o-42\", \"amount\": " + amount + "}");

      record(session, createOrder(), reserveStock("o-42"), charge, confirmOrder("o-42"));

      assertEquals(verdict, described(arazzoSessionService.verify(session.getId()).orElseThrow()).get(2));
   }

   /**
    * A session opened with the inputs of the workflow checks the values coming from them like the others; without
    * inputs, nothing coming from them is checked.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = { "c-7 | c-7 | 0 createOrder PASSED",
         "c-7 | c-9 | 0 createOrder FAILED `createOrder` body `/customerId` should come from `$inputs.customerId` "
               + "(c-7), got c-9",
         "null | c-9 | 0 createOrder PASSED" })
   void theValuesComingFromTheInputsAreCheckedWhenTheSessionHasThem(String given, String sent, String verdict)
         throws Exception {
      ArazzoDocument saga = orderSagaIsImportedAndLinked();
      Map<String, Object> inputs = given == null ? null : Map.of("customerId", given);
      ArazzoSession session = arazzoSessionService.openSession(saga.getId(), SAGA, inputs).orElseThrow();
      ArazzoSessionCall placeOrder = createOrder();
      placeOrder.setRequestBody(placeOrder.getRequestBody().replace("c-7", sent));

      record(session, placeOrder, reserveStock("o-42"), chargePayment("o-42"), confirmOrder("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(verdict, described(verification).get(0));
      assertEquals(inputs == null ? Map.of() : inputs, verification.getInputs());
   }

   /** Inputs given to a session are checked against the workflow: an unknown, invalid or missing one is refused. */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "'{\"customerId\": \"c-7\", \"coupon\": \"WELCOME\"}' | Unknown input: coupon",
         "'{\"customerId\": 7}' | Invalid input customerId: a string is expected, got 7",
         "'{}' | Missing input: customerId" })
   void aSessionWithUnknownInvalidOrMissingInputsIsRefused(String inputs, String reason) throws Exception {
      ArazzoDocument saga = orderSagaIsImportedAndLinked();
      Map<String, Object> given = new ObjectMapper().readValue(inputs, new TypeReference<Map<String, Object>>() {
      });

      assertEquals(reason, assertThrows(ArazzoRunRefusedException.class,
            () -> arazzoSessionService.openSession(saga.getId(), SAGA, given)).getMessage());
      assertEquals(0, arazzoSessionRepository.count());
   }

   /**
    * dependsOn relaxes the order (Arazzo 1.1): in Order saga 1.1.0, reserveStock and chargePayment both depend on
    * createOrder only, so they may come in any order; confirmOrder declares no dependsOn, so it comes after every step
    * listed before it.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "createOrder,chargePayment,reserveStock,confirmOrder | SUCCESS | ",
         "createOrder,chargePayment,confirmOrder,reserveStock | FAILURE | "
               + "3 confirmOrder FAILED step `reserveStock` expected before `confirmOrder`" })
   void dependsOnRelaxesTheOrderOfTheSteps(String received, WorkflowRunStatus status, String failedStep)
         throws Exception {
      ArazzoDocument saga = orderSagaIsImportedAndLinked("order-saga-parallel");
      ArazzoSession session = arazzoSessionService.openSession(saga.getId(), SAGA).orElseThrow();
      Map<String, ArazzoSessionCall> calls = Map.of("createOrder", createOrder(), "reserveStock", reserveStock("o-42"),
            "chargePayment", chargePayment("o-42"), "confirmOrder", confirmOrder("o-42"));

      record(session, Arrays.stream(received.split(",")).map(calls::get).toArray(ArazzoSessionCall[]::new));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(status, verification.getStatus());
      assertEquals(failedStep == null ? List.of() : List.of(failedStep),
            described(verification).stream().filter(step -> step.contains("FAILED")).toList());
   }

   /** AC-04: a step the service never calls fails the verification as missing. */
   @Test
   void aMissingStepFailsTheVerification() throws Exception {
      ArazzoSession session = sessionOnTheSaga();

      record(session, createOrder(), reserveStock("o-42"), chargePayment("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, verification.getStatus());
      assertEquals(List.of("0 createOrder PASSED", "1 reserveStock PASSED", "2 chargePayment PASSED",
            "3 confirmOrder FAILED step `confirmOrder` missing"), described(verification));
      WorkflowStepResult confirmOrder = verification.getSteps().get(3);
      assertNull(confirmOrder.statusCode());
      assertNull(confirmOrder.testCaseId());
   }

   /** A call to an operation no step calls, or a second call of an operation, is matched to no step. */
   @Test
   void aCallNoStepTakesIsMatchedToNone() throws Exception {
      ArazzoSession session = sessionOnTheSaga();

      record(session, createOrder(), createOrder(), reserveStock("o-42"), chargePayment("o-42"),
            call("Payment API", "GET /payments", "/payments", null, 200, "[]"), confirmOrder("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.SUCCESS, verification.getStatus());
      assertEquals(Arrays.asList("createOrder", null, "reserveStock", "chargePayment", null, "confirmOrder"),
            verification.getSession().calls().stream().map(WorkflowSessionCall::stepId).toList());
   }

   /** AC-05: two sessions on the saga at the same time; each verification reports only the calls of its session. */
   @Test
   void sessionsAreIsolated() throws Exception {
      ArazzoSession first = sessionOnTheSaga();
      ArazzoSession second = arazzoSessionService.openSession(first.getDocumentId(), SAGA).orElseThrow();

      record(first, createOrder());
      record(second, createOrder(), reserveStock("o-42"));
      record(first, reserveStock("o-42"), chargePayment("o-42"));
      record(second, chargePayment("o-42"), confirmOrder("o-42"));
      record(first, confirmOrder("o-42"));

      for (ArazzoSession session : List.of(first, second)) {
         WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
         assertEquals(WorkflowRunStatus.SUCCESS, verification.getStatus());
         assertEquals(List.of("0 createOrder", "1 reserveStock", "2 chargePayment", "3 confirmOrder"),
               verification.getSession().calls().stream().map(call -> call.index() + " " + call.stepId()).toList());
      }
   }

   /** A closed session records no more calls, and can still be verified until it expires. */
   @Test
   void aClosedSessionRecordsNoMoreCallsAndCanStillBeVerified() throws Exception {
      ArazzoSession session = sessionOnTheSaga();
      record(session, createOrder(), reserveStock("o-42"), chargePayment("o-42"));

      assertTrue(arazzoSessionService.closeSession(session.getId()));
      record(session, confirmOrder("o-42"));

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals("3 confirmOrder FAILED step `confirmOrder` missing", described(verification).get(3));
      assertEquals(3, verification.getSession().calls().size());
   }

   /** A call naming an unknown session is not recorded, and an unknown session has no verification. */
   @Test
   void anUnknownSessionRecordsNothingAndHasNoVerification() throws Exception {
      orderSagaIsImportedAndLinked();

      arazzoSessionService.recordCall("unknown", createOrder());

      assertEquals(Optional.empty(), arazzoSessionService.verify("unknown"));
      assertFalse(arazzoSessionService.closeSession("unknown"));
   }

   /** No session is opened on an unknown document; one on an unknown workflow is refused, naming it. */
   @Test
   void aSessionIsOpenedOnAKnownWorkflowOnly() throws Exception {
      ArazzoDocument saga = orderSagaIsImportedAndLinked();

      assertEquals(Optional.empty(), arazzoSessionService.openSession("unknown", SAGA));
      assertEquals("Unknown workflow: cancelSaga", assertThrows(ArazzoRunRefusedException.class,
            () -> arazzoSessionService.openSession(saga.getId(), "cancelSaga")).getMessage());
   }

   /** A workflow whose steps call no operation of a linked API cannot be verified: the session is refused. */
   @Test
   void aSessionOnAWorkflowWhoseSourcesAreNotLinkedIsRefused() throws Exception {
      ArazzoDocument saga = arazzoDocumentService.importArazzoDocument(example("order-saga.arazzo.yaml", "order-saga"));

      assertEquals("Workflow placeOrderSaga cannot be verified: no API name and version for this source",
            assertThrows(ArazzoRunRefusedException.class, () -> arazzoSessionService.openSession(saga.getId(), SAGA))
                  .getMessage());
   }

   /** A session is opened on a workflow by the title and version of its document, as the Microcks API names it. */
   @Test
   void aSessionIsOpenedByTheTitleAndVersionOfTheDocument() throws Exception {
      ArazzoDocument saga = orderSagaIsImportedAndLinked();

      ArazzoSession session = arazzoSessionService.openSession("Order saga:1.0.0", SAGA).orElseThrow();

      assertEquals(List.of(saga.getId(), "Order saga", "1.0.0", SAGA), List.of(session.getDocumentId(),
            session.getDocumentTitle(), session.getDocumentVersion(), session.getWorkflowId()));
   }

   private ArazzoSession sessionOnTheSaga() throws Exception {
      ArazzoDocument saga = orderSagaIsImportedAndLinked();
      return arazzoSessionService.openSession(saga.getId(), SAGA).orElseThrow();
   }

   private void record(ArazzoSession session, ArazzoSessionCall... calls) {
      for (ArazzoSessionCall call : calls) {
         arazzoSessionService.recordCall(session.getId(), call);
      }
   }

   /** Each step as index, id and status, then the reason it failed when it did. */
   private static List<String> described(WorkflowResult verification) {
      return verification.getSteps().stream().map(step -> step.index() + " " + step.stepId() + " " + step.status()
            + (step.message() == null ? "" : " " + step.message())).toList();
   }

   /** The service reserves the items of an order; the mock answers with reservation r-9. */
   private static ArazzoSessionCall reserveStock(String orderId) {
      return call("Stock API", "POST /reservations", "/reservations",
            "{\"orderId\": \"" + orderId + "\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}]}", 201,
            "{\"id\": \"r-9\", \"orderId\": \"o-42\"}");
   }

   /** The service charges an order 25.0; the mock answers with payment p-3. */
   private static ArazzoSessionCall chargePayment(String orderId) {
      return call("Payment API", "POST /payments", "/payments", "{\"orderId\": \"" + orderId + "\", \"amount\": 25.0}",
            201, "{\"id\": \"p-3\", \"orderId\": \"o-42\", \"status\": \"CHARGED\"}");
   }

   /** The service confirms an order; the mock answers with order o-42, confirmed. */
   private static ArazzoSessionCall confirmOrder(String orderId) {
      return call("Order API", "POST /orders/{orderId}/confirm", "/orders/" + orderId + "/confirm", null, 200,
            "{\"id\": \"o-42\", \"status\": \"CONFIRMED\"}");
   }

   /** The order saga service places order o-42 for customer c-7; the mock answers with order o-42. */
   private static ArazzoSessionCall createOrder() {
      return call("Order API", "POST /orders", "/orders",
            "{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0, "
                  + "\"currency\": \"EUR\"}",
            201, "{\"id\": \"o-42\", \"customerId\": \"c-7\", \"amount\": 25.0, \"status\": \"PENDING\"}");
   }

   /** A call to the mocks of an API 1.0.0, as the mocks record it in a session. */
   private static ArazzoSessionCall call(String apiName, String operationName, String path, String body, int status,
         String responseBody) {
      ArazzoSessionCall call = new ArazzoSessionCall();
      call.setServiceName(apiName);
      call.setServiceVersion("1.0.0");
      call.setOperationName(operationName);
      call.setMethod(operationName.substring(0, operationName.indexOf(' ')));
      call.setPath(path);
      call.setRequestUrl("http://localhost:8080/rest/" + apiName.replace(' ', '+') + "/1.0.0" + path);
      call.setRequestHeaders(Set.of(new Header("Content-Type", Set.of("application/json"))));
      call.setRequestBody(body);
      call.setResponseStatus(status);
      call.setResponseHeaders(Set.of(new Header("Content-Type", Set.of("application/json"))));
      call.setResponseBody(responseBody);
      return call;
   }

   private ArazzoDocument orderSagaIsImportedAndLinked() throws Exception {
      return orderSagaIsImportedAndLinked("order-saga");
   }

   /** Imports the Order, Stock and Payment APIs, then a saga of the fixture and its overlay, linking them. */
   private ArazzoDocument orderSagaIsImportedAndLinked(String saga) throws Exception {
      for (String api : List.of("order-api", "stock-api", "payment-api")) {
         Path contract = Files.createTempFile(api, ".yaml");
         Files.writeString(contract, example("order-saga.contracts.yaml", api));
         serviceService.importServiceDefinition(contract.toFile(), null, new ArtifactInfo(api + ".yaml", true));
      }
      arazzoDocumentService.importArazzoSources(example("order-saga.arazzo.yaml", saga + "-sources"));
      return arazzoDocumentService.importArazzoDocument(example("order-saga.arazzo.yaml", saga));
   }

   /** One YAML document per example of a fixture, each announced by a {@code # example: <name> - <what>} comment. */
   private static String example(String fixture, String example) throws IOException {
      String marker = "# example: " + example + " ";
      try (InputStream stream = ArazzoSessionVerificationTest.class.getResourceAsStream(FIXTURES + fixture)) {
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.startsWith(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fixture));
      }
   }
}
