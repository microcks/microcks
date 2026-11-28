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
package io.microcks.minion.arazzo.runner;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

/**
 * Reads the examples of the run fixture: one YAML document per example, each announced by a
 * {@code # example: <name> - <what>} comment, and specifies runs of their workflows against the mocks of API Pastry.
 * @author SebastienDegodez
 */
final class RunFixtures {

   static final Duration TIMEOUT = Duration.ofSeconds(10);
   static final Map<String, OperationTarget> PASTRY_OPERATIONS = Map.of("GetPastries",
         new OperationTarget("GET", "/pastry"), "GetPastryByName", new OperationTarget("GET", "/pastry/{name}"),
         "PatchPastry", new OperationTarget("PATCH", "/pastry/{name}"));

   private RunFixtures() {
   }

   /**
    * @param example    The name of an example of the fixture
    * @param workflowId The workflow of that example to run
    * @param inputs     The inputs of the run
    * @return The run of that workflow against the mocks of API Pastry - 2.0
    */
   static RunSpecification runOf(String example, String workflowId, Map<String, Object> inputs) throws IOException {
      return new RunSpecification("run-1", workflowId, exampleOf(example), inputs,
            Map.of("pastryApi", new SourceTarget(PastryMocks.BASE_URL, PASTRY_OPERATIONS)), TIMEOUT);
   }

   /**
    * @param example    The name of an example of the fixture
    * @param workflowId The workflow of that example to run
    * @param patch      The operation PatchPastry of the source, as its contract declares it
    * @return The run of that workflow against the mocks of API Pastry - 2.0
    */
   static RunSpecification runOf(String example, String workflowId, OperationTarget patch) throws IOException {
      Map<String, OperationTarget> operations = new java.util.HashMap<>(PASTRY_OPERATIONS);
      operations.put("PatchPastry", patch);
      return new RunSpecification("run-1", workflowId, exampleOf(example), Map.of(),
            Map.of("pastryApi", new SourceTarget(PastryMocks.BASE_URL, operations)), TIMEOUT);
   }

   static String exampleOf(String example) throws IOException {
      try (InputStream stream = RunFixtures.class
            .getResourceAsStream("/io/microcks/minion/arazzo/runner/workflow-runs.arazzo.yaml")) {
         String marker = "# example: " + example + " ";
         return Arrays.stream(new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\n---\n"))
               .filter(document -> document.contains(marker)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException(example + " is not in the fixture"));
      }
   }
}
