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
package io.microcks.event;

import io.microcks.domain.TestResult;


/**
 * Event raised when a Service Test is completed.
 * @author laurent
 */
public class TestCompletionEvent {

   /** The completed TestResult at the end of test. */
   private final TestResult result;

   /**
    * Creates a new {@code TestCompletionEvent} with test result.
    * @param result The TestResult after completion
    */
   public TestCompletionEvent(TestResult result) {
      this.result = result;
   }

   public TestResult getResult() {
      return result;
   }
}
