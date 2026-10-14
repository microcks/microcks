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
package io.github.microcks.web.dto;

import io.github.microcks.domain.TestCasePhase;

/**
 * Data Transfer object for reporting the progress phase of a test case run while an asynchronous test is in progress.
 * @author sebastien
 */
public class TestCasePhaseDTO {

   private String operationName;
   private TestCasePhase phase;

   public String getOperationName() {
      return operationName;
   }

   public TestCasePhase getPhase() {
      return phase;
   }
}
