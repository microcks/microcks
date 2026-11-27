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
package io.microcks.service;

import java.util.Map;

/**
 * The implementations a run calls instead of the Microcks mocks.
 * @param baseUrls   The base URL of the implementation of each linked source description, by source name
 * @param secretName The name of the secret whose authorization the calls to the implementations carry; null for none
 * @param perSource  For a mixed run, where each source description goes, by source name: {@value #MOCK} or
 *                   {@value #IMPLEMENTATION}, a source it does not name going to its mocks; null when every source goes
 *                   to its implementation
 * @author SebastienDegodez
 */
public record ArazzoRunImplementations(Map<String, String> baseUrls, String secretName, Map<String, String> perSource) {

   /** A source sent to its Microcks mocks in a mixed run. */
   public static final String MOCK = "mock";
   /** A source sent to its implementation in a mixed run. */
   public static final String IMPLEMENTATION = "implementation";

   /** No base URL is set when none is given. */
   public ArazzoRunImplementations {
      baseUrls = baseUrls == null ? Map.of() : baseUrls;
   }

   /**
    * A run sending every source description to its implementation.
    * @param baseUrls   The base URL of the implementation of each linked source description, by source name
    * @param secretName The name of the secret whose authorization every call carries; null for none
    */
   public ArazzoRunImplementations(Map<String, String> baseUrls, String secretName) {
      this(baseUrls, secretName, null);
   }

   /** @return Whether the run sends some sources to their mocks and the others to their implementations. */
   public boolean mixed() {
      return perSource != null;
   }
}
