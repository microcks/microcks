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
package io.github.microcks.minion.async.client.dto;

import io.github.microcks.domain.TestCasePhase;

import io.quarkus.runtime.annotations.RegisterForReflection;

/**
 * Data Transfer object for reporting the progress phase of a test case run while an asynchronous test is in progress.
 * @author sebastien
 */
@RegisterForReflection
public class TestCasePhaseDTO {

   private String operationName;
   private TestCasePhase phase;

   public TestCasePhaseDTO() {
   }

   /**
    * Create a new TestCasePhaseDTO for an operation and a phase.
    * @param operationName The name of the operation this phase relates to
    * @param phase         The current progress phase of the test case
    */
   public TestCasePhaseDTO(String operationName, TestCasePhase phase) {
      this.operationName = operationName;
      this.phase = phase;
   }

   public String getOperationName() {
      return operationName;
   }

   public void setOperationName(String operationName) {
      this.operationName = operationName;
   }

   public TestCasePhase getPhase() {
      return phase;
   }

   public void setPhase(TestCasePhase phase) {
      this.phase = phase;
   }
}
