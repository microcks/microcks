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

import io.microcks.domain.workflow.WorkflowStepOperation;
import io.microcks.artifact.arazzo.ArazzoOperationCall;
import io.microcks.artifact.arazzo.ArazzoStep;
import io.microcks.artifact.arazzo.ArazzoWorkflow;
import io.microcks.domain.Header;
import io.microcks.domain.workflow.WorkflowSessionRecordedCall;
import io.microcks.service.arazzo.ArazzoSessionVerifier.ExpectedStep;
import io.microcks.service.arazzo.ArazzoSessionVerifier.LinkedApi;
import io.microcks.service.arazzo.ArazzoSessionVerifier.StepVerdict;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of what the outputs of a step see of the response the mocks gave to its call: its status, its headers and its
 * body, read as JSON or kept as text; and of the calls of a session no remaining step of the workflow expects.
 * @author SebastienDegodez
 */
class ArazzoSessionVerifierTest {

   @Test
   void theOutputsOfAStepReadTheStatusHeadersAndBodyOfTheResponseOfTheMocks() {
      Map<String, String> outputs = new LinkedHashMap<>();
      outputs.put("status", "$statusCode");
      outputs.put("location", "$response.header.Location");
      outputs.put("receipt", "$response.body");
      ArazzoStep printReceipt = new ArazzoStep("printReceipt", null,
            new ArazzoOperationCall("orderApi", "printReceipt"), List.of(), null, List.of(), outputs, List.of(),
            List.of(), null, null);
      ExpectedStep expected = new ExpectedStep(0, printReceipt,
            new WorkflowStepOperation("orderApi", "printReceipt", "POST", "/receipts"), "Order API", "1.0.0", null);
      WorkflowSessionRecordedCall call = new WorkflowSessionRecordedCall();
      call.setServiceName("Order API");
      call.setServiceVersion("1.0.0");
      call.setOperationName("POST /receipts");
      call.setPath("/receipts");
      call.setResponseStatus(202);
      call.setResponseHeaders(Set.of(new Header("Location", Set.of("/receipts/rc-1"))));
      call.setResponseBody("Receipt of order o-42");

      ArazzoWorkflow workflow = new ArazzoWorkflow("printWorkflow", null, null, List.of(), List.of(printReceipt),
            Map.of(), Map.of(), List.of());

      List<ArazzoSessionVerifier.StepVerdict> verdicts = ArazzoSessionVerifier.verify(workflow, List.of(expected),
            List.of(call), Map.of(), null);

      assertEquals(Map.of("status", 202, "location", "/receipts/rc-1", "receipt", "Receipt of order o-42"),
            verdicts.get(0).outputs());
   }

   /**
    * A call no step took fails the verification when it calls an operation of a linked API, named by its operationId
    * (by its Microcks name when it has none), after the step received last before it; a call to an API no source is
    * linked to does not.
    */
   @ParameterizedTest
   @CsvSource(delimiter = '|', value = {
         "Order API | 1.0.0 | POST /orders/{orderId}/confirm | false | unexpected call `confirmOrder` after `createOrder`",
         "Order API | 1.0.0 | POST /orders/{orderId}/confirm | true  | unexpected call `confirmOrder` before any step",
         "Order API | 1.0.0 | GET /orders                    | false | unexpected call `GET /orders` after `createOrder`",
         "Audit API | 1.0.0 | POST /events                   | false | ",
         "Order API | 2.0.0 | POST /orders/{orderId}/confirm | false | " })
   void aCallNoRemainingStepExpectsIsUnexpected(String apiName, String apiVersion, String operationName,
         boolean receivedFirst, String failure) {
      ExpectedStep createOrder = new ExpectedStep(0,
            new ArazzoStep("createOrder", null, new ArazzoOperationCall("orderApi", "createOrder"), List.of(), null,
                  List.of(), Map.of(), List.of(), List.of(), null, null),
            new WorkflowStepOperation("orderApi", "createOrder", "POST", "/orders", null, null), "Order API", "1.0.0",
            null);
      WorkflowSessionRecordedCall placed = call(receivedFirst ? 1 : 0, "Order API", "1.0.0", "POST /orders");
      WorkflowSessionRecordedCall other = call(receivedFirst ? 0 : 1, apiName, apiVersion, operationName);
      List<StepVerdict> verdicts = List.of(new StepVerdict(createOrder, placed, List.of(), Map.of(), List.of()));
      List<WorkflowSessionRecordedCall> calls = receivedFirst ? List.of(other, placed) : List.of(placed, other);

      Map<Long, String> unexpected = ArazzoSessionVerifier.unexpectedCalls(verdicts, calls,
            List.of(new LinkedApi("Order API", "1.0.0",
                  Map.of("POST /orders", "createOrder", "POST /orders/{orderId}/confirm", "confirmOrder"))));

      assertEquals(failure == null ? Map.of() : Map.of(other.getIndex(), failure), unexpected);
   }

   private static WorkflowSessionRecordedCall call(long index, String apiName, String apiVersion,
         String operationName) {
      WorkflowSessionRecordedCall call = new WorkflowSessionRecordedCall();
      call.setIndex(index);
      call.setServiceName(apiName);
      call.setServiceVersion(apiVersion);
      call.setOperationName(operationName);
      return call;
   }
}
