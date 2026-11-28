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

import io.microcks.domain.Operation;
import io.microcks.domain.Service;
import io.microcks.domain.workflow.WorkflowInjectedFailure;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the decision to inject a failure: only a call to the very operation a failure of its session names, in the
 * same API and version, is answered with the example of that failure.
 * @author SebastienDegodez
 */
class ArazzoSessionFailuresTest {

   private static final List<WorkflowInjectedFailure> CARD_REFUSED = List
         .of(new WorkflowInjectedFailure("chargePayment", "Payment API", "1.0.0", "POST /payments", "card refused"));

   @ParameterizedTest
   @CsvSource(delimiter = '|', value = { "Payment API | 1.0.0 | POST /payments    | card refused",
         "Payment API | 1.0.0 | GET /payments     | ", "Payment API | 2.0.0 | POST /payments    | ",
         "Order API   | 1.0.0 | POST /payments    | " })
   void onlyTheOperationAFailureNamesAnswersWithItsExample(String apiName, String apiVersion, String operationName,
         String example) {
      Service api = new Service();
      api.setName(apiName);
      api.setVersion(apiVersion);
      Operation operation = new Operation();
      operation.setName(operationName);

      assertEquals(Optional.ofNullable(example), ArazzoSessionFailures.exampleToInject(CARD_REFUSED, api, operation));
   }
}
