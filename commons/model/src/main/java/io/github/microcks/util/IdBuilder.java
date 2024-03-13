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
package io.github.microcks.util;

import io.github.microcks.domain.Operation;
import io.github.microcks.domain.Service;
import io.github.microcks.domain.TestResult;

/**
 * Helper class for building composite/aggregates keys or Ids.
 * @author laurent
 */
public class IdBuilder {

   /**
    * Private Constructor. So that the utility class cannot be instanced
    */
   private IdBuilder() {
   }

   /**
    * Build a unique operation Id from service and operation.
    * @param service   The domain service holding operation
    * @param operation A domain bean representing operation to build an id for
    * @return A unique identifier for operation.
    */
   public static String buildOperationId(Service service, Operation operation) {
      return service.getId() + "-" + operation.getName();
   }

   /**
    * Build a unique TestCase Id from test result and operation.
    * @param testResult The domain testResult holding test case
    * @param operation  A domain bean representing operation matching case
    * @return A unique identifier for test case.
    */
   public static String buildTestCaseId(TestResult testResult, Operation operation) {
      return testResult.getId() + "-" + testResult.getTestNumber() + "-" + operation.getName();
   }

   /**
    * Build a unique TestCase Id from test result and operation.
    * @param testResult    The domain testResult holding test case
    * @param operationName A string representing matching operation name case
    * @return A unique identifier for test case.
    */
   public static String buildTestCaseId(TestResult testResult, String operationName) {
      return testResult.getId() + "-" + testResult.getTestNumber() + "-" + operationName;
   }

   /**
    * Build the full name of a Resource dedicated to no particular operations of a Service. Such Resource is typically a
    * global Schema dependency that defines shared data types, so that you'll be able to easily retrieve it later.
    * @param service      The domain service owning this resource
    * @param resourceName The name of resource
    * @return A full name for this globally attached resource.
    */
   public static String buildResourceFullName(Service service, String resourceName) {
      return service.getName() + "-" + service.getVersion() + "-" + Sanitizer.urlSanitize(resourceName);
   }

   /**
    * Build the full name of a Resource dedicated to no particular operations of a Service. Such Resource is typically a
    * global Schema dependency that defines shared data types, so that you'll be able to easily retrieve it later.
    * @param service      The domain service owning this resource
    * @param resourceName The name of resource
    * @param context      The context this resource belongs to
    * @return A full name for this globally attached resource.
    */
   public static String buildResourceFullName(Service service, String resourceName, String context) {
      return service.getName() + "-" + service.getVersion() + "-" + Sanitizer.urlSanitize(context.replace(".", ""))
            + "-" + Sanitizer.urlSanitize(resourceName);
   }
}
