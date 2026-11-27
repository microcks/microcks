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

import io.microcks.artifact.arazzo.ArazzoWorkflow;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests of the inputs a session is opened with, checked against the JSON schema type each input of its workflow
 * declares.
 * @author SebastienDegodez
 */
class ArazzoSessionInputsTest {

   private static final ObjectMapper JSON = new ObjectMapper();

   @ParameterizedTest
   @CsvSource(delimiter = '|', nullValues = "null", value = { "string | '\"c-7\"' | null",
         "string | 7 | Invalid input value: a string is expected, got 7", "number | 2.5 | null", "number | 2 | null",
         "number | '\"2.5\"' | Invalid input value: a number is expected, got 2.5", "integer | 3 | null",
         "integer | 30000000000 | null", "integer | 2.5 | Invalid input value: an integer is expected, got 2.5",
         "boolean | true | null", "boolean | '\"true\"' | Invalid input value: a boolean is expected, got true",
         "object | '{\"city\": \"Lille\"}' | null",
         "object | '\"Lille\"' | Invalid input value: an object is expected, got Lille", "array | '[1]' | null",
         "array | 1 | Invalid input value: an array is expected, got 1", "date | 1 | null", "null | 1 | null",
         "string | null | null" })
   void anInputIsOfTheTypeTheWorkflowDeclares(String type, String value, String refusal) throws Exception {
      Map<String, String> types = new HashMap<>();
      types.put("value", type);
      ArazzoWorkflow workflow = new ArazzoWorkflow("w", null, null, List.of("value"), List.of(), Map.of(), types,
            List.of());
      Map<String, Object> inputs = new HashMap<>();
      inputs.put("value", JSON.readValue(value == null ? "null" : value, Object.class));

      assertEquals(Optional.ofNullable(refusal), ArazzoSessionInputs.refusal(workflow, inputs));
   }

   @Test
   void theInputsTheWorkflowRequiresAreAllNamedWhenMissing() throws Exception {
      ArazzoWorkflow workflow = new ArazzoWorkflow("w", null, null, List.of("customerId", "currency"), List.of(),
            Map.of(), Map.of(), List.of("customerId", "currency"));

      assertEquals(Optional.of("Missing inputs: customerId, currency"),
            ArazzoSessionInputs.refusal(workflow, JSON.readValue("{}", new TypeReference<Map<String, Object>>() {
            })));
   }
}
