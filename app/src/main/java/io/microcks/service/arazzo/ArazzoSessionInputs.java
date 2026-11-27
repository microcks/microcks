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

import io.microcks.artifact.arazzo.ArazzoDataFlow;
import io.microcks.artifact.arazzo.ArazzoWorkflow;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the inputs a session is opened with against its workflow: each input must be one the workflow declares or
 * uses, of the JSON schema type it declares, and the inputs a run needs must all be given, as for a run.
 * @author SebastienDegodez
 */
final class ArazzoSessionInputs {

   private ArazzoSessionInputs() {
   }

   /**
    * @param workflow The workflow of the session
    * @param inputs   The inputs the session is opened with, by name
    * @return Why the inputs are refused: the first unknown input, else the first invalid one, else the missing ones;
    *         empty when they are accepted
    */
   static Optional<String> refusal(ArazzoWorkflow workflow, Map<String, Object> inputs) {
      List<String> needed = ArazzoDataFlow.neededInputs(workflow);
      Set<String> known = new LinkedHashSet<>(workflow.inputNames());
      known.addAll(needed);
      Optional<String> unknown = inputs.keySet().stream().filter(name -> !known.contains(name)).findFirst();
      if (unknown.isPresent()) {
         return Optional.of("Unknown input: " + unknown.get());
      }
      for (Map.Entry<String, Object> input : inputs.entrySet()) {
         String type = workflow.inputTypes().get(input.getKey());
         if (type != null && input.getValue() != null && !isOfType(input.getValue(), type)) {
            return Optional.of("Invalid input " + input.getKey() + ": " + article(type) + " " + type
                  + " is expected, got " + input.getValue());
         }
      }
      List<String> missing = needed.stream().filter(name -> inputs.get(name) == null).toList();
      if (!missing.isEmpty()) {
         // As a run refuses the inputs it needs and is not given.
         return Optional
               .of((missing.size() == 1 ? "Missing input: " : "Missing inputs: ") + String.join(", ", missing));
      }
      return Optional.empty();
   }

   /** Whether a value read from JSON has a JSON schema type; an unknown type accepts any value. */
   private static boolean isOfType(Object value, String type) {
      return switch (type) {
         case "string" -> value instanceof String;
         case "number" -> value instanceof Number;
         case "integer" -> value instanceof Integer || value instanceof Long;
         case "boolean" -> value instanceof Boolean;
         case "object" -> value instanceof Map;
         case "array" -> value instanceof List;
         default -> true;
      };
   }

   private static String article(String type) {
      return type.startsWith("a") || type.startsWith("i") || type.startsWith("o") ? "an" : "a";
   }
}
