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
package io.microcks.web.arazzo.dto;

import java.util.Map;

/**
 * What the API developer gives to run a workflow.
 * @param inputs          The inputs of the workflow, by name
 * @param timeout         How long a step waits for its response, in milliseconds; null for the default
 * @param implementations The base URL of the implementation of each source sent to it, by source name; null to run
 *                        against the Microcks mocks
 * @param secretName      The name of the secret whose authorization the calls to the implementations carry; null for
 *                        none
 * @param target          The mixed target of the Deep Ink mockup ({@code {"kind": "mixed", "perSource": {...}}}): where
 *                        each source goes, {@code mock} or {@code implementation}; null when every source goes to the
 *                        same place
 * @param validateSchema  Whether each response is validated against the OpenAPI schema of its operation; null for no
 * @param example         The name of the recorded example the run plays, whose inputs replace the given ones; null to
 *                        play none
 * @author SebastienDegodez
 */
public record ArazzoRunLaunchDTO(Map<String, Object> inputs, Long timeout, Map<String, String> implementations,
      String secretName, MixedTarget target, Boolean validateSchema, String example) {

   /**
    * A run against the Microcks mocks.
    * @param inputs  The inputs of the workflow, by name
    * @param timeout How long a step waits for its response, in milliseconds; null for the default
    */
   public ArazzoRunLaunchDTO(Map<String, Object> inputs, Long timeout) {
      this(inputs, timeout, null, null, null, null, null);
   }

   /**
    * A run against the implementations of every source, or against the mocks when no implementation is given.
    * @param inputs          The inputs of the workflow, by name
    * @param timeout         How long a step waits for its response, in milliseconds; null for the default
    * @param implementations The base URL of the implementation of each source, by source name
    * @param secretName      The name of the secret whose authorization the calls carry; null for none
    */
   public ArazzoRunLaunchDTO(Map<String, Object> inputs, Long timeout, Map<String, String> implementations,
         String secretName) {
      this(inputs, timeout, implementations, secretName, null, null, null);
   }

   /**
    * A run against the Microcks mocks.
    * @param inputs         The inputs of the workflow, by name
    * @param timeout        How long a step waits for its response, in milliseconds; null for the default
    * @param validateSchema Whether each response is validated against the OpenAPI schema of its operation; null for no
    */
   public ArazzoRunLaunchDTO(Map<String, Object> inputs, Long timeout, Boolean validateSchema) {
      this(inputs, timeout, null, null, null, validateSchema, null);
   }

   /**
    * @param kind      Always {@code mixed}
    * @param perSource Where each source goes, by source name: {@code mock} or {@code implementation}
    */
   public record MixedTarget(String kind, Map<String, String> perSource) {
   }

   /** @return Whether the run sends some sources to their mocks and the others to their implementations. */
   public boolean mixed() {
      return target != null && "mixed".equals(target.kind());
   }
}
