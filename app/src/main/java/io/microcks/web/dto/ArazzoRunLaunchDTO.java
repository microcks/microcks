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
package io.microcks.web.dto;

import java.util.Map;

/**
 * What the API developer gives to run a workflow.
 * @param inputs          The inputs of the workflow, by name
 * @param timeout         How long a step waits for its response, in milliseconds; null for the default
 * @param implementations The base URL of the implementation of each linked source, by source name; null to run against
 *                        the Microcks mocks
 * @param secretName      The name of the secret whose authorization every call to the implementations carries; null for
 *                        none
 * @author SebastienDegodez
 */
public record ArazzoRunLaunchDTO(Map<String, Object> inputs, Long timeout, Map<String, String> implementations,
      String secretName) {

   /**
    * A run against the Microcks mocks.
    * @param inputs  The inputs of the workflow, by name
    * @param timeout How long a step waits for its response, in milliseconds; null for the default
    */
   public ArazzoRunLaunchDTO(Map<String, Object> inputs, Long timeout) {
      this(inputs, timeout, null, null);
   }
}
