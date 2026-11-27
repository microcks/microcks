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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The W3C baggage a call to the Microcks mocks carries, telling Live Traces which run, workflow, step and attempt it
 * comes from: {@code microcks-arazzo-run}, {@code microcks-arazzo-workflow}, {@code microcks-arazzo-step} and
 * {@code microcks-arazzo-attempt}. Each call keeps its own trace: the baggage marks it, it does not link the calls.
 * @author SebastienDegodez
 */
final class RunBaggage {

   /** The header of the W3C baggage. */
   static final String HEADER = "baggage";

   private RunBaggage() {
   }

   /**
    * Add the entries of the run to the baggage of a call; a baggage the step sends itself, whatever the case of its
    * name, is kept, and the entries are added after its own.
    * @param headers    The headers of the call
    * @param runId      The id of the run in Microcks
    * @param workflowId The id of the workflow
    * @param stepId     The id of the step
    * @param attempt    Which call of the step this is, from 1; a retried step is called again
    */
   static void mark(Map<String, String> headers, String runId, String workflowId, String stepId, int attempt) {
      String entries = "microcks-arazzo-run=" + encode(runId) + ",microcks-arazzo-workflow=" + encode(workflowId)
            + ",microcks-arazzo-step=" + encode(stepId) + ",microcks-arazzo-attempt=" + attempt;
      String name = headers.keySet().stream().filter(HEADER::equalsIgnoreCase).findFirst().orElse(HEADER);
      headers.merge(name, entries, (own, run) -> own + "," + run);
   }

   /** A value a W3C baggage carries as is: any other character is percent-encoded. */
   private static String encode(String value) {
      return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
   }
}
