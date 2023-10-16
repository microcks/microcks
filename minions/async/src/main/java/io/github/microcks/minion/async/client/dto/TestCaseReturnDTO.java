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

import io.github.microcks.domain.TestReturn;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer object for grouping base information to report a test case run (and thus create a TestCaseResult).
 * @author laurent
 */
@RegisterForReflection
public class TestCaseReturnDTO {

   private String operationName;
   private List<TestReturn> testReturns;

   public TestCaseReturnDTO() {
   }

   /**
    * Create a new TestCaseReturnDTo corresponding to an operation.
    * @param operationName The name of the operation this Test case return relates to
    */
   public TestCaseReturnDTO(String operationName) {
      this.operationName = operationName;
   }

   public String getOperationName() {
      return operationName;
   }

   public void setOperationName(String operationName) {
      this.operationName = operationName;
   }

   public List<TestReturn> getTestReturns() {
      return testReturns;
   }

   public void setTestReturns(List<TestReturn> testReturns) {
      this.testReturns = testReturns;
   }

   public void addTestReturn(TestReturn testReturn) {
      if (testReturns == null) {
         testReturns = new ArrayList<>();
      }
      testReturns.add(testReturn);
   }
}
