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
package io.github.microcks.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * Companion objects for TestResult. Each TestCaseResult correspond to a particuliar service operation / action
 * reference by the operationName field. TestCaseResults owns a collection of TestStepResults (one for every request
 * associated to service operation / action).
 * @author laurent
 */
public class TestCaseResult {

   private boolean success = false;
   private long elapsedTime = -1;
   private String operationName;

   private List<TestStepResult> testStepResults = new ArrayList<>();

   public boolean isSuccess() {
      return success;
   }

   public void setSuccess(boolean success) {
      this.success = success;
   }

   public long getElapsedTime() {
      return elapsedTime;
   }

   public void setElapsedTime(long elapsedTime) {
      this.elapsedTime = elapsedTime;
   }

   public String getOperationName() {
      return operationName;
   }

   public void setOperationName(String operationName) {
      this.operationName = operationName;
   }

   public List<TestStepResult> getTestStepResults() {
      return testStepResults;
   }

   public void setTestStepResults(List<TestStepResult> testStepResults) {
      this.testStepResults = testStepResults;
   }
}
