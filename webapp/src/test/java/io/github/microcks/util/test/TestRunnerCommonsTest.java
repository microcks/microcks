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
package io.github.microcks.util.test;

import io.github.microcks.domain.Header;
import io.github.microcks.domain.Operation;
import io.github.microcks.domain.OperationsHeaders;
import io.github.microcks.domain.Request;
import io.github.microcks.domain.TestResult;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test case for TestRunnerCommons class.
 * @author laurent
 */
class TestRunnerCommonsTest {

   @Test
   void testCollectHeadersOperationOverrideReplacesRequestHeader() {
      // Request example sets X-Region to "example".
      Request request = new Request();
      request.setHeaders(Set.of(new Header("X-Region", new HashSet<>(Set.of("example")))));

      // Operation-specific override sets X-Region to "operation".
      OperationsHeaders operationsHeaders = new OperationsHeaders();
      operationsHeaders.put("GET /region", Set.of(new Header("X-Region", new HashSet<>(Set.of("operation")))));

      TestResult testResult = new TestResult();
      testResult.setOperationsHeaders(operationsHeaders);

      Operation operation = new Operation();
      operation.setName("GET /region");

      Set<Header> headers = TestRunnerCommons.collectHeaders(testResult, request, operation);

      // There should be only one X-Region header carrying the overridden value.
      assertEquals(1, headers.size());
      Header xRegion = headers.iterator().next();
      assertEquals("X-Region", xRegion.getName());
      assertEquals(1, xRegion.getValues().size());
      assertTrue(xRegion.getValues().contains("operation"));
      assertFalse(xRegion.getValues().contains("example"));
   }

   @Test
   void testCollectHeadersGlobalOverrideReplacesRequestHeader() {
      Request request = new Request();
      request.setHeaders(Set.of(new Header("X-Region", new HashSet<>(Set.of("example")))));

      OperationsHeaders operationsHeaders = new OperationsHeaders();
      operationsHeaders.put(OperationsHeaders.GLOBALS, Set.of(new Header("X-Region", new HashSet<>(Set.of("global")))));

      TestResult testResult = new TestResult();
      testResult.setOperationsHeaders(operationsHeaders);

      Operation operation = new Operation();
      operation.setName("GET /region");

      Set<Header> headers = TestRunnerCommons.collectHeaders(testResult, request, operation);

      assertEquals(1, headers.size());
      Header xRegion = headers.iterator().next();
      assertEquals("X-Region", xRegion.getName());
      assertEquals(Set.of("global"), xRegion.getValues());
   }

   @Test
   void testCollectHeadersOperationOverrideReplacesGlobalAndRequestHeader() {
      Request request = new Request();
      request.setHeaders(Set.of(new Header("X-Region", new HashSet<>(Set.of("example")))));

      OperationsHeaders operationsHeaders = new OperationsHeaders();
      operationsHeaders.put(OperationsHeaders.GLOBALS, Set.of(new Header("X-Region", new HashSet<>(Set.of("global")))));
      operationsHeaders.put("GET /region", Set.of(new Header("X-Region", new HashSet<>(Set.of("operation")))));

      TestResult testResult = new TestResult();
      testResult.setOperationsHeaders(operationsHeaders);

      Operation operation = new Operation();
      operation.setName("GET /region");

      Set<Header> headers = TestRunnerCommons.collectHeaders(testResult, request, operation);

      // Only the operation-specific value should remain: not "example", not "global".
      assertEquals(1, headers.size());
      Header xRegion = headers.iterator().next();
      assertEquals(Set.of("operation"), xRegion.getValues());
   }
}
