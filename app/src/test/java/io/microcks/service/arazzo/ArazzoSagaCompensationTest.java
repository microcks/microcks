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
import io.microcks.domain.Operation;
import io.microcks.domain.Response;
import io.microcks.domain.Service;
import io.microcks.domain.workflow.WorkflowDocument;
import io.microcks.domain.workflow.WorkflowSession;
import io.microcks.domain.workflow.WorkflowSessionRecordedCall;
import io.microcks.domain.workflow.WorkflowResult;
import io.microcks.domain.workflow.WorkflowRunStatus;
import io.microcks.domain.workflow.WorkflowSessionCall;
import io.microcks.repository.ServiceRepository;
import io.microcks.repository.workflow.WorkflowSessionRepository;
import io.microcks.service.ArtifactInfo;
import io.microcks.service.ServiceService;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Acceptance tests of making a step of a saga fail on the mocks and verifying the compensation: the developer of an
 * order saga service opens a session on compensatePaymentRefused of Order saga 1.0.0 in which chargePayment answers
 * with the example card refused of Payment API 1.0.0, then verifies that the service released the stock and cancelled
 * the order. They enter through the session use case.
 * @author SebastienDegodez
 */
@QuarkusTest
class ArazzoSagaCompensationTest {

   private static final String FIXTURES = "/io/microcks/service/arazzo/";
   private static final String COMPENSATION = "compensatePaymentRefused";
   private static final Map<String, String> CARD_REFUSED = Map.of("chargePayment", "card refused");

   @Inject
   private ArazzoSessionService arazzoSessionService;

   @Inject
   private ArazzoSessionFailures arazzoSessionFailures;

   @Inject
   private ArazzoDocumentService arazzoDocumentService;

   @Inject
   private ServiceService serviceService;

   @Inject
   private ServiceRepository serviceRepository;

   @Inject
   private WorkflowSessionRepository workflowSessionRepository;

   /** AC-01: chargePayment, set to fail in the session, answers with the example card refused of Payment API. */
   @Test
   void anInjectedFailureMakesTheStepAnswerWithTheChosenExample() throws Exception {
      WorkflowSession session = sessionOnTheCompensation(CARD_REFUSED);

      Response answer = injectedFailure(session, "Payment API", "POST /payments").orElseThrow();

      assertEquals(List.of("card refused", "402"), List.of(answer.getName(), answer.getStatus()));
      assertEquals("{\"orderId\":\"o-42\",\"status\":\"REFUSED\",\"reason\":\"Card refused\"}", answer.getContent());
      assertEquals(List.of("X-Decline-Reason [insufficient-funds]"),
            answer.getHeaders().stream().map(header -> header.getName() + " " + header.getValues()).toList());
      assertEquals(CARD_REFUSED, session.getFailures());
   }

   /** Only the step set to fail answers with its example: the other steps of the session answer as usual. */
   @Test
   void theOtherOperationsOfTheSessionAnswerAsUsual() throws Exception {
      WorkflowSession session = sessionOnTheCompensation(CARD_REFUSED);

      assertEquals(Optional.empty(), injectedFailure(session, "Order API", "POST /orders"));
      assertEquals(Optional.empty(), injectedFailure(session, "Stock API", "DELETE /reservations/{reservationId}"));
   }

   /** AC-02: the service releases reservation r-9 and cancels order o-42 after the refusal: the compensation passes. */
   @Test
   void aCompleteCompensationPassesTheVerification() throws Exception {
      WorkflowSession session = sessionOnTheCompensation(CARD_REFUSED);

      record(session, createOrder(), reserveStock(), chargePaymentRefused(), releaseStock(), cancelOrder());

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.SUCCESS, verification.getStatus());
      assertEquals(List.of("0 createOrder PASSED", "1 reserveStock PASSED", "2 chargePayment PASSED",
            "3 releaseStock PASSED", "4 cancelOrder PASSED"), described(verification));
   }

   /** AC-03: the service cancels the order but never releases the stock: releaseStock is missing. */
   @Test
   void aMissingCompensationStepFailsTheVerification() throws Exception {
      WorkflowSession session = sessionOnTheCompensation(CARD_REFUSED);

      record(session, createOrder(), reserveStock(), chargePaymentRefused(), cancelOrder());

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, verification.getStatus());
      assertEquals("3 releaseStock FAILED step `releaseStock` missing", described(verification).get(3));
   }

   /** AC-04: the service still confirms the order after the refused payment: that call was not expected. */
   @Test
   void aCallThatShouldNotHappenAfterTheFailureFailsTheVerification() throws Exception {
      WorkflowSession session = sessionOnTheCompensation(CARD_REFUSED);

      record(session, createOrder(), reserveStock(), chargePaymentRefused(), confirmOrder(), releaseStock(),
            cancelOrder());

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.FAILURE, verification.getStatus());
      WorkflowSessionCall confirmOrder = verification.getSession().calls().get(3);
      assertEquals("3 POST /orders/o-42/confirm null unexpected call `confirmOrder` after `chargePayment`",
            confirmOrder.index() + " " + confirmOrder.method() + " " + confirmOrder.path() + " " + confirmOrder.stepId()
                  + " " + confirmOrder.failure());
      assertEquals(List.of("0 createOrder PASSED", "1 reserveStock PASSED", "2 chargePayment PASSED",
            "3 releaseStock PASSED", "4 cancelOrder PASSED"), described(verification));
   }

   /**
    * A call to the mocks of an API no source of the document is linked to, such as its Notification API, is matched to
    * no step and does not fail the verification: only the calls to linked APIs are expected by the workflow.
    */
   @Test
   void aCallToAnApiNoSourceIsLinkedToIsNotUnexpected() throws Exception {
      WorkflowSession session = sessionOnTheCompensation(CARD_REFUSED);

      record(session, createOrder(), reserveStock(), chargePaymentRefused(),
            call("Notification API", "POST /notifications", "/notifications", "{\"orderId\": \"o-42\"}", 202, null),
            releaseStock(), cancelOrder());

      WorkflowResult verification = arazzoSessionService.verify(session.getId()).orElseThrow();
      assertEquals(WorkflowRunStatus.SUCCESS, verification.getStatus());
      WorkflowSessionCall notification = verification.getSession().calls().get(3);
      assertEquals("null null", notification.stepId() + " " + notification.failure());
   }

   /** AC-05: of two sessions calling chargePayment, only the one that set it to fail receives card refused. */
   @Test
   void injectionIsLimitedToItsSession() throws Exception {
      WorkflowSession withFailure = sessionOnTheCompensation(CARD_REFUSED);
      WorkflowSession withoutFailure = arazzoSessionService
            .openSession(withFailure.getDocumentId(), COMPENSATION, null, Map.of()).orElseThrow();

      assertEquals("402", injectedFailure(withFailure, "Payment API", "POST /payments").orElseThrow().getStatus());
      assertEquals(Optional.empty(), injectedFailure(withoutFailure, "Payment API", "POST /payments"));
   }

   /** Once its session is closed, a step set to fail answers as usual again, as the session records no more calls. */
   @Test
   void aClosedSessionInjectsNothingAnyMore() throws Exception {
      WorkflowSession session = sessionOnTheCompensation(CARD_REFUSED);

      arazzoSessionService.closeSession(session.getId());

      assertEquals(Optional.empty(), injectedFailure(session, "Payment API", "POST /payments"));
   }

   /** Outside any session, or naming a session that does not exist, nothing is injected: the mocks answer as today. */
   @Test
   void nothingIsInjectedOutsideASession() throws Exception {
      sessionOnTheCompensation(CARD_REFUSED);
      Service payment = serviceRepository.findByNameAndVersion("Payment API", "1.0.0");

      assertEquals(Optional.empty(), arazzoSessionFailures.injectedFailure(null, payment, operation(payment)));
      assertEquals(Optional.empty(), arazzoSessionFailures.injectedFailure("unknown", payment, operation(payment)));
   }

   /**
    * A failure set on a step the workflow does not have, or with an example its operation does not have, even when
    * another operation of the same API has it, is refused.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "refundPayment | card refused | Cannot make step `refundPayment` fail: compensatePaymentRefused has no such step",
         "chargePayment | card expired | Cannot make step `chargePayment` fail: POST /payments of Payment API 1.0.0 "
               + "has no example `card expired`",
         "cancelOrder   | c-7          | Cannot make step `cancelOrder` fail: POST /orders/{orderId}/cancel of "
               + "Order API 1.0.0 has no example `c-7`" })
   void aFailureOnAnUnknownStepOrExampleIsRefusedAndNoSessionIsOpened(String stepId, String example, String reason)
         throws Exception {
      WorkflowDocument saga = orderSagaIsImportedAndLinked();

      assertEquals(reason,
            assertThrows(ArazzoRunRefusedException.class,
                  () -> arazzoSessionService.openSession(saga.getId(), COMPENSATION, null, Map.of(stepId, example)))
                        .getMessage());
      assertEquals(0, workflowSessionRepository.count());
   }

   private Optional<Response> injectedFailure(WorkflowSession session, String apiName, String operationName) {
      Service api = serviceRepository.findByNameAndVersion(apiName, "1.0.0");
      Operation operation = api.getOperations().stream().filter(candidate -> candidate.getName().equals(operationName))
            .findFirst().orElseThrow();
      return arazzoSessionFailures.injectedFailure(session.getId(), api, operation);
   }

   private static Operation operation(Service payment) {
      return payment.getOperations().get(0);
   }

   private WorkflowSession sessionOnTheCompensation(Map<String, String> failures) throws Exception {
      WorkflowDocument saga = orderSagaIsImportedAndLinked();
      return arazzoSessionService.openSession(saga.getId(), COMPENSATION, null, failures).orElseThrow();
   }

   private void record(WorkflowSession session, WorkflowSessionRecordedCall... calls) {
      for (WorkflowSessionRecordedCall call : calls) {
         arazzoSessionService.recordCall(session.getId(), call);
      }
   }

   /** Each step as index, id and status, then the reason it failed when it did. */
   private static List<String> described(WorkflowResult verification) {
      return verification.getSteps().stream().map(step -> step.index() + " " + step.stepId() + " " + step.status()
            + (step.message() == null ? "" : " " + step.message())).toList();
   }

   /** The order saga service places order o-42 for customer c-7; the mock answers with order o-42. */
   private static WorkflowSessionRecordedCall createOrder() {
      return call("Order API", "POST /orders", "/orders",
            "{\"customerId\": \"c-7\", \"items\": [{\"sku\": \"ABC-1\", \"quantity\": 2}], \"amount\": 25.0}", 201,
            "{\"id\": \"o-42\", \"customerId\": \"c-7\", \"amount\": 25.0, \"status\": \"PENDING\"}");
   }

   /** The service reserves the items of order o-42; the mock answers with reservation r-9. */
   private static WorkflowSessionRecordedCall reserveStock() {
      return call("Stock API", "POST /reservations", "/reservations", "{\"orderId\": \"o-42\"}", 201,
            "{\"id\": \"r-9\", \"orderId\": \"o-42\", \"status\": \"RESERVED\"}");
   }

   /** The service charges order o-42; the mock answers with the injected example card refused. */
   private static WorkflowSessionRecordedCall chargePaymentRefused() {
      return call("Payment API", "POST /payments", "/payments", "{\"orderId\": \"o-42\", \"amount\": 25.0}", 402,
            "{\"orderId\": \"o-42\", \"status\": \"REFUSED\", \"reason\": \"Card refused\"}");
   }

   /** The service releases reservation r-9. */
   private static WorkflowSessionRecordedCall releaseStock() {
      return call("Stock API", "DELETE /reservations/{reservationId}", "/reservations/r-9", null, 200,
            "{\"id\": \"r-9\", \"status\": \"RELEASED\"}");
   }

   /** The service cancels order o-42. */
   private static WorkflowSessionRecordedCall cancelOrder() {
      return call("Order API", "POST /orders/{orderId}/cancel", "/orders/o-42/cancel", null, 200,
            "{\"id\": \"o-42\", \"status\": \"CANCELLED\"}");
   }

   /** The service confirms order o-42, although its payment was refused. */
   private static WorkflowSessionRecordedCall confirmOrder() {
      return call("Order API", "POST /orders/{orderId}/confirm", "/orders/o-42/confirm", null, 200,
            "{\"id\": \"o-42\", \"status\": \"CONFIRMED\"}");
   }

   /** A call to the mocks of an API 1.0.0, as the mocks record it in a session. */
   private static WorkflowSessionRecordedCall call(String apiName, String operationName, String path, String body,
         int status,
         String responseBody) {
      WorkflowSessionRecordedCall call = new WorkflowSessionRecordedCall();
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

   private WorkflowDocument orderSagaIsImportedAndLinked() throws Exception {
      for (String api : List.of("order-api", "stock-api", "payment-api")) {
         Path contract = Files.createTempFile(api, ".yaml");
         Files.writeString(contract, example("saga-compensation.contracts.yaml", api));
         serviceService.importServiceDefinition(contract.toFile(), null, new ArtifactInfo(api + ".yaml", true));
      }
      arazzoDocumentService.importArazzoMetadata(example("saga-compensation.arazzo.yaml", "order-saga-metadata"));
      return arazzoDocumentService.importArazzoDocument(example("saga-compensation.arazzo.yaml", "order-saga"));
   }

   /** One YAML document per example of a fixture, each announced by a {@code # example: <name> - <what>} comment. */
   private static String example(String fixture, String example) throws IOException {
      String marker = "# example: " + example + " ";
      try (InputStream stream = ArazzoSagaCompensationTest.class.getResourceAsStream(FIXTURES + fixture)) {
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.startsWith(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in " + fixture));
      }
   }
}
