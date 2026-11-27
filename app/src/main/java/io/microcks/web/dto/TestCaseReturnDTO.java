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

import io.github.microcks.domain.TestReturn;

import java.util.List;

/**
 * Data Transfer object for grouping base information to report a test case run (and thus create a TestCaseResult).
 * @author laurent
 */
public class TestCaseReturnDTO {

   private String operationName;
   private List<TestReturn> testReturns;

   public String getOperationName() {
      return operationName;
   }

   public List<TestReturn> getTestReturns() {
      return testReturns;
   }
}
